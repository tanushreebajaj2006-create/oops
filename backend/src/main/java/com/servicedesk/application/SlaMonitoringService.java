package com.servicedesk.application;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.event.IncidentEventPublisher;
import com.servicedesk.domain.incident.event.SlaAlertRaised;
import com.servicedesk.domain.sla.EscalationPolicy;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.sla.SlaAlertLog;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaMonitor;
import com.servicedesk.domain.sla.SlaScanResult;
import com.servicedesk.domain.sla.SlaSnapshot;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The background SLA check (run every minute by a scheduler in infrastructure):
 *
 * <ol>
 *   <li><b>SLA monitoring</b> - every open incident is evaluated; each new warning ("deadline
 *       approaching") and violation is recorded once and published as an event, which the audit
 *       trail records and the notifier sends.
 *   <li><b>Automatic escalation</b> - an unacknowledged P1/P2 incident whose response deadline has
 *       passed goes to a team lead (and is reassigned to them if they have capacity); if it is
 *       still unacknowledged a full response period later, an administrator is alerted.
 * </ol>
 */
@Service
@Transactional
public class SlaMonitoringService {

  private static final Logger LOG = LoggerFactory.getLogger(SlaMonitoringService.class);
  private static final SystemActor SYSTEM = SystemActor.INSTANCE;

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final SlaAlertLog alertLog;
  private final SlaService sla;
  private final EscalationPolicy escalationPolicy;
  private final IncidentEventPublisher publisher;
  private final Clock clock;

  public SlaMonitoringService(
      IncidentRepository incidents,
      UserRepository users,
      SlaAlertLog alertLog,
      SlaService sla,
      EscalationPolicy escalationPolicy,
      IncidentEventPublisher publisher,
      Clock clock) {
    this.incidents = incidents;
    this.users = users;
    this.alertLog = alertLog;
    this.sla = sla;
    this.escalationPolicy = escalationPolicy;
    this.publisher = publisher;
    this.clock = clock;
  }

  /** Summary of one run, for logs and the admin dashboard. */
  public record RunResult(
      int incidentsChecked, int warnings, int violations, int escalations, int skipped) {}

  public RunResult runCheck() {
    SlaCalculator calculator = sla.calculator();
    List<Incident> open = incidents.findOpen();
    Map<IncidentId, Incident> byId =
        open.stream().collect(Collectors.toMap(Incident::id, Function.identity()));

    SlaScanResult scan = new SlaMonitor(calculator, alertLog).scan(open);
    for (SlaAlert alert : scan.newAlerts()) {
      publisher.publish(SlaAlertRaised.of(alert, byId.get(alert.incidentId())));
    }
    scan.skipped().forEach((id, why) -> LOG.warn("SLA check skipped {}: {}", id, why));

    int escalations = 0;
    for (Incident incident : open) {
      Optional<SlaSnapshot> snapshot = SlaService.snapshot(incident, calculator);
      if (snapshot.isEmpty()) {
        continue;
      }
      int required = escalationPolicy.requiredLevel(incident, snapshot.get());
      int before = incident.escalationLevel();
      while (incident.escalationLevel() < required) {
        if (!escalateOneLevel(incident, snapshot.get())) {
          break; // nobody to escalate to - try again next run
        }
      }
      if (incident.escalationLevel() > before) {
        escalations += incident.escalationLevel() - before;
        incidents.save(incident);
        publisher.publishAll(incident.pullEvents());
      }
    }
    RunResult result =
        new RunResult(
            open.size(),
            scan.warnings().size(),
            scan.violations().size(),
            escalations,
            scan.skipped().size());
    if (result.warnings() + result.violations() + result.escalations() > 0) {
      LOG.info("SLA check: {}", result);
    }
    return result;
  }

  /**
   * @return false if no suitable person exists for the next level
   */
  private boolean escalateOneLevel(Incident incident, SlaSnapshot snapshot) {
    int level = incident.escalationLevel() + 1;
    Instant now = clock.instant();
    Duration target =
        Duration.between(snapshot.response().startedAt(), snapshot.response().dueAt());
    String reason =
        "Not acknowledged within the %s response target (%d min)"
            .formatted(snapshot.priority(), target.toMinutes());

    Role role = EscalationPolicy.roleForLevel(Math.min(level, EscalationPolicy.maxLevel()));
    if (role == Role.TEAM_LEAD) {
      Optional<Technician> lead = availableLeadFor(incident);
      if (lead.isPresent()) {
        incident.escalate(level, lead.get(), reason, SYSTEM, now);
        incident.assignTo(lead.get(), SYSTEM, now, "Escalation level " + level + ": " + reason);
        return true;
      }
      // no lead has capacity: still alert one, without moving the work
      Optional<User> anyLead = supervisor(Role.TEAM_LEAD);
      if (anyLead.isPresent()) {
        incident.escalate(
            level, anyLead.get(), reason + "; no team lead has free capacity", SYSTEM, now);
        return true;
      }
    }
    Optional<User> admin = supervisor(Role.ADMINISTRATOR);
    if (admin.isEmpty()) {
      return false;
    }
    incident.escalate(level, admin.get(), reason, SYSTEM, now);
    return true;
  }

  /** Team lead with free capacity, preferring skill in the incident's category, then low load. */
  private Optional<Technician> availableLeadFor(Incident incident) {
    String category = incident.category().map(c -> c.code()).orElse("");
    Technician current = incident.assignee().orElse(null);
    return users.findTechnicians().stream()
        .filter(t -> t.role() == Role.TEAM_LEAD)
        .filter(Technician::canTakeMoreWork)
        .filter(t -> !t.equals(current))
        .min(
            Comparator.<Technician>comparingInt(
                    t -> -t.skillFor(category).map(s -> s.proficiency()).orElse(0))
                .thenComparingDouble(Technician::utilisation));
  }

  private Optional<User> supervisor(Role role) {
    return users.withPermission(Permission.RECEIVE_ESCALATIONS).stream()
        .filter(u -> u.role() == role)
        .findFirst();
  }
}
