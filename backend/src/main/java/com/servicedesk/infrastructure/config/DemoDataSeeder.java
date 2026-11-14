package com.servicedesk.infrastructure.config;

import com.servicedesk.domain.assignment.TechnicianAssigner;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.audit.AuditTrailListener;
import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.incident.NewIncident;
import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.incident.event.IncidentEventPublisher;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.PriorityStrategy;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a realistic week of sample incidents the first time the application starts against an
 * empty database (disable with {@code app.demo.enabled=false}).
 *
 * <p>Incidents are created through the <b>domain model</b> - factory, classification, the active
 * priority and assignment strategies, lifecycle methods - with back-dated timestamps, so each one
 * has a consistent priority, assignee, SLA history and complete audit trail. Only the audit
 * listener is attached while seeding, so no notifications are sent for historical data.
 *
 * <p>The data deliberately includes every lifecycle state, some SLA breaches, three near-identical
 * payment reports (for duplicate detection) and one recent unacknowledged P1 that the first SLA
 * check will escalate.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);
  private static final SystemActor SYSTEM = SystemActor.INSTANCE;

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final ItServiceRepository services;
  private final CategoryRepository categories;
  private final StrategyRegistry<PriorityStrategy> priorityStrategies;
  private final TechnicianAssigner assigner;
  private final IncidentEventPublisher auditOnly;
  private final Clock clock;

  private SeedClock seedClock;
  private IncidentFactory factory;
  private List<Technician> team;

  public DemoDataSeeder(
      IncidentRepository incidents,
      UserRepository users,
      ItServiceRepository services,
      CategoryRepository categories,
      StrategyRegistry<PriorityStrategy> priorityStrategies,
      TechnicianAssigner assigner,
      AuditLog auditLog,
      Clock clock) {
    this.incidents = incidents;
    this.users = users;
    this.services = services;
    this.categories = categories;
    this.priorityStrategies = priorityStrategies;
    this.assigner = assigner;
    this.auditOnly = new IncidentEventPublisher().subscribe(new AuditTrailListener(auditLog));
    this.clock = clock;
  }

  /** How far through the lifecycle a sample incident gets. */
  enum Stage {
    NEW,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    AWAITING_INFO,
    RESOLVED,
    CLOSED,
    CANCELLED
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (incidents.count(IncidentQuery.all()) > 0) {
      return;
    }
    Instant now = clock.instant();
    seedClock = new SeedClock(now);
    factory = new IncidentFactory(incidents, seedClock);
    team = users.findTechnicians();

    // ---- last week: mostly finished work -------------------------------------------------
    seed(
        "E101",
        "PAYMENT",
        "APPLICATION",
        "Payment gateway timing out at checkout",
        "Card payments time out after 30 seconds for most customers since 09:40.",
        Level.CRITICAL,
        Level.HIGH,
        400,
        ago(now, 6, 0),
        Stage.CLOSED,
        25,
        5,
        "Scaled out payment API pods",
        "Traffic spike exhausted connection pool");
    seed(
        "E102",
        "VPN",
        "NETWORK",
        "VPN disconnects every 30 minutes",
        "Remote staff are dropped from the VPN roughly every half hour and must log in again.",
        Level.HIGH,
        Level.MEDIUM,
        40,
        ago(now, 6, 2),
        Stage.CLOSED,
        300,
        4,
        "Increased VPN session timeout",
        "Idle timeout misconfigured after upgrade");
    seed(
        "E103",
        "EMAIL",
        "SOFTWARE",
        "Outlook not syncing shared calendar",
        "The shared HR calendar has not updated in Outlook since yesterday afternoon.",
        Level.MEDIUM,
        Level.LOW,
        1,
        ago(now, 5, 4),
        Stage.CLOSED,
        360,
        3,
        "Rebuilt the local Outlook profile",
        "Corrupted offline cache");
    seed(
        "E104",
        "CORE_DB",
        "DATABASE",
        "Reporting queries extremely slow",
        "Daily sales reports take over 10 minutes instead of under one minute.",
        Level.HIGH,
        Level.HIGH,
        60,
        ago(now, 5, 1),
        Stage.CLOSED,
        180,
        4,
        "Added missing index on orders.created_at",
        "Index dropped during migration");
    seed(
        "E105",
        "WIFI",
        "NETWORK",
        "Wi-Fi drops on the third floor",
        "Laptops on the third floor lose Wi-Fi every few minutes since Monday.",
        Level.MEDIUM,
        Level.MEDIUM,
        25,
        ago(now, 4, 3),
        Stage.RESOLVED,
        240,
        0,
        "Replaced faulty access point AP-3F-02",
        "Hardware fault in access point");
    seed(
        "E106",
        "AUTH",
        "AUTHENTICATION",
        "SSO login loop for new joiners",
        "New employees are redirected back to the login page endlessly after entering credentials.",
        Level.HIGH,
        Level.HIGH,
        15,
        ago(now, 4, 0),
        Stage.CLOSED,
        90,
        5,
        "Assigned missing default group to new accounts",
        "Provisioning script skipped group step");
    seed(
        "E101",
        "ERP",
        "APPLICATION",
        "Month-end close job failed",
        "The ERP month-end close batch job failed twice with error FIN-4012.",
        Level.HIGH,
        Level.CRITICAL,
        10,
        ago(now, 3, 5),
        Stage.CLOSED,
        200,
        2,
        "Re-ran job after clearing locked ledger rows",
        "Stale lock left by aborted session");
    seed(
        "E102",
        "CLOUD_STORAGE",
        "STORAGE",
        "Shared drive quota exceeded",
        "The sales shared drive is full and nobody can upload new proposals.",
        Level.LOW,
        Level.MEDIUM,
        8,
        ago(now, 3, 2),
        Stage.CLOSED,
        120,
        4,
        "Raised quota and archived old files",
        "Quota not reviewed for two years");
    seed(
        "E103",
        "PRINTING",
        "HARDWARE",
        "Printer on second floor keeps jamming",
        "The second-floor printer jams on every double-sided job.",
        Level.LOW,
        Level.LOW,
        5,
        ago(now, 3, 0),
        Stage.CANCELLED,
        0,
        0,
        null,
        null);
    seed(
        "E104",
        "HR_PORTAL",
        "APPLICATION",
        "Payslip PDF download fails",
        "Clicking 'Download payslip' shows an error page for all employees.",
        Level.MEDIUM,
        Level.HIGH,
        120,
        ago(now, 2, 4),
        Stage.RESOLVED,
        150,
        0,
        "Renewed expired document-service certificate",
        "Expired TLS certificate");

    // ---- open work ------------------------------------------------------------------------
    seed(
        "E105",
        "WIKI",
        "SOFTWARE",
        "Wiki search returns no results",
        "Searching the internal wiki returns nothing, even for page titles.",
        Level.LOW,
        Level.LOW,
        30,
        ago(now, 2, 0),
        Stage.IN_PROGRESS,
        0,
        0,
        null,
        null);
    seed(
        "E106",
        "VPN",
        "NETWORK",
        "Cannot connect to VPN from home",
        "The VPN client shows 'authentication failed' although my password works elsewhere.",
        Level.MEDIUM,
        Level.HIGH,
        3,
        ago(now, 1, 3),
        Stage.AWAITING_INFO,
        0,
        0,
        null,
        null);
    seed(
        "E101",
        "EMAIL",
        "SOFTWARE",
        "Emails delayed by 20 minutes",
        "Incoming external e-mails arrive about twenty minutes late since this morning.",
        Level.HIGH,
        Level.MEDIUM,
        200,
        ago(now, 1, 0),
        Stage.IN_PROGRESS,
        0,
        0,
        null,
        null);
    seed(
        "E102",
        "CORE_DB",
        "DATABASE",
        "Replication lag on reporting replica",
        "The reporting replica is about 40 minutes behind the primary database.",
        Level.MEDIUM,
        Level.MEDIUM,
        5,
        now.minus(Duration.ofHours(20)),
        Stage.ASSIGNED,
        0,
        0,
        null,
        null);
    seed(
        "E103",
        "AUTH",
        "SECURITY",
        "Suspicious login attempts on finance accounts",
        "Several finance accounts show repeated failed logins from unknown locations overnight.",
        Level.HIGH,
        Level.CRITICAL,
        12,
        now.minus(Duration.ofHours(6)),
        Stage.IN_PROGRESS,
        0,
        0,
        null,
        null);
    seed(
        "E104",
        "ERP",
        "APPLICATION",
        "Purchase order approval button missing",
        "Managers no longer see the 'Approve' button on purchase orders.",
        Level.MEDIUM,
        Level.MEDIUM,
        15,
        now.minus(Duration.ofHours(4)),
        Stage.ACKNOWLEDGED,
        0,
        0,
        null,
        null);
    seed(
        "E105",
        "CLOUD_STORAGE",
        null,
        "File sync stuck at processing",
        "My desktop sync client has shown 'processing' for three hours and nothing uploads.",
        Level.MEDIUM,
        Level.LOW,
        4,
        now.minus(Duration.ofHours(3)),
        Stage.NEW,
        0,
        0,
        null,
        null);
    seed(
        "E106",
        "PRINTING",
        "HARDWARE",
        "Label printer offline in warehouse",
        "The warehouse label printer shows offline, so parcels cannot be shipped.",
        Level.MEDIUM,
        Level.HIGH,
        6,
        now.minus(Duration.ofHours(2)),
        Stage.ACKNOWLEDGED,
        0,
        0,
        null,
        null);

    // ---- three reports of what is probably the same payment outage --------------------
    seed(
        "E101",
        "PAYMENT",
        "APPLICATION",
        "Payment application unavailable",
        "The payment application shows a 503 error and users are unable to complete transactions.",
        Level.CRITICAL,
        Level.CRITICAL,
        500,
        now.minus(Duration.ofMinutes(40)),
        Stage.IN_PROGRESS,
        0,
        0,
        null,
        null);
    seed(
        "E104",
        "PAYMENT",
        "APPLICATION",
        "Payment transaction failing",
        "Every card transaction fails at the confirmation step with 'service unavailable'.",
        Level.HIGH,
        Level.CRITICAL,
        50,
        now.minus(Duration.ofMinutes(30)),
        Stage.ACKNOWLEDGED,
        0,
        0,
        null,
        null);
    seed(
        "E106",
        "PAYMENT",
        "APPLICATION",
        "Unable to make payment",
        "I am unable to make a payment for a customer order; the page keeps loading.",
        Level.HIGH,
        Level.HIGH,
        20,
        now.minus(Duration.ofMinutes(25)),
        Stage.ASSIGNED,
        0,
        0,
        null,
        null);
    seed(
        "E105",
        "WIFI",
        null,
        "Office Wi-Fi slow in conference rooms",
        "Video calls in the conference rooms freeze because the Wi-Fi is very slow.",
        Level.LOW,
        Level.MEDIUM,
        10,
        now.minus(Duration.ofMinutes(30)),
        Stage.NEW,
        0,
        0,
        null,
        null);

    LOG.info("Demo data: created {} sample incidents", incidents.count(IncidentQuery.all()));
  }

  /**
   * Creates one incident at {@code created} and moves it to {@code stage}.
   *
   * @param resolveAfterMin minutes from creation to resolution (RESOLVED / CLOSED)
   * @param rating feedback stars for CLOSED incidents (0 = none)
   */
  private void seed(
      String reporterId,
      String serviceCode,
      String categoryCode,
      String title,
      String description,
      Level impact,
      Level urgency,
      int affected,
      Instant created,
      Stage stage,
      int resolveAfterMin,
      int rating,
      String resolution,
      String rootCause) {
    seedClock.set(created);
    User reporter = users.get(new UserId(reporterId));
    Incident i =
        factory.create(
            new NewIncident(
                reporter,
                title,
                description,
                services.get(serviceCode),
                impact,
                urgency,
                affected));
    if (stage != Stage.NEW && categoryCode != null) {
      Instant t = created.plusSeconds(30);
      i.classify(categories.get(categoryCode), SYSTEM, t);
      i.prioritize(priorityStrategies.active(), SYSTEM, t);
      try {
        assigner.assign(i, team, SYSTEM, created.plusSeconds(60));
      } catch (TechnicianUnavailableException e) {
        finish(i);
        return;
      }
      Technician tech = i.assignee().orElseThrow();
      if (stage == Stage.CANCELLED) {
        i.cancel(
            reporter,
            "The printer started working again after a restart",
            created.plusSeconds(600));
        finish(i);
        return;
      }
      if (stage != Stage.ASSIGNED) {
        i.acknowledge(tech, created.plusSeconds(120));
      }
      if (stage.ordinal() >= Stage.IN_PROGRESS.ordinal()) {
        i.startWork(tech, created.plusSeconds(300));
        i.addNote(
            tech,
            NoteType.INVESTIGATION,
            "Reproduced the problem; checking logs.",
            created.plusSeconds(420));
      }
      if (stage == Stage.AWAITING_INFO) {
        i.requestInfo(
            tech,
            "Which VPN client version is installed on your laptop?",
            created.plusSeconds(900));
      }
      if (stage == Stage.RESOLVED || stage == Stage.CLOSED) {
        Instant resolvedAt = created.plus(Duration.ofMinutes(resolveAfterMin));
        i.resolve(tech, resolution, rootCause, resolvedAt);
        if (stage == Stage.CLOSED) {
          i.verify(reporter, resolvedAt.plus(Duration.ofMinutes(30)));
          if (rating > 0) {
            i.giveFeedback(
                reporter,
                rating,
                rating >= 4 ? "Quick and clear, thanks" : "Took a while",
                resolvedAt.plus(Duration.ofMinutes(31)));
          }
          i.close(reporter, "Confirmed fixed", resolvedAt.plus(Duration.ofMinutes(32)));
        }
      }
    }
    finish(i);
  }

  private void finish(Incident i) {
    incidents.save(i);
    auditOnly.publishAll(i.pullEvents());
  }

  private static Instant ago(Instant now, int days, int hours) {
    return now.minus(Duration.ofDays(days)).minus(Duration.ofHours(hours));
  }

  /** Clock the factory reads creation times from while seeding. */
  private static final class SeedClock extends Clock {
    private Instant now;

    SeedClock(Instant start) {
      this.now = start;
    }

    void set(Instant instant) {
      this.now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
