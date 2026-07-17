package com.hitech.erp.usermanagement.api;

import com.hitech.erp.api.usermanagement.UserApi;
import com.hitech.erp.api.usermanagement.model.PasswordUpdateRequest;
import com.hitech.erp.api.usermanagement.model.UserCreateRequest;
import com.hitech.erp.api.usermanagement.model.UserPageResponse;
import com.hitech.erp.api.usermanagement.model.UserResponse;
import com.hitech.erp.api.usermanagement.model.UserUpdateRequest;
import com.hitech.erp.usermanagement.service.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

  private final UserService userService;

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:VIEW')")
  public ResponseEntity<UserPageResponse> getUsers(Optional<Integer> page, Optional<Integer> size) {
    return ResponseEntity.ok(userService.getUsers(page.orElse(0), size.orElse(20)));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:VIEW')")
  public ResponseEntity<UserResponse> getUserById(Long id) {
    return ResponseEntity.ok(userService.getUserById(id));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:CREATE')")
  public ResponseEntity<UserResponse> createUser(UserCreateRequest userCreateRequest) {
    return ResponseEntity.ok(userService.createUser(userCreateRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:EDIT')")
  public ResponseEntity<UserResponse> updateUser(Long id, UserUpdateRequest userUpdateRequest) {
    return ResponseEntity.ok(userService.updateUser(id, userUpdateRequest));
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:DELETE')")
  public ResponseEntity<Void> deactivateUser(Long id) {
    userService.deactivateUser(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:EDIT')")
  public ResponseEntity<Void> updateUserPassword(Long id, PasswordUpdateRequest passwordUpdateRequest) {
    userService.updateUserPassword(id, passwordUpdateRequest.getNewPassword());
    return ResponseEntity.noContent().build();
  }
}
