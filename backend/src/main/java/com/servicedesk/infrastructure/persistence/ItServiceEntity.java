package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Row of {@code it_services}. */
@Entity
@Table(name = "it_services")
public class ItServiceEntity {

  @Id String code;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  String criticality;

  @Column(nullable = false)
  boolean active;

  protected ItServiceEntity() {}

  ItServiceEntity(String code) {
    this.code = code;
  }
}
