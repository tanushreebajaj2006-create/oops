package com.servicedesk.domain.support;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.incident.NewIncident;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Employee;
import com.servicedesk.domain.user.Skill;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Shared builders for Phase 2 tests (priority, SLA, assignment). */
public final class TestData {

  public static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

  public final MutableClock clock = new MutableClock(T0);
  public final Employee employee =
      new Employee(new UserId("E102"), "Asha Rao", "asha@corp.com", "h", "Finance");
  public final SystemActor system = SystemActor.INSTANCE;
  public final ItService payment = new ItService("PAYMENT", "Payment Gateway", Level.CRITICAL);
  public final ItService wiki = new ItService("WIKI", "Internal Wiki", Level.LOW);
  public final Category network = new Category("NETWORK", "Network");
  public final Category database = new Category("DATABASE", "Database");

  private final AtomicLong seq = new AtomicLong(2000);
  public final IncidentFactory factory =
      new IncidentFactory(() -> IncidentId.of(seq.getAndIncrement()), clock);

  public static Instant minutes(long m) {
    return T0.plusSeconds(60 * m);
  }

  public static Technician tech(String id, int maxLoad, int experience, Skill... skills) {
    return new Technician(
        new UserId(id),
        "Tech " + id,
        id.toLowerCase() + "@corp.com",
        "h",
        List.of(skills),
        experience,
        maxLoad);
  }

  public static Skill skill(String category, int level) {
    return new Skill(category, level);
  }

  public Incident reported(ItService service) {
    return factory.create(
        new NewIncident(
            employee,
            "Payment app unavailable",
            "Users cannot complete transactions since 10:00",
            service,
            Level.HIGH,
            Level.HIGH,
            50));
  }

  /** Created at the clock's current time, classified and given the priority directly. */
  public Incident classified(Category category, Priority priority) {
    Incident i = reported(payment);
    i.classify(category, system, clock.instant());
    i.applyPriority(priority, system, clock.instant());
    return i;
  }
}
