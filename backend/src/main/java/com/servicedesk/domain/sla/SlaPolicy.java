package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.SlaConfigurationMissingException;
import com.servicedesk.domain.priority.Priority;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A named set of response/resolution targets, one per {@link Priority}.
 *
 * <p>A policy is either the organisation-wide <b>default</b> ({@code serviceCode} empty) or
 * specific to one IT service (e.g. the payment gateway has tighter targets). Targets are stored in
 * an {@link EnumMap} - the natural, compact choice when the keys are a small fixed enum.
 *
 * <p><b>Encapsulation</b>: the map is never exposed for writing; administrators change targets
 * through {@link #setTarget} which validates them. The warning threshold decides when a running
 * clock turns from ON_TRACK to AT_RISK.
 */
public class SlaPolicy {

  public static final double DEFAULT_WARNING_THRESHOLD = 0.75;

  private final String name;
  private final String serviceCode; // null = default policy
  private final Map<Priority, SlaTarget> targets = new EnumMap<>(Priority.class);
  private double warningThreshold;

  private SlaPolicy(String name, String serviceCode, double warningThreshold) {
    this.name = Require.maxLength(Require.notBlank(name, "SLA policy name"), 80, "SLA policy name");
    this.serviceCode = serviceCode == null ? null : Require.notBlank(serviceCode, "Service code");
    setWarningThreshold(warningThreshold);
  }

  /** Organisation-wide fallback policy used when a service has no policy of its own. */
  public static SlaPolicy defaultPolicy(String name) {
    return new SlaPolicy(name, null, DEFAULT_WARNING_THRESHOLD);
  }

  /** Policy that applies only to incidents on the given service. */
  public static SlaPolicy forService(String name, String serviceCode) {
    return new SlaPolicy(
        name,
        Require.notBlank(serviceCode, "Service code").toUpperCase(),
        DEFAULT_WARNING_THRESHOLD);
  }

  /**
   * The default table from the project brief.
   *
   * <pre>
   *   P1  5 min / 30 min     P2  15 min / 2 h
   *   P3  1 h   / 8 h        P4  4 h    / 24 h
   * </pre>
   */
  public static SlaPolicy standard() {
    return defaultPolicy("Standard SLA")
        .setTarget(Priority.P1, SlaTarget.of(Duration.ofMinutes(5), Duration.ofMinutes(30)))
        .setTarget(Priority.P2, SlaTarget.of(Duration.ofMinutes(15), Duration.ofHours(2)))
        .setTarget(Priority.P3, SlaTarget.of(Duration.ofHours(1), Duration.ofHours(8)))
        .setTarget(Priority.P4, SlaTarget.of(Duration.ofHours(4), Duration.ofHours(24)));
  }

  /**
   * Sets the target for one priority. Higher priorities may not be given looser targets than lower
   * ones (a P1 must never be allowed more time than a P2).
   *
   * @return this policy, for chaining
   */
  public SlaPolicy setTarget(Priority priority, SlaTarget target) {
    Require.notNull(priority, "Priority");
    Require.notNull(target, "SLA target");
    for (Map.Entry<Priority, SlaTarget> e : targets.entrySet()) {
      Priority other = e.getKey();
      SlaTarget t = e.getValue();
      boolean otherIsHigher = other.isHigherThan(priority);
      boolean otherIsLower = priority.isHigherThan(other);
      if ((otherIsHigher && t.resolution().compareTo(target.resolution()) > 0)
          || (otherIsLower && t.resolution().compareTo(target.resolution()) < 0)) {
        throw new InvalidInputException(
            priority
                + " resolution target must sit between the targets of neighbouring priorities");
      }
    }
    targets.put(priority, target);
    return this;
  }

  /**
   * @throws SlaConfigurationMissingException if this policy has no target for {@code priority}
   */
  public SlaTarget targetFor(Priority priority) {
    SlaTarget target = targets.get(Require.notNull(priority, "Priority"));
    if (target == null) {
      throw new SlaConfigurationMissingException(
          "SLA policy '" + name + "' has no target for " + priority);
    }
    return target;
  }

  public boolean hasTargetFor(Priority priority) {
    return targets.containsKey(priority);
  }

  /**
   * @param fraction share of the allowed time after which a running clock is AT_RISK (0.5-0.95)
   */
  public final void setWarningThreshold(double fraction) {
    if (fraction < 0.5 || fraction > 0.95) {
      throw new InvalidInputException("Warning threshold must be between 0.5 and 0.95");
    }
    this.warningThreshold = fraction;
  }

  public String name() {
    return name;
  }

  public Optional<String> serviceCode() {
    return Optional.ofNullable(serviceCode);
  }

  public boolean isDefault() {
    return serviceCode == null;
  }

  public double warningThreshold() {
    return warningThreshold;
  }

  public Map<Priority, SlaTarget> targets() {
    return Collections.unmodifiableMap(targets);
  }

  @Override
  public String toString() {
    return "SlaPolicy[" + name + (isDefault() ? ", default" : ", " + serviceCode) + "]";
  }
}
