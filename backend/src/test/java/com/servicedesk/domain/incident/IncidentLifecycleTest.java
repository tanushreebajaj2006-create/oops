package com.servicedesk.domain.incident;

import static com.servicedesk.domain.incident.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidIncidentException;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Incident lifecycle (State pattern)")
class IncidentLifecycleTest {

  private Fixtures f;

  @BeforeEach
  void setUp() {
    f = new Fixtures();
  }

  @Test
  @DisplayName("TC-01 full happy path NEW -> ... -> CLOSED")
  void happyPath() {
    Incident i = f.newIncident();
    assertThat(i.status()).isEqualTo(IncidentStatus.NEW);
    assertThat(i.id().value()).isEqualTo("INC1024");

    i.classify(f.application, f.system, at(1));
    assertThat(i.status()).isEqualTo(IncidentStatus.CLASSIFIED);
    i.applyPriority(com.servicedesk.domain.priority.Priority.P1, f.system, at(1));

    i.assignTo(f.tech, f.system, at(2));
    assertThat(i.status()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(f.tech.activeWorkload()).isEqualTo(1);

    i.acknowledge(f.tech, at(7));
    assertThat(i.acknowledgedAt()).contains(at(7));
    i.startWork(f.tech, at(15));
    i.resolve(f.tech, "Restarted payment pods", "Memory leak", at(20));
    assertThat(i.status()).isEqualTo(IncidentStatus.RESOLVED);
    assertThat(f.tech.activeWorkload()).isZero();

    i.verify(f.employee, at(30));
    i.close(f.employee, "Works now", at(35));
    assertThat(i.status()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(i.isTerminal()).isTrue();
    assertThat(i.closure()).isPresent();
    assertThat(i.allowedActions()).isEmpty();
  }

  @Test
  @DisplayName("TC-02 NEW -> CLOSED is rejected")
  void cannotCloseNewIncident() {
    Incident i = f.newIncident();
    assertThatThrownBy(() -> i.close(f.admin, "skip", at(1)))
        .isInstanceOf(InvalidStateTransitionException.class)
        .hasMessageContaining("NEW");
    assertThat(i.status()).isEqualTo(IncidentStatus.NEW);
  }

  @Test
  @DisplayName("TC-03 cannot resolve before acknowledging and starting work")
  void cannotResolveFromAssigned() {
    Incident i = f.assigned();
    assertThatThrownBy(() -> i.resolve(f.tech, "fix", null, at(3)))
        .isInstanceOf(InvalidStateTransitionException.class);
    assertThat(i.resolution()).isEmpty();
  }

  @Test
  @DisplayName("TC-04 cannot assign before category and priority are known")
  void assignNeedsClassification() {
    Incident i = f.newIncident();
    assertThatThrownBy(() -> i.assignTo(f.tech, f.admin, at(1)))
        .isInstanceOf(InvalidStateTransitionException.class);

    i.classify(f.application, f.system, at(1)); // classified but no priority yet
    assertThatThrownBy(() -> i.assignTo(f.tech, f.admin, at(2)))
        .isInstanceOf(InvalidIncidentException.class)
        .hasMessageContaining("priority");
    assertThat(f.tech.activeWorkload()).isZero();
  }

  @Test
  @DisplayName("TC-05 re-assignment moves workload from old to new technician")
  void reassignmentTransfersWorkload() {
    Incident i = f.inProgress();
    i.assignTo(f.lead, f.admin, at(30)); // escalate to team lead
    assertThat(i.status()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(i.assignee()).contains(f.lead);
    assertThat(f.tech.activeWorkload()).isZero();
    assertThat(f.lead.activeWorkload()).isEqualTo(1);
  }

  @Test
  @DisplayName("TC-06 information request pauses work until reporter answers")
  void awaitingInfoCycle() {
    Incident i = f.inProgress();
    i.requestInfo(f.tech, "Which browser are you using?", at(16));
    assertThat(i.status()).isEqualTo(IncidentStatus.AWAITING_INFO);
    assertThatThrownBy(() -> i.resolve(f.tech, "fix", null, at(17)))
        .isInstanceOf(InvalidStateTransitionException.class);

    i.provideInfo(f.employee, "Chrome 130", at(18));
    assertThat(i.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
    assertThat(i.notes())
        .extracting(Note::type)
        .containsExactly(NoteType.INFO_REQUEST, NoteType.INFO_RESPONSE);
  }

  @Test
  @DisplayName("TC-07 reopen returns work to the same technician and clears the resolution")
  void reopen() {
    Incident i = f.resolved();
    i.reopen(f.employee, "Still failing for card payments", at(25));
    assertThat(i.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
    assertThat(i.resolution()).isEmpty();
    assertThat(i.reopenCount()).isEqualTo(1);
    assertThat(f.tech.activeWorkload()).isEqualTo(1);
  }

  @Test
  @DisplayName("TC-08 cancelling an assigned incident frees the technician")
  void cancelReleasesTechnician() {
    Incident i = f.assigned();
    i.cancel(f.employee, "Duplicate of INC1000", at(3));
    assertThat(i.status()).isEqualTo(IncidentStatus.CANCELLED);
    assertThat(f.tech.activeWorkload()).isZero();
    assertThatThrownBy(() -> i.classify(f.application, f.system, at(4)))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  @DisplayName("TC-09 cannot cancel once work has started")
  void cannotCancelInProgress() {
    Incident i = f.inProgress();
    assertThatThrownBy(() -> i.cancel(f.admin, "no", at(20)))
        .isInstanceOf(InvalidStateTransitionException.class);
  }

  @Test
  @DisplayName("TC-10 allowed actions reflect the current state")
  void allowedActions() {
    assertThat(f.newIncident().allowedActions())
        .containsExactlyInAnyOrder(IncidentAction.CLASSIFY, IncidentAction.CANCEL);
    assertThat(f.resolved().allowedActions())
        .containsExactlyInAnyOrder(IncidentAction.VERIFY, IncidentAction.REOPEN);
  }

  @Test
  @DisplayName("TC-11 failed action leaves incident unchanged")
  void failedActionIsAtomic() {
    Incident i = f.inProgress();
    var before = i.updatedAt();
    assertThatThrownBy(() -> i.resolve(f.tech, "  ", null, at(40)))
        .isInstanceOf(com.servicedesk.domain.exception.InvalidInputException.class);
    assertThat(i.status()).isEqualTo(IncidentStatus.IN_PROGRESS);
    assertThat(i.updatedAt()).isEqualTo(before);
    assertThat(f.tech.activeWorkload()).isEqualTo(1);
  }

  @Test
  @DisplayName("TC-12 feedback only after resolution and only once")
  void feedbackRules() {
    Incident open = f.inProgress();
    assertThatThrownBy(() -> open.giveFeedback(f.employee, 5, "great", at(20)))
        .isInstanceOf(InvalidIncidentException.class);

    Incident done = new Fixtures().resolved();
    done.giveFeedback(done.reporter(), 4, "Quick fix", at(30));
    assertThat(done.feedback()).map(Feedback::rating).contains(4);
    assertThatThrownBy(() -> done.giveFeedback(done.reporter(), 5, "again", at(31)))
        .isInstanceOf(InvalidIncidentException.class);
  }
}
