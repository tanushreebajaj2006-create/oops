package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Row of {@code notifications}: one item in a user's in-app inbox. */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "recipient_id", nullable = false)
  String recipientId;

  @Column(nullable = false)
  String channel;

  @Column(nullable = false)
  String subject;

  @Column(nullable = false)
  String body;

  @Column(name = "incident_id", nullable = false)
  String incidentId;

  @Column(name = "event_type", nullable = false)
  String eventType;

  @Column(nullable = false)
  boolean urgent;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "is_read", nullable = false)
  boolean read;

  protected NotificationEntity() {}

  NotificationEntity(
      String recipientId,
      String channel,
      String subject,
      String body,
      String incidentId,
      String eventType,
      boolean urgent,
      Instant createdAt) {
    this.recipientId = recipientId;
    this.channel = channel;
    this.subject = subject;
    this.body = body;
    this.incidentId = incidentId;
    this.eventType = eventType;
    this.urgent = urgent;
    this.createdAt = createdAt;
  }
}
