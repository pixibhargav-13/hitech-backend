package com.hitech.erp.project.api;

import com.hitech.erp.project.service.AccessService;
import com.hitech.erp.task.security.CurrentUser;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight introspection endpoint the frontend calls to decide UI affordances that depend on the
 * caller's position in the role hierarchy — e.g. showing the "My Tasks / All Users' Tasks" toggle
 * only when the caller actually has someone reporting up to them.
 */
@RestController
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
public class AccessController {

  private final AccessService accessService;

  public record AccessSelf(boolean superAdmin, boolean hasSubtree) {}

  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<AccessSelf> me() {
    AuthenticatedUser user = CurrentUser.get();
    boolean sa = accessService.seesEverything(user);
    boolean subtree = sa || accessService.hasSubtree(user);
    return ResponseEntity.ok(new AccessSelf(sa, subtree));
  }
}
