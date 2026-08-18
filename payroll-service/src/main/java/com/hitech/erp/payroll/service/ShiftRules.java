package com.hitech.erp.payroll.service;

import com.hitech.erp.payroll.db.ShiftEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * Turns a punch pair into a day code and overtime, using the member's shift.
 *
 * <p>This is the piece the payroll module was missing. A shift already carried
 * {@code halfDayHours}, {@code fullDayHours}, {@code graceMinutes} and {@code overtimeEnabled}, but
 * nothing read them: any punch — in or out, one minute or ten hours — set the day to Present, and
 * overtime only existed if an admin typed it in. On a 6-hour shift a 3-hour day paid a full day and
 * a 9-hour day paid no overtime.
 *
 * <p>The rules, deliberately plain so a site manager can predict them:
 *
 * <ul>
 *   <li>hours ≥ fullDayHours → <b>P</b>, overtime = hours − fullDayHours (when the shift allows it)
 *   <li>halfDayHours ≤ hours &lt; fullDayHours → <b>HD</b>
 *   <li>hours &lt; halfDayHours → <b>A</b> — turning up for twenty minutes is not a working day
 *   <li>no punch-out → nothing derived; the day stays incomplete (see {@link #INCOMPLETE_FACTOR})
 * </ul>
 *
 * <p>Grace is credited, not deducted: arriving within the grace window counts as arriving on time,
 * so a member who is five minutes late on a 30-minute grace still gets their full day.
 */
public final class ShiftRules {

  /**
   * What a day with a punch-in but no punch-out is worth once the day is over.
   *
   * <p>Paying it in full rewards forgetting to punch out; paying nothing punishes someone who
   * genuinely worked. Half is the standard compromise, and such days are counted separately so the
   * muster can prompt an admin to correct them.
   */
  public static final BigDecimal INCOMPLETE_FACTOR = BigDecimal.valueOf(0.5);

  private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

  private ShiftRules() {}

  /** The outcome of evaluating one day's punches against a shift. */
  public record Evaluation(String code, BigDecimal workedHours, BigDecimal overtimeHours) {}

  /**
   * Evaluate a punch pair. Returns null when the pair is incomplete or unparseable — the caller
   * should then leave the existing code alone rather than invent one.
   */
  public static Evaluation evaluate(ShiftEntity shift, String inTime, String outTime) {
    BigDecimal hours = hoursBetween(inTime, outTime);
    if (hours == null) return null;

    double full = shift == null || shift.getFullDayHours() <= 0 ? 8 : shift.getFullDayHours();
    double half = shift == null || shift.getHalfDayHours() <= 0 ? full / 2 : shift.getHalfDayHours();
    int grace = shift == null ? 0 : Math.max(0, shift.getGraceMinutes());
    boolean otEnabled = shift == null || shift.isOvertimeEnabled();

    // Credit the grace window so a slightly-late arrival isn't docked half a day.
    BigDecimal credited = hours.add(BigDecimal.valueOf(grace).divide(MINUTES_PER_HOUR, 4, RoundingMode.HALF_UP));

    BigDecimal fullBd = BigDecimal.valueOf(full);
    String code;
    BigDecimal overtime = BigDecimal.ZERO;
    if (credited.compareTo(fullBd) >= 0) {
      code = "P";
      // Overtime is measured on hours actually worked, not on the grace-inflated figure — grace
      // forgives lateness, it doesn't manufacture paid overtime.
      if (otEnabled) overtime = hours.subtract(fullBd).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    } else if (credited.compareTo(BigDecimal.valueOf(half)) >= 0) {
      code = "HD";
    } else {
      code = "A";
    }
    return new Evaluation(code, hours, overtime);
  }

  /** Hours between two "HH:mm" stamps, or null when either is missing/unparseable. */
  public static BigDecimal hoursBetween(String inTime, String outTime) {
    LocalTime in = parse(inTime);
    LocalTime out = parse(outTime);
    if (in == null || out == null) return null;
    int minutes = out.toSecondOfDay() / 60 - in.toSecondOfDay() / 60;
    // A shift that ends past midnight reads as negative; roll it into the next day.
    if (minutes < 0) minutes += 24 * 60;
    return BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
  }

  private static LocalTime parse(String hhmm) {
    if (hhmm == null || hhmm.isBlank()) return null;
    try {
      return LocalTime.parse(hhmm.trim().length() == 5 ? hhmm.trim() : hhmm.trim().substring(0, 5));
    } catch (RuntimeException ex) {
      return null;
    }
  }

  /** A day's payable fraction from its code — the single place P/HD/A become numbers. */
  public static BigDecimal payableFraction(String code) {
    if (code == null) return BigDecimal.ZERO;
    return switch (code) {
      case "P" -> BigDecimal.ONE;
      case "HD" -> INCOMPLETE_FACTOR;
      default -> BigDecimal.ZERO;
    };
  }
}
