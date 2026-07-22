package com.hitech.erp.task.db;

import java.time.LocalDate;
import java.util.Locale;

/**
 * How often a task repeats. NONE is a one-off. CUSTOM repeats every {@code recurrenceInterval}
 * days; the named rules ignore the interval and step by their own period.
 */
public enum RecurrenceRule {
  NONE,
  CUSTOM,
  DAILY,
  WEEKLY,
  MONTHLY,
  QUARTERLY,
  HALF_YEARLY;

  public static RecurrenceRule from(String value) {
    if (value == null || value.isBlank()) return NONE;
    String normalised = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    try {
      return RecurrenceRule.valueOf(normalised);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid recurrence rule: " + value);
    }
  }

  /** Advances a date by one step of this rule. */
  public LocalDate next(LocalDate from, int interval) {
    int step = Math.max(1, interval);
    return switch (this) {
      case CUSTOM -> from.plusDays(step);
      case DAILY -> from.plusDays(step);
      case WEEKLY -> from.plusWeeks(1);
      case MONTHLY -> from.plusMonths(1);
      case QUARTERLY -> from.plusMonths(3);
      case HALF_YEARLY -> from.plusMonths(6);
      case NONE -> from;
    };
  }

  /** Human label used in activity log entries and reports. */
  public String label() {
    return switch (this) {
      case NONE -> "Does not repeat";
      case CUSTOM -> "Custom";
      case DAILY -> "Daily";
      case WEEKLY -> "Weekly";
      case MONTHLY -> "Monthly";
      case QUARTERLY -> "Quarterly";
      case HALF_YEARLY -> "Half Yearly";
    };
  }
}
