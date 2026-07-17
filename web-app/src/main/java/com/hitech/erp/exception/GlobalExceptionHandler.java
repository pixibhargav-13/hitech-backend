package com.hitech.erp.exception;

import com.hitech.erp.common.exception.DuplicateValueException;
import com.hitech.erp.common.exception.EntityDeletionNotAllowedException;
import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.common.exception.InvalidCredentialsException;
import com.hitech.erp.common.model.ErrorDetailResponse;
import com.hitech.erp.common.model.ErrorResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String FAILED = "FAILED";

  @ExceptionHandler(EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorDetailResponse handleNotFound(HttpServletRequest request, Exception ex) {
    logError(HttpStatus.NOT_FOUND, request, ex);
    return new ErrorDetailResponse(HttpStatus.NOT_FOUND.value(), new ErrorResponse(FAILED, ex.getMessage()));
  }

  @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ErrorDetailResponse handleAccessDenied(HttpServletRequest request, Exception ex) {
    logError(HttpStatus.FORBIDDEN, request, ex);
    return new ErrorDetailResponse(HttpStatus.FORBIDDEN.value(), new ErrorResponse(FAILED, "Access is denied"));
  }

  @ExceptionHandler({InvalidCredentialsException.class, JwtException.class})
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorDetailResponse handleUnauthorized(HttpServletRequest request, Exception ex) {
    logError(HttpStatus.UNAUTHORIZED, request, ex);
    return new ErrorDetailResponse(HttpStatus.UNAUTHORIZED.value(), new ErrorResponse(FAILED, ex.getMessage()));
  }

  @ExceptionHandler({
    DuplicateValueException.class,
    EntityDeletionNotAllowedException.class,
    IllegalArgumentException.class,
    IllegalStateException.class
  })
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ErrorDetailResponse handleUnprocessable(HttpServletRequest request, Exception ex) {
    logError(HttpStatus.UNPROCESSABLE_ENTITY, request, ex);
    return new ErrorDetailResponse(
        HttpStatus.UNPROCESSABLE_ENTITY.value(), new ErrorResponse(FAILED, ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ErrorDetailResponse handleValidation(HttpServletRequest request, MethodArgumentNotValidException ex) {
    logError(HttpStatus.UNPROCESSABLE_ENTITY, request, ex);
    List<ErrorResponse> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> new ErrorResponse(FAILED, fieldError.getField() + " : " + fieldError.getDefaultMessage()))
            .toList();
    return new ErrorDetailResponse(HttpStatus.UNPROCESSABLE_ENTITY.value(), errors);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorDetailResponse handleGeneric(HttpServletRequest request, Exception ex) {
    logError(HttpStatus.INTERNAL_SERVER_ERROR, request, ex);
    return new ErrorDetailResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(), new ErrorResponse(FAILED, "An unexpected error occurred."));
  }

  private void logError(HttpStatus status, HttpServletRequest request, Exception ex) {
    log.error("Exception [{}] on [{} {}]: {}", status, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
  }
}
