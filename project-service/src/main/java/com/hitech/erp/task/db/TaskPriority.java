package com.hitech.erp.task.db;

/** Task priority — mirrors the Taskopad frontend (Low / Medium / High). */
public enum TaskPriority {
  LOW,
  MEDIUM,
  HIGH;

  public static TaskPriority from(String value) {
    if (value == null) throw new IllegalArgumentException("Task priority is required");
    try {
      return TaskPriority.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid task priority: " + value);
    }
  }
}
