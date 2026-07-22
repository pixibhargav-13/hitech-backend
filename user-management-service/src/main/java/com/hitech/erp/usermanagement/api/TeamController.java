package com.hitech.erp.usermanagement.api;

import com.hitech.erp.usermanagement.db.AppUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal team directory (id + name + role) for any authenticated user — used to resolve task
 * assignees/followers in Taskopad. Unlike {@code /api/v1/users} (the admin User Management screen,
 * gated by USER_MANAGEMENT:VIEW), this is readable by every signed-in user since assigning work
 * needs to see who's on the team.
 */
@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
public class TeamController {

  private final AppUserRepository userRepository;

  public record TeamMember(
      Long id, String fullName, String roleName, boolean active, Long departmentId, String departmentName) {}

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Transactional(readOnly = true)
  public ResponseEntity<List<TeamMember>> getTeam() {
    List<TeamMember> team =
        userRepository.findAll().stream()
            .filter(u -> u.isActive())
            .map(
                u ->
                    new TeamMember(
                        u.getId(),
                        u.getFullName(),
                        u.getRole().getName(),
                        u.isActive(),
                        u.getDepartment() == null ? null : u.getDepartment().getId(),
                        u.getDepartment() == null ? null : u.getDepartment().getName()))
            .toList();
    return ResponseEntity.ok(team);
  }
}
