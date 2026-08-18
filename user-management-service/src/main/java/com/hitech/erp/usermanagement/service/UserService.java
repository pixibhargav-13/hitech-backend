package com.hitech.erp.usermanagement.service;

import com.hitech.erp.api.usermanagement.model.UserCreateRequest;
import com.hitech.erp.api.usermanagement.model.UserPageResponse;
import com.hitech.erp.api.usermanagement.model.UserResponse;
import com.hitech.erp.api.usermanagement.model.UserUpdateRequest;
import com.hitech.erp.common.exception.DuplicateValueException;
import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.DepartmentEntity;
import com.hitech.erp.usermanagement.db.DepartmentRepository;
import com.hitech.erp.usermanagement.db.RefreshTokenRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.mapper.UserManagementMapper;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final AppUserRepository userRepository;
  private final RoleRepository roleRepository;
  private final DepartmentRepository departmentRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserManagementMapper mapper;

  @Transactional(readOnly = true)
  public UserPageResponse getUsers(int page, int size) {
    Page<AppUserEntity> result = userRepository.findAll(PageRequest.of(page, size));

    return new UserPageResponse()
        .content(mapper.toUserResponses(result.getContent()))
        .page(result.getNumber())
        .size(result.getSize())
        .totalElements(result.getTotalElements())
        .totalPages(result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public UserResponse getUserById(Long id) {
    return mapper.toUserResponse(requireUser(id));
  }

  @Transactional
  public UserResponse createUser(UserCreateRequest request) {
    // A member who never signs in needs no credentials; one who does must have both. Validating
    // here rather than in the contract keeps a single rule for create and update.
    boolean loginUser = !Boolean.FALSE.equals(request.getIsLoginUser());
    String email = emptyToNull(request.getEmail());

    if (loginUser) {
      if (email == null) {
        throw new IllegalArgumentException("An email address is required for members who sign in.");
      }
      if (request.getPassword() == null || request.getPassword().isBlank()) {
        throw new IllegalArgumentException("A password is required for members who sign in.");
      }
    }
    if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
      throw new DuplicateValueException("A user with email '" + email + "' already exists");
    }

    AppUserEntity user = new AppUserEntity();
    user.setLoginUser(loginUser);
    user.setEmail(email);
    user.setFullName(request.getFullName());
    user.setPhoneNumber(request.getPhoneNumber());
    user.setPasswordHash(
        loginUser && request.getPassword() != null ? passwordEncoder.encode(request.getPassword()) : null);
    user.setActive(true);
    user.setRole(requireRole(request.getRoleId()));
    user.setDepartment(findDepartment(request.getDepartmentId()));
    user.setStaffType(request.getStaffType());
    user.setOnPayroll(Boolean.TRUE.equals(request.getOnPayroll()));
    user.setPhotoUrl(emptyToNull(request.getPhotoUrl()));

    return mapper.toUserResponse(userRepository.save(user));
  }

  @Transactional
  public UserResponse updateUser(Long id, UserUpdateRequest request) {
    AppUserEntity user = requireUser(id);

    if (request.getFullName() != null) {
      user.setFullName(request.getFullName());
    }
    if (request.getPhoneNumber() != null) {
      user.setPhoneNumber(request.getPhoneNumber());
    }
    if (request.getRoleId() != null) {
      user.setRole(requireRole(request.getRoleId()));
    }
    if (request.getDepartmentId() != null) {
      user.setDepartment(findDepartment(request.getDepartmentId()));
    }
    if (request.getIsActive() != null) {
      user.setActive(request.getIsActive());
    }
    if (request.getStaffType() != null) {
      user.setStaffType(request.getStaffType());
    }
    if (request.getOnPayroll() != null) {
      user.setOnPayroll(request.getOnPayroll());
    }
    // Turning sign-in off strips the credentials rather than leaving an orphaned hash behind;
    // turning it on requires an address to sign in with.
    if (request.getIsLoginUser() != null && request.getIsLoginUser() != user.isLoginUser()) {
      if (request.getIsLoginUser()) {
        String email = emptyToNull(request.getEmail() != null ? request.getEmail() : user.getEmail());
        if (email == null) {
          throw new IllegalArgumentException("Set an email address before enabling sign-in for this member.");
        }
        user.setEmail(email);
        user.setLoginUser(true);
      } else {
        user.setLoginUser(false);
        user.setPasswordHash(null);
      }
    }
    if (request.getEmail() != null) {
      String email = emptyToNull(request.getEmail());
      if (email == null && user.isLoginUser()) {
        throw new IllegalArgumentException("A member who signs in must keep an email address.");
      }
      if (email != null
          && !email.equalsIgnoreCase(user.getEmail())
          && userRepository.existsByEmailIgnoreCase(email)) {
        throw new DuplicateValueException("A user with email '" + email + "' already exists");
      }
      user.setEmail(email);
    }
    // Photo: a null field means "unchanged"; an empty string is an explicit "remove the photo".
    if (request.getPhotoUrl() != null) {
      user.setPhotoUrl(emptyToNull(request.getPhotoUrl()));
    }

    return mapper.toUserResponse(userRepository.save(user));
  }

  @Transactional
  public void deactivateUser(Long id) {
    AppUserEntity user = requireUser(id);
    user.setActive(false);
    userRepository.save(user);
  }

  /**
   * Hard delete - removes the user record entirely (and their refresh tokens). Guards against
   * deleting yourself or a seeded system-role account (e.g. Super Admin) to avoid lockout.
   */
  @Transactional
  public void deleteUserPermanently(Long id) {
    AppUserEntity user = requireUser(id);

    Long currentUserId = currentUserId();
    if (Objects.equals(currentUserId, id)) {
      throw new EntityDeletionNotAllowedException("You cannot delete your own account");
    }
    if (user.getRole() != null && user.getRole().isSystem()) {
      throw new EntityDeletionNotAllowedException(
          "System accounts (e.g. Super Admin) cannot be permanently deleted");
    }

    refreshTokenRepository.deleteByUserId(id);
    userRepository.delete(user);
  }

  @Transactional
  public void updateUserPassword(Long id, String newPassword) {
    AppUserEntity user = requireUser(id);
    if (!user.isLoginUser()) {
      throw new IllegalArgumentException("This member doesn't sign in — enable sign-in before setting a password.");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  private AppUserEntity requireUser(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
  }

  /** Treat a blank photo string as "no photo" so an empty upload clears rather than stores "". */
  private static String emptyToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  /** Department is optional — 0 or null clears it. */
  private DepartmentEntity findDepartment(Long id) {
    if (id == null || id == 0L) return null;
    return departmentRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
  }

  private RoleEntity requireRole(Long id) {
    return roleRepository
        .findById(Objects.requireNonNull(id, "roleId is required"))
        .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
  }

  private Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) ? u.id() : null;
  }
}
