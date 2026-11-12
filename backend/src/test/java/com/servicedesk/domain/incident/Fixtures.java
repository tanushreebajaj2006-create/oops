package com.servicedesk.domain.incident;

import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Administrator;
import com.servicedesk.domain.user.Employee;
import com.servicedesk.domain.user.Skill;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.TeamLead;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Shared test data: one of each user type, a service, a category and an incident factory. */
final class Fixtures {

  static final Instant T0 = Instant.parse("2026-11-12T10:05:00Z");

  final Employee employee =
      new Employee(new UserId("E102"), "Asha Rao", "asha@corp.com", "h", "Finance");
  final Employee otherEmployee =
      new Employee(new UserId("E103"), "Ravi K", "ravi@corp.com", "h", "HR");
  final Technician tech =
      new Technician(
          new UserId("T205"),
          "Neha S",
          "neha@corp.com",
          "h",
          List.of(new Skill("APPLICATION", 4)),
          5,
          2);
  final Technician otherTech =
      new Technician(
          new UserId("T206"),
          "Arjun P",
          "arjun@corp.com",
          "h",
          List.of(new Skill("NETWORK", 3)),
          2,
          2);
  final TeamLead lead =
      new TeamLead(new UserId("L301"), "Meera L", "meera@corp.com", "h", List.of(), 10, 3);
  final Administrator admin = new Administrator(new UserId("A001"), "Admin", "admin@corp.com", "h");
  final SystemActor system = SystemActor.INSTANCE;

  final ItService payment = new ItService("PAYMENT", "Payment Gateway", Level.CRITICAL);
  final Category application = new Category("APPLICATION", "Application");

  private final AtomicLong seq = new AtomicLong(1024);
  final IncidentFactory factory =
      new IncidentFactory(
          () -> IncidentId.of(seq.getAndIncrement()), Clock.fixed(T0, ZoneOffset.UTC));

  static Instant at(int minutesAfterT0) {
    return T0.plusSeconds(60L * minutesAfterT0);
  }

  Incident newIncident() {
    return factory.create(
        new NewIncident(
            employee,
            "Payment app unavailable",
            "Users cannot complete transactions since 10:00",
            payment,
            Level.HIGH,
            Level.CRITICAL,
            250));
  }

  /** NEW -> CLASSIFIED with priority P1. */
  Incident classified() {
    Incident i = newIncident();
    i.classify(application, system, at(1));
    i.applyPriority(Priority.P1, system, at(1));
    return i;
  }

  Incident assigned() {
    Incident i = classified();
    i.assignTo(tech, system, at(2));
    return i;
  }

  Incident inProgress() {
    Incident i = assigned();
    i.acknowledge(tech, at(7));
    i.startWork(tech, at(15));
    return i;
  }

  Incident resolved() {
    Incident i = inProgress();
    i.resolve(tech, "Restarted payment pods", "Memory leak", at(20));
    return i;
  }
}
