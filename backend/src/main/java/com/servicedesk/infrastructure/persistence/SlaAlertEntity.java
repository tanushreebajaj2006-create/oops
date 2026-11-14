package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row of {@code sla_alerts}: a recorded SLA warning or violation. */
@Entity
@Table(name = "sla_alerts")
public class SlaAlertEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "alert_type", nullable = false)
  String alertType;

  @Column(name = "incident_id", nullable = false)
  String incidentId;

  @Column(name = "sla_kind", nullable = false)
  String slaKind;

  @Column(nullable = false)
  String priority;

  @Column(name = "due_at", nullable = false)
  Instant dueAt;

  @Column(name = "detected_at", nullable = false)
  Instant detectedAt;

  @Column(name = "assignee_id")
  String assigneeId;

  protected SlaAlertEntity() {}

  SlaAlertEntity(
      String alertType,
      String incidentId,
      String slaKind,
      String priority,
      Instant dueAt,
      Instant detectedAt,
      String assigneeId) {
    this.alertType = alertType;
    this.incidentId = incidentId;
    this.slaKind = slaKind;
    this.priority = priority;
    this.dueAt = dueAt;
    this.detectedAt = detectedAt;
    this.assigneeId = assigneeId;
  }
}
