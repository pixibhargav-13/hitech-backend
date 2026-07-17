package com.hitech.erp.usermanagement.security;

import java.util.List;

/** Principal carried in the security context, built entirely from JWT claims - no DB hit per request. */
public record AuthenticatedUser(
    Long id, String email, String fullName, Long roleId, String roleName, List<String> permissions) {}
