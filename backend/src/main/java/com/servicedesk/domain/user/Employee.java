package com.servicedesk.domain.user;

import java.util.EnumSet;
import java.util.Set;

/** An employee / customer who reports incidents and tracks their own tickets. */
public class Employee extends User {

  private static final Set<Permission> PERMISSIONS = EnumSet.of(Permission.REPORT_INCIDENT);

  private String department;

  public Employee(UserId id, String fullName, String email, String passwordHash, String dept) {
    super(id, fullName, email, passwordHash);
    this.department = dept;
  }

  @Override
  public Role role() {
    return Role.EMPLOYEE;
  }

  @Override
  protected Set<Permission> permissions() {
    return PERMISSIONS;
  }

  public String department() {
    return department;
  }

  public void moveToDepartment(String newDepartment) {
    this.department = newDepartment;
  }
}
