package com.hitech.erp.procurement.db;

import com.hitech.erp.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** One vendor's price for one line of the enquiry. */
@Getter
@Setter
@Entity
@Table(name = "procurement_quote_lines")
public class QuoteLineEntity extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quote_id", nullable = false)
  private QuoteEntity quote;

  @Column(name = "rfq_line_id", nullable = false)
  private Long rfqLineId;

  /**
   * Null means the vendor did not quote this line. Deliberately nullable rather than defaulting to
   * zero: on a comparative statement a blank that reads as free has caused real mistakes.
   */
  @Column(precision = 16, scale = 2)
  private BigDecimal rate;

  /** Vendors sometimes quote a different quantity (pack sizes). Null means "as asked". */
  @Column(precision = 16, scale = 3)
  private BigDecimal quantity;

  @Column(length = 300)
  private String note;
}
