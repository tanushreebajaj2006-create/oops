package com.servicedesk.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.exception.WorkloadExceededException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Technician workload and skills")
class TechnicianTest {

  private Technician tech(int max) {
    return new Technician(
        new UserId("T1"), "Neha", "neha@corp.com", "h", List.of(new Skill("network", 3)), 4, max);
  }

  @Test
  @DisplayName("TC-27 workload limit is enforced at the boundary")
  void workloadLimit() {
    Technician t = tech(2);
    t.takeOn();
    t.takeOn();
    assertThat(t.canTakeMoreWork()).isFalse();
    assertThatThrownBy(t::takeOn).isInstanceOf(WorkloadExceededException.class);
    assertThat(t.activeWorkload()).isEqualTo(2);
    assertThat(t.utilisation()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("TC-28 unavailable or deactivated technicians cannot take work")
  void unavailable() {
    Technician t = tech(5);
    t.markUnavailable();
    assertThatThrownBy(t::takeOn).isInstanceOf(TechnicianUnavailableException.class);
    t.markAvailable();
    t.deactivate();
    assertThatThrownBy(t::takeOn).isInstanceOf(TechnicianUnavailableException.class);
  }

  @Test
  @DisplayName("TC-29 release never makes workload negative")
  void releaseFloor() {
    Technician t = tech(5);
    t.release();
    assertThat(t.activeWorkload()).isZero();
  }

  @Test
  @DisplayName("TC-30 skills are case-insensitive and proficiency must be 1-5")
  void skills() {
    Technician t = tech(5);
    assertThat(t.hasSkill("NETWORK")).isTrue();
    assertThat(t.skillFor("Network")).map(Skill::proficiency).contains(3);
    t.addSkill(new Skill("NETWORK", 5)); // replaces existing
    assertThat(t.skills()).hasSize(1);
    assertThatThrownBy(() -> new Skill("DB", 6)).isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> t.skills().clear()).isInstanceOf(UnsupportedOperationException.class);
  }
}
