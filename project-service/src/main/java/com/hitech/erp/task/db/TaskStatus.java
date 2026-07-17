package com.hitech.erp.task.db;

/** Task lifecycle states — mirror the Taskopad frontend labels (Pending / In Progress / …). */
public enum TaskStatus {
  PENDING,
  IN_PROGRESS,
  ON_HOLD,
  STUCK,
  COMPLETED;

  /** Accepts UI labels ("In Progress"), enum names, dashes/underscores — case-insensitive. */
  public static TaskStatus from(String value) {
    if (value == null) throw new IllegalArgumentException("Task status is required");
    try {
      return TaskStatus.valueOf(value.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid task status: " + value);
    }
  }
}
