package com.hitech.erp.common.exception;

public class EntityDeletionNotAllowedException extends RuntimeException {

  public EntityDeletionNotAllowedException(String message) {
    super(message);
  }
}
