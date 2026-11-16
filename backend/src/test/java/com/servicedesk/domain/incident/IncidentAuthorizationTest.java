package com.servicedesk.domain.incident;

import static com.servicedesk.domain.incident.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.UnauthorizedOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Incident authorization rules")
class IncidentAuthorizationTest {

  private Fixtures f;

  @BeforeEach
  void setUp() {
    f = new Fixtures();
  }

  @Test
  @DisplayName("TC-13 an employee cannot assign incidents")
  void employeeCannotAssign() {
    Incident i = f.classified();
    assertThatThrownBy(() -> i.assignTo(f.tech, f.employee, at(2)))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThat(i.status()).isEqualTo(IncidentStatus.CLASSIFIED);
  }

  @Test
  @DisplayName("TC-14 only the assigned technician can acknowledge")
  void otherTechnicianCannotAcknowledge() {
    Incident i = f.assigned();
    assertThatThrownBy(() -> i.acknowledge(f.otherTech, at(3)))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThatThrownBy(() -> i.acknowledge(f.admin, at(3)))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName("TC-15 only the reporter or an admin can verify; others cannot")
  void verifyRules() {
    Incident i = f.resolved();
    assertThatThrownBy(() -> i.verify(f.otherEmployee, at(25)))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThatThrownBy(() -> i.verify(f.tech, at(25)))
        .isInstanceOf(UnauthorizedOperationException.class);
    i.verify(f.admin, at(25));
    assertThat(i.status()).isEqualTo(IncidentStatus.VERIFIED);
  }

  @Test
  @DisplayName("TC-16 reporter cannot see internal investigation notes")
  void investigationNotesHidden() {
    Incident i = f.inProgress();
    i.addNote(f.tech, NoteType.INVESTIGATION, "Heap at 98%, suspect leak", at(16));
    i.addNote(f.tech, NoteType.COMMENT, "Working on it", at(17));

    assertThat(i.notesVisibleTo(f.employee))
        .extracting(Note::type)
        .containsExactly(NoteType.COMMENT);
    assertThat(i.notesVisibleTo(f.tech)).hasSize(2);
    assertThat(i.notesVisibleTo(f.admin)).hasSize(2);
  }

  @Test
  @DisplayName("TC-17 unrelated users cannot see or comment on an incident")
  void unrelatedUserIsBlocked() {
    Incident i = f.inProgress();
    assertThat(i.isVisibleTo(f.otherEmployee)).isFalse();
    assertThat(i.isVisibleTo(f.otherTech)).isFalse();
    assertThat(i.isVisibleTo(f.lead)).isTrue();
    assertThatThrownBy(() -> i.addNote(f.otherEmployee, NoteType.COMMENT, "hi", at(16)))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName("TC-18 deactivated admin loses all permissions")
  void deactivatedUser() {
    Incident i = f.classified();
    f.admin.deactivate();
    assertThatThrownBy(() -> i.assignTo(f.tech, f.admin, at(2)))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName("TC-19 the system actor may auto-close but not resolve")
  void systemActor() {
    Incident i = f.resolved();
    assertThatThrownBy(() -> i.reopen(f.system, "x", at(21)))
        .isInstanceOf(UnauthorizedOperationException.class);
    i.verify(f.employee, at(22));
    i.close(f.system, "Auto-closed after verification", at(23));
    assertThat(i.status()).isEqualTo(IncidentStatus.CLOSED);
  }
}
