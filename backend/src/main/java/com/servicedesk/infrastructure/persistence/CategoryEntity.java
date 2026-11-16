package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Row of {@code categories}. */
@Entity
@Table(name = "categories")
public class CategoryEntity {

  @Id String code;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  boolean active;

  protected CategoryEntity() {}

  CategoryEntity(String code) {
    this.code = code;
  }
}
