package com.hitech.erp.common.model;

import java.util.List;

public record ErrorDetailResponse(int statusCode, List<ErrorResponse> errors) {

  public ErrorDetailResponse(int statusCode, ErrorResponse error) {
    this(statusCode, List.of(error));
  }
}
