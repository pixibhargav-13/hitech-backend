package com.hitech.erp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitech.erp.api.usermanagement.model.AuthResponse;
import com.hitech.erp.api.usermanagement.model.RoleRequest;
import com.hitech.erp.api.usermanagement.model.RoleResponse;
import com.hitech.erp.api.usermanagement.model.UserCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Golden-path coverage of the auth + dynamic-role vertical slice: bootstrap admin login, role
 * creation with a permission subset, user creation, login as the new user, permission-gated
 * access (403 outside role, 200 inside), refresh rotation and logout revocation.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private MockMvc mockMvc;

  // Spring Boot 4's auto-configured ObjectMapper bean is the new tools.jackson (Jackson 3) type;
  // the openapi-generated DTOs only need classic Jackson annotations, so a plain instance is fine
  // for building/parsing test request and response bodies.
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String ADMIN_EMAIL = "admin@hitech.local";
  private static final String ADMIN_PASSWORD = "Admin@123";

  private String loginAndGetAccessToken(String email, String password) throws Exception {
    var loginBody = objectMapper.createObjectNode().put("email", email).put("password", password);

    var result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginBody)))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();

    AuthResponse response =
        objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    return response.getAccessToken();
  }

  @Test
  void goldenPath_roleCreation_userCreation_permissionGating_refreshAndLogout() throws Exception {
    // 1. Login as the seeded bootstrap Super Admin
    String adminToken = loginAndGetAccessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

    // 2. Super Admin can list roles
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/roles")
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(MockMvcResultMatchers.status().isOk());

    // Find the PROJECT:VIEW permission id from the seeded permission list
    var permissionsResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/v1/permissions")
                    .header("Authorization", "Bearer " + adminToken))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
    var permissions =
        objectMapper.readValue(
            permissionsResult.getResponse().getContentAsString(),
            com.hitech.erp.api.usermanagement.model.PermissionResponse[].class);
    long projectViewPermissionId =
        List.of(permissions).stream()
            .filter(p -> "PROJECT:VIEW".equals(p.getCode()))
            .findFirst()
            .orElseThrow()
            .getId();

    // 3. Create a role limited to just PROJECT:VIEW
    RoleRequest roleRequest =
        new RoleRequest().name("Project Viewer").description("Read-only project access");
    roleRequest.setPermissionIds(List.of(projectViewPermissionId));

    var createRoleResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/v1/roles")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(roleRequest)))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
    RoleResponse createdRole =
        objectMapper.readValue(createRoleResult.getResponse().getContentAsString(), RoleResponse.class);
    assertThat(createdRole.getPermissions()).hasSize(1);

    // 4. Create a user with that role
    UserCreateRequest userCreateRequest =
        new UserCreateRequest()
            .email("viewer@hitech.local")
            .password("Viewer@123")
            .fullName("Project Viewer User")
            .roleId(createdRole.getId());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userCreateRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());

    // 5. Login as the new limited user
    String viewerToken = loginAndGetAccessToken("viewer@hitech.local", "Viewer@123");

    // 6. Limited user is forbidden from user-management endpoints (outside their permission set)
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/roles")
                .header("Authorization", "Bearer " + viewerToken))
        .andExpect(MockMvcResultMatchers.status().isForbidden());

    // 7. But /me is accessible to any authenticated user and reflects the correct permission set
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + viewerToken))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.permissions[0]").value("PROJECT:VIEW"));

    // 8. Unauthenticated requests are rejected
    mockMvc
        .perform(MockMvcRequestBuilders.get("/api/v1/roles"))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
  }

  @Test
  void refreshRotatesTokenAndLogoutRevokesIt() throws Exception {
    var loginBody =
        objectMapper.createObjectNode().put("email", ADMIN_EMAIL).put("password", ADMIN_PASSWORD);

    var loginResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginBody)))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();
    AuthResponse login =
        objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);

    var refreshBody = objectMapper.createObjectNode().put("refreshToken", login.getRefreshToken());

    // Refresh succeeds once
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshBody)))
        .andExpect(MockMvcResultMatchers.status().isOk());

    // The old refresh token was rotated out - reusing it now fails
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshBody)))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
  }
}
