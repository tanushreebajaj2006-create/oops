package com.servicedesk.domain.sla;

/**
 * Health of one SLA clock. Ordered from best to worst by {@link #severity()} so the overall status
 * of an incident is simply the worst of its two clocks.
 */
public enum SlaStatus {
  /** Finished before the deadline. */
  MET(0),
  /** Clock stopped without the work being done (e.g. incident cancelled). */
  NOT_APPLICABLE(0),
  /** Running, comfortably inside the deadline. */
  ON_TRACK(1),
  /** Running, past the warning threshold (e.g. 75% of the time used). */
  AT_RISK(2),
  /** Deadline missed (still running or finished late). */
  BREACHED(3);

  private final int severity;

  SlaStatus(int severity) {
    this.severity = severity;
  }

  public int severity() {
    return severity;
  }

  public static SlaStatus worst(SlaStatus a, SlaStatus b) {
    return a.severity >= b.severity ? a : b;
  }
}
