package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row of the append-only {@code audit_log}. */
@Entity
@Table(name = "audit_log")
public class AuditEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long seq;

  @Column(name = "occurred_at", nullable = false)
  Instant occurredAt;

  @Column(name = "actor_id", nullable = false)
  String actorId;

  @Column(name = "actor_role", nullable = false)
  String actorRole;

  @Column(nullable = false)
  String action;

  @Column(name = "incident_id")
  String incidentId;

  @Column(name = "previous_status")
  String previousStatus;

  @Column(name = "new_status")
  String newStatus;

  @Column(nullable = false)
  String description;

  protected AuditEntryEntity() {}

  AuditEntryEntity(
      Instant occurredAt,
      String actorId,
      String actorRole,
      String action,
      String incidentId,
      String previousStatus,
      String newStatus,
      String description) {
    this.occurredAt = occurredAt;
    this.actorId = actorId;
    this.actorRole = actorRole;
    this.action = action;
    this.incidentId = incidentId;
    this.previousStatus = previousStatus;
    this.newStatus = newStatus;
    this.description = description;
  }
}
