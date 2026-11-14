package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.User;
import java.time.Clock;

/**
 * <b>Factory pattern</b>: the only way to create a brand-new {@link Incident}.
 *
 * <p>It centralises all creation rules (permission to report, valid text lengths, active
 * service...) and hides how ids and timestamps are produced. Both collaborators are injected
 * through interfaces, so tests can use a fixed clock and a simple counter.
 */
public class IncidentFactory {

  public static final int TITLE_MIN = 5;
  public static final int TITLE_MAX = 120;
  public static final int DESCRIPTION_MIN = 10;
  public static final int DESCRIPTION_MAX = 4000;
  public static final int MAX_AFFECTED_USERS = 1_000_000;

  private final IncidentIdGenerator idGenerator;
  private final Clock clock;

  public IncidentFactory(IncidentIdGenerator idGenerator, Clock clock) {
    this.idGenerator = Require.notNull(idGenerator, "Id generator");
    this.clock = Require.notNull(clock, "Clock");
  }

  public Incident create(NewIncident request) {
    Require.notNull(request, "Incident details");
    User reporter = Require.notNull(request.reporter(), "Reporter");
    reporter.requirePermission(Permission.REPORT_INCIDENT);

    String title = Require.notBlank(request.title(), "Title");
    Require.that(
        title.length() >= TITLE_MIN && title.length() <= TITLE_MAX,
        "Title must be " + TITLE_MIN + "-" + TITLE_MAX + " characters");
    String description = Require.notBlank(request.description(), "Description");
    Require.that(
        description.length() >= DESCRIPTION_MIN && description.length() <= DESCRIPTION_MAX,
        "Description must be " + DESCRIPTION_MIN + "-" + DESCRIPTION_MAX + " characters");

    ItService service = Require.notNull(request.service(), "Affected service");
    service.requireActive();
    Require.notNull(request.impact(), "Impact");
    Require.notNull(request.urgency(), "Urgency");
    Require.inRange(request.affectedUsers(), 1, MAX_AFFECTED_USERS, "Affected users");

    Incident incident =
        new Incident(
            idGenerator.nextId(),
            reporter,
            title,
            description,
            service,
            request.impact(),
            request.urgency(),
            request.affectedUsers(),
            clock.instant());
    incident.recordCreated();
    return incident;
  }
}
