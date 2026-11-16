package com.servicedesk.domain.incident;

import static com.servicedesk.domain.incident.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.incident.event.IncidentAssigned;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.IncidentStatusChanged;
import com.servicedesk.domain.incident.event.PriorityChanged;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.Priority;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Incident domain events")
class IncidentEventsTest {

  private final Fixtures f = new Fixtures();

  @Test
  @DisplayName("TC-61 every lifecycle step records one event with the right before/after status")
  void lifecycleEmitsEvents() {
    Incident i = f.resolved();
    i.verify(f.employee, at(25));
    i.close(f.employee, "Confirmed working", at(30));

    List<IncidentEvent> events = i.pullEvents();
    assertThat(events)
        .extracting(IncidentEvent::type)
        .containsExactly(
            EventType.CREATED,
            EventType.CLASSIFIED,
            EventType.PRIORITY_CHANGED,
            EventType.ASSIGNED,
            EventType.STATUS_CHANGED, // acknowledged
            EventType.STATUS_CHANGED, // investigation started
            EventType.RESOLVED,
            EventType.STATUS_CHANGED, // verified
            EventType.CLOSED);

    IncidentEvent created = events.get(0);
    assertThat(created.previousStatus()).isEmpty();
    assertThat(created.newStatus()).isEqualTo(IncidentStatus.NEW);
    assertThat(created.actorId()).isEqualTo(f.employee.id());
    assertThat(created.description())
        .isEqualTo("INC1024 created by EMPLOYEE E102 on service PAYMENT");

    IncidentEvent assigned = events.get(3);
    assertThat(assigned.previousStatus()).contains(IncidentStatus.CLASSIFIED);
    assertThat(assigned.newStatus()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(assigned.assigneeId()).contains(f.tech.id());
    assertThat(assigned.priority()).contains(Priority.P1);

    assertThat(((IncidentStatusChanged) events.get(4)).description())
        .isEqualTo("Technician acknowledged incident");
    assertThat(events.get(8).newStatus()).isEqualTo(IncidentStatus.CLOSED);
    // timestamps are the action times, in order
    assertThat(events).extracting(IncidentEvent::occurredAt).isSorted();
  }

  @Test
  @DisplayName("TC-62 a rejected action records no event; pullEvents drains the queue")
  void failedActionsRecordNothing() {
    Incident i = f.newIncident();
    assertThat(i.pendingEvents()).hasSize(1);
    assertThatThrownBy(() -> i.close(f.employee, "skip", at(1)))
        .isInstanceOf(InvalidStateTransitionException.class);
    assertThat(i.pendingEvents()).hasSize(1);
    assertThat(i.pullEvents()).hasSize(1);
    assertThat(i.pullEvents()).isEmpty();
  }

  @Test
  @DisplayName(
      "TC-63 priority events carry the strategy rationale; reassignment names the previous"
          + " technician")
  void richEventDetails() {
    Incident i = f.newIncident();
    i.classify(f.application, f.system, at(1));
    i.prioritize(new MatrixPriorityStrategy(), f.system, at(1));
    i.applyPriority(Priority.P2, f.admin, at(2));
    i.assignTo(f.tech, f.system, at(3), "SKILL_BASED: APPLICATION skill 4/5");
    i.assignTo(f.lead, f.admin, at(4));

    List<IncidentEvent> events = i.pullEvents();
    PriorityChanged first = (PriorityChanged) events.get(2);
    assertThat(first.isInitial()).isTrue();
    assertThat(first.description())
        .isEqualTo(
            "Priority calculated as P1 (MATRIX: Impact 3 x Urgency 4 x Criticality 4 = 48 -> P1)");
    PriorityChanged manual = (PriorityChanged) events.get(3);
    assertThat(manual.isChange()).isTrue();
    assertThat(manual.description()).isEqualTo("Priority changed from P1 to P2");

    IncidentAssigned firstAssign = (IncidentAssigned) events.get(4);
    assertThat(firstAssign.description())
        .isEqualTo("Assigned to Technician T205 (SKILL_BASED: APPLICATION skill 4/5)");
    IncidentAssigned reassign = (IncidentAssigned) events.get(5);
    assertThat(reassign.isReassignment()).isTrue();
    assertThat(reassign.previousTechnicianId()).contains(f.tech.id());
    assertThat(reassign.description()).isEqualTo("Reassigned from T205 to L301");
  }
}
