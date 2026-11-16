package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Database row for every kind of user (single-table mapping of the {@code User} hierarchy; {@code
 * role} says which subclass to rebuild). A plain data holder: all behaviour lives in the domain
 * classes, and {@link UserMapper} converts between the two.
 */
@Entity
@Table(name = "users")
public class UserEntity {

  @Id String id;

  @Column(name = "user_role", nullable = false)
  String role;

  @Column(name = "full_name", nullable = false)
  String fullName;

  @Column(nullable = false)
  String email;

  @Column(name = "password_hash")
  String passwordHash;

  String phone;

  @Column(nullable = false)
  boolean active;

  String department;

  @Column(name = "experience_years")
  Integer experienceYears;

  @Column(name = "max_workload")
  Integer maxWorkload;

  Boolean available;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
  Set<SkillValue> skills = new HashSet<>();

  protected UserEntity() {}

  UserEntity(String id) {
    this.id = id;
  }

  /** One row of {@code user_skills}. */
  @Embeddable
  public static class SkillValue {

    @Column(name = "category_code", nullable = false)
    String categoryCode;

    @Column(nullable = false)
    int proficiency;

    protected SkillValue() {}

    SkillValue(String categoryCode, int proficiency) {
      this.categoryCode = categoryCode;
      this.proficiency = proficiency;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof SkillValue s
          && Objects.equals(categoryCode, s.categoryCode)
          && proficiency == s.proficiency;
    }

    @Override
    public int hashCode() {
      return Objects.hash(categoryCode, proficiency);
    }
  }
}
