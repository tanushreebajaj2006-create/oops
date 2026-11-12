package com.servicedesk.domain.user;

import java.util.List;

/**
 * Everything needed to create a user of any role. Fields that do not apply to a role are ignored
 * (e.g. {@code skills} for an employee).
 */
public record NewUser(
    UserId id,
    Role role,
    String fullName,
    String email,
    String passwordHash,
    String department,
    List<Skill> skills,
    int experienceYears,
    Integer maxWorkload) {}
