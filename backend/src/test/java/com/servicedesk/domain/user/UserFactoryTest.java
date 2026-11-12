package com.servicedesk.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidInputException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("User creation and polymorphic permissions")
class UserFactoryTest {

  private NewUser request(Role role, String email) {
    return new NewUser(
        new UserId("U1"),
        role,
        "Test User",
        email,
        "hash",
        "IT",
        List.of(new Skill("DATABASE", 2)),
        3,
        null);
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = "SYSTEM", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("TC-31 factory builds the right subclass for each role")
  void createsEachRole(Role role) {
    User u = UserFactory.create(request(role, "user@corp.com"));
    assertThat(u.role()).isEqualTo(role);
    assertThat(u.isActive()).isTrue();
  }

  @Test
  @DisplayName("TC-32 a team lead is a technician with extra permissions")
  void teamLeadIsTechnician() {
    User lead = UserFactory.create(request(Role.TEAM_LEAD, "lead@corp.com"));
    User tech = UserFactory.create(request(Role.TECHNICIAN, "tech@corp.com"));
    assertThat(lead).isInstanceOf(Technician.class);
    assertThat(lead.can(Permission.ASSIGN_INCIDENTS)).isTrue();
    assertThat(tech.can(Permission.ASSIGN_INCIDENTS)).isFalse();
    assertThat(((Technician) tech).maxWorkload()).isEqualTo(Technician.DEFAULT_MAX_WORKLOAD);
  }

  @Test
  @DisplayName("TC-33 permissions differ by role")
  void permissionsByRole() {
    User emp = UserFactory.create(request(Role.EMPLOYEE, "e@corp.com"));
    User admin = UserFactory.create(request(Role.ADMINISTRATOR, "a@corp.com"));
    assertThat(emp.can(Permission.REPORT_INCIDENT)).isTrue();
    assertThat(emp.can(Permission.VIEW_AUDIT_LOG)).isFalse();
    assertThat(admin.can(Permission.VIEW_AUDIT_LOG)).isTrue();
  }

  @Test
  @DisplayName("TC-34 the SYSTEM user and invalid emails are rejected")
  void invalidRequests() {
    assertThatThrownBy(() -> UserFactory.create(request(Role.SYSTEM, "s@corp.com")))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> UserFactory.create(request(Role.EMPLOYEE, "not-an-email")))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  @DisplayName("TC-35 users are equal by id and email is normalised")
  void equality() {
    User a = UserFactory.create(request(Role.EMPLOYEE, "Mixed@Corp.com"));
    User b = UserFactory.create(request(Role.ADMINISTRATOR, "other@corp.com"));
    assertThat(a).isEqualTo(b); // same id U1
    assertThat(a.email()).isEqualTo("mixed@corp.com");
  }
}
