package com.hitech.erp.tender.db;

/** Where a tender sits in the funnel: Sorting → Research → Applied → Won / Lost. */
public enum TenderStage {
  SORTING,
  RESEARCH,
  APPLIED,
  WON,
  LOST
}
