package com.hitech.erp.usermanagement.api;

import com.hitech.erp.api.usermanagement.AuthenticationApi;
import com.hitech.erp.api.usermanagement.model.AuthResponse;
import com.hitech.erp.api.usermanagement.model.CurrentUserResponse;
import com.hitech.erp.api.usermanagement.model.LoginRequest;
import com.hitech.erp.api.usermanagement.model.RefreshTokenRequest;
import com.hitech.erp.usermanagement.security.AuthenticatedUser;
import com.hitech.erp.usermanagement.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthenticationApi {

  private final AuthService authService;

  @Override
  public ResponseEntity<AuthResponse> login(LoginRequest loginRequest) {
    return ResponseEntity.ok(authService.login(loginRequest.getEmail(), loginRequest.getPassword()));
  }

  @Override
  public ResponseEntity<AuthResponse> refresh(RefreshTokenRequest refreshTokenRequest) {
    return ResponseEntity.ok(authService.refresh(refreshTokenRequest.getRefreshToken()));
  }

  @Override
  public ResponseEntity<Void> logout(RefreshTokenRequest refreshTokenRequest) {
    authService.logout(refreshTokenRequest.getRefreshToken());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<CurrentUserResponse> getCurrentUser() {
    AuthenticatedUser principal =
        (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return ResponseEntity.ok(authService.getCurrentUser(principal));
  }
}
