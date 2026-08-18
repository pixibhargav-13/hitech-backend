package com.hitech.erp.approval.db;

import java.util.Arrays;
import java.util.Optional;

/**
 * Everything that can be put behind an approval chain.
 *
 * <p>The registry lives in code rather than being free-text so the settings screen can list types
 * that nobody has configured yet, and so a typo can't create a chain that will never fire. Adding a
 * type here plus a seed row in a migration is all it takes to make a new record approvable.
 */
public enum ApprovalEntityType {
  LEAVE_APPLICATION("Leave Application"),
  TASK_COMPLETION("Task Completion"),
  PAYROLL_RUN("Payroll Run"),
  PURCHASE_ORDER("Purchase Order"),
  SALES_INVOICE("Sales Invoice"),
  PAYMENT_ENTRY("Payment Entry"),
  SITE_EXPENSE("Site Expense"),
  MATERIAL_PURCHASE("Material Purchase"),
  TENDER_SUBMISSION("Tender Submission");

  private final String label;

  ApprovalEntityType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static Optional<ApprovalEntityType> from(String value) {
    if (value == null) return Optional.empty();
    String normalised = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    return Arrays.stream(values()).filter(t -> t.name().equals(normalised)).findFirst();
  }
}
