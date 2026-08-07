package com.hitech.erp.tender.db;

/** Sub-status once a tender has been applied to. Decoded from the source workbook's STATUS column. */
public enum TenderStatus {
  SUBMITTED,
  TECH_OPENED,
  TECH_QUALIFIED,
  TECH_DISQUALIFIED,
  WON,
  LOST,
  RETENDERED,
  CANCELLED,
  COMPLETED
}
