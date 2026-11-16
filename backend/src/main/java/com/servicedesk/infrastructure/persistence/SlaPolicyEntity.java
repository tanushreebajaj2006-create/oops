package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Row of {@code sla_policies} with its {@code sla_targets}. */
@Entity
@Table(name = "sla_policies")
public class SlaPolicyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  String name;

  @Column(name = "service_code")
  String serviceCode;

  @Column(name = "warning_threshold", nullable = false)
  double warningThreshold;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "sla_targets", joinColumns = @JoinColumn(name = "policy_id"))
  Set<TargetValue> targets = new HashSet<>();

  protected SlaPolicyEntity() {}

  SlaPolicyEntity(String serviceCode) {
    this.serviceCode = serviceCode;
  }

  /** One row of {@code sla_targets}; durations in seconds. */
  @Embeddable
  public static class TargetValue {

    @Column(nullable = false)
    String priority;

    @Column(name = "response_seconds", nullable = false)
    long responseSeconds;

    @Column(name = "resolution_seconds", nullable = false)
    long resolutionSeconds;

    protected TargetValue() {}

    TargetValue(String priority, long responseSeconds, long resolutionSeconds) {
      this.priority = priority;
      this.responseSeconds = responseSeconds;
      this.resolutionSeconds = resolutionSeconds;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof TargetValue t
          && Objects.equals(priority, t.priority)
          && responseSeconds == t.responseSeconds
          && resolutionSeconds == t.resolutionSeconds;
    }

    @Override
    public int hashCode() {
      return Objects.hash(priority, responseSeconds, resolutionSeconds);
    }
  }
}
