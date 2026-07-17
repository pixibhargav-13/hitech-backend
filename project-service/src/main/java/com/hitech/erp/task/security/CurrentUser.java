package com.hitech.erp.task.security;

import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the JWT-derived principal out of the security context. */
public final class CurrentUser {

  private CurrentUser() {}

  public static AuthenticatedUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new IllegalStateException("No authenticated user in context");
    }
    return user;
  }
}
