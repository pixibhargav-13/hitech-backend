package com.hitech.erp.usermanagement.service;

import com.hitech.erp.common.exception.DuplicateValueException;
import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.usermanagement.db.AppUserRepository;
import com.hitech.erp.usermanagement.db.DepartmentEntity;
import com.hitech.erp.usermanagement.db.DepartmentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

  private final DepartmentRepository departmentRepository;
  private final AppUserRepository userRepository;

  /** A department plus how many people sit in it. */
  public record DepartmentResponse(
      Long id, String name, String code, String description, Long headUserId, boolean isActive, long memberCount) {}

  public record DepartmentUpsertRequest(String name, String code, String description, Long headUserId, Boolean isActive) {}

  @Transactional(readOnly = true)
  public List<DepartmentResponse> getDepartments() {
    return departmentRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public DepartmentResponse getDepartmentById(Long id) {
    return toResponse(require(id));
  }

  @Transactional
  public DepartmentResponse createDepartment(DepartmentUpsertRequest r) {
    String name = requireName(r.name());
    if (departmentRepository.existsByNameIgnoreCase(name)) {
      throw new DuplicateValueException("A department named '" + name + "' already exists");
    }
    DepartmentEntity d = new DepartmentEntity();
    d.setName(name);
    d.setCode(r.code());
    d.setDescription(r.description());
    d.setHeadUserId(r.headUserId());
    d.setActive(r.isActive() == null || r.isActive());
    return toResponse(departmentRepository.save(d));
  }

  @Transactional
  public DepartmentResponse updateDepartment(Long id, DepartmentUpsertRequest r) {
    DepartmentEntity d = require(id);
    if (r.name() != null && !r.name().isBlank()) {
      String name = r.name().trim();
      departmentRepository
          .findByNameIgnoreCase(name)
          .filter(other -> !other.getId().equals(id))
          .ifPresent(other -> {
            throw new DuplicateValueException("A department named '" + name + "' already exists");
          });
      d.setName(name);
    }
    if (r.code() != null) d.setCode(r.code());
    if (r.description() != null) d.setDescription(r.description());
    if (r.headUserId() != null) d.setHeadUserId(r.headUserId());
    if (r.isActive() != null) d.setActive(r.isActive());
    return toResponse(departmentRepository.save(d));
  }

  @Transactional
  public void deleteDepartment(Long id) {
    DepartmentEntity d = require(id);
    // Refuse rather than silently orphaning people — the caller should move them first.
    if (userRepository.existsByDepartmentId(id)) {
      throw new EntityDeletionNotAllowedException(
          "'" + d.getName() + "' still has members. Move them to another department first.");
    }
    departmentRepository.delete(d);
  }

  private DepartmentEntity require(Long id) {
    return departmentRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Department not found: " + id));
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Department name is required");
    return name.trim();
  }

  private DepartmentResponse toResponse(DepartmentEntity d) {
    return new DepartmentResponse(
        d.getId(),
        d.getName(),
        d.getCode(),
        d.getDescription(),
        d.getHeadUserId(),
        d.isActive(),
        userRepository.countByDepartmentId(d.getId()));
  }
}
