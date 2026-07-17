package com.hitech.erp.usermanagement.api;

import com.hitech.erp.api.usermanagement.ModuleApi;
import com.hitech.erp.api.usermanagement.model.ModuleResponse;
import com.hitech.erp.usermanagement.service.ModuleService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModuleController implements ModuleApi {

  private final ModuleService moduleService;

  @Override
  @PreAuthorize("hasAuthority('USER_MANAGEMENT:VIEW')")
  public ResponseEntity<List<ModuleResponse>> getModules() {
    return ResponseEntity.ok(moduleService.getAllModules());
  }
}
