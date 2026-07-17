package com.hitech.erp.usermanagement.service;

import com.hitech.erp.api.usermanagement.model.UserCreateRequest;
import com.hitech.erp.api.usermanagement.model.UserPageResponse;
import com.hitech.erp.api.usermanagement.model.UserResponse;
import com.hitech.erp.api.usermanagement.model.UserUpdateRequest;
import com.hitech.erp.common.exception.DuplicateValueException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.usermanagement.db.AppUserEntity;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.RoleEntity;
import com.hitech.erp.usermanagement.db.RoleRepository;
import com.hitech.erp.usermanagement.mapper.UserManagementMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final AppUserRepository userRepository;
  private final RoleRepository roleRepository;
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
    if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
      throw new DuplicateValueException("A user with email '" + request.getEmail() + "' already exists");
    }

    AppUserEntity user = new AppUserEntity();
    user.setEmail(request.getEmail());
    user.setFullName(request.getFullName());
    user.setPhoneNumber(request.getPhoneNumber());
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setActive(true);
    user.setRole(requireRole(request.getRoleId()));

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
    if (request.getIsActive() != null) {
      user.setActive(request.getIsActive());
    }

    return mapper.toUserResponse(userRepository.save(user));
  }

  @Transactional
  public void deactivateUser(Long id) {
    AppUserEntity user = requireUser(id);
    user.setActive(false);
    userRepository.save(user);
  }

  @Transactional
  public void updateUserPassword(Long id, String newPassword) {
    AppUserEntity user = requireUser(id);
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
  }

  private AppUserEntity requireUser(Long id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
  }

  private RoleEntity requireRole(Long id) {
    return roleRepository
        .findById(Objects.requireNonNull(id, "roleId is required"))
        .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
  }
}
