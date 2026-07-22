package com.hitech.erp.usermanagement.api;

import com.hitech.erp.usermanagement.service.DepartmentService;
import com.hitech.erp.usermanagement.service.DepartmentService.DepartmentResponse;
import com.hitech.erp.usermanagement.service.DepartmentService.DepartmentUpsertRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Department directory. Reading is open to any signed-in user (Taskopad filters and pickers need
 * it); creating/editing is gated behind user-management permissions.
 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService departmentService;

  @GetMapping
  public ResponseEntity<List<DepartmentResponse>> getDepartments() {
    return ResponseEntity.ok(departmentService.getDepartments());
  }

  @GetMapping("/{id}")
  public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable("id") Long id) {
    return ResponseEntity.ok(departmentService.getDepartmentById(id));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:CREATE')")
  public ResponseEntity<DepartmentResponse> createDepartment(@RequestBody DepartmentUpsertRequest request) {
    return ResponseEntity.ok(departmentService.createDepartment(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:EDIT')")
  public ResponseEntity<DepartmentResponse> updateDepartment(
      @PathVariable("id") Long id, @RequestBody DepartmentUpsertRequest request) {
    return ResponseEntity.ok(departmentService.updateDepartment(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:DELETE')")
  public ResponseEntity<Void> deleteDepartment(@PathVariable("id") Long id) {
    departmentService.deleteDepartment(id);
    return ResponseEntity.noContent().build();
  }
}
