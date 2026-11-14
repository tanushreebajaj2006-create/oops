package com.servicedesk.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.audit.AuditQuery;
import com.servicedesk.domain.audit.AuditTrailListener;
import com.servicedesk.domain.exception.EntityNotFoundException;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.incident.NewIncident;
import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.incident.event.IncidentEventPublisher;
import com.servicedesk.domain.notification.InAppInbox;
import com.servicedesk.domain.notification.Notification;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.sla.SlaAlertLog;
import com.servicedesk.domain.sla.SlaKind;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaPolicyCatalog;
import com.servicedesk.domain.sla.SlaPolicyRepository;
import com.servicedesk.domain.sla.SlaTarget;
import com.servicedesk.domain.user.Employee;
import com.servicedesk.domain.user.NewUser;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.TeamLead;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserFactory;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence layer against the real Flyway schema (H2 in PostgreSQL mode). Every test runs in a
 * transaction that is rolled back, and {@link #reload()} flushes and clears the persistence context
 * so assertions read what is really in the database, not cached objects.
 */
@SpringBootTest
@Transactional
@DisplayName("Persistence (Flyway schema + JPA repositories)")
class PersistenceIntegrationTest {

  static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

  @Autowired IncidentRepository incidents;
  @Autowired UserRepository users;
  @Autowired ItServiceRepository services;
  @Autowired CategoryRepository categories;
  @Autowired SlaPolicyRepository slaPolicies;
  @Autowired AuditLog auditLog;
  @Autowired InAppInbox inbox;
  @Autowired SlaAlertLog slaAlerts;
  @Autowired EntityManager em;

  private IncidentFactory factory;
  private final SystemActor system = SystemActor.INSTANCE;

  @BeforeEach
  void setUp() {
    factory = new IncidentFactory(incidents, Clock.fixed(T0, ZoneOffset.UTC));
  }

  private void reload() {
    em.flush();
    em.clear();
  }

  private static Instant at(int minutes) {
    return T0.plus(Duration.ofMinutes(minutes));
  }

  private Incident report(String reporter, String service, String title, Level impact) {
    return factory.create(
        new NewIncident(
            users.get(new UserId(reporter)),
            title,
            title + " - reported from the finance floor",
            services.get(service),
            impact,
            Level.HIGH,
            20));
  }

  // ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("TC-78 seed data: categories, services, SLA policies and one account per role")
  void seedData() {
    assertThat(categories.findAll()).hasSize(9);
    assertThat(services.findAll()).hasSize(11);
    assertThat(services.get("payment").criticality()).isEqualTo(Level.CRITICAL);

    SlaPolicyCatalog catalog = slaPolicies.loadCatalog();
    ItService payment = services.get("PAYMENT");
    assertThat(catalog.policyFor(payment).name()).isEqualTo("Payments Gold");
    assertThat(catalog.targetFor(payment, Priority.P1).resolution())
        .isEqualTo(Duration.ofMinutes(15));
    assertThat(catalog.targetFor(payment, Priority.P4).resolution())
        .isEqualTo(Duration.ofHours(24));
    assertThat(catalog.targetFor(services.get("WIKI"), Priority.P1).response())
        .isEqualTo(Duration.ofMinutes(5));

    assertThat(users.findAll())
        .extracting(User::role)
        .contains(Role.ADMINISTRATOR, Role.TEAM_LEAD, Role.TECHNICIAN, Role.EMPLOYEE);
    assertThat(users.findTechnicians()).hasSize(8);
    Technician kavya = (Technician) users.get(new UserId("T203"));
    assertThat(kavya.skillFor("DATABASE")).isPresent().get().extracting("proficiency").isEqualTo(4);
    assertThat(users.get(new UserId("L301"))).isInstanceOf(TeamLead.class);
    assertThat(users.withPermission(Permission.RECEIVE_ESCALATIONS))
        .extracting(u -> u.id().value())
        .containsExactly("A001", "L301", "L302");
    // the documented demo password matches the stored BCrypt hash
    User admin = users.findByEmail("ADMIN@servicedesk.example").orElseThrow();
    assertThat(new BCryptPasswordEncoder().matches("Demo@12345", admin.passwordHash())).isTrue();
  }

  @Test
  @DisplayName("TC-79 an incident survives a full save/reload round-trip at every lifecycle step")
  void incidentRoundTrip() {
    Incident i = report("E101", "PAYMENT", "Payment app unavailable", Level.CRITICAL);
    assertThat(i.id().value()).matches("INC\\d{4,}");
    incidents.save(i);

    Technician neha = (Technician) users.get(new UserId("T201"));
    i.classify(categories.get("APPLICATION"), system, at(1));
    i.prioritize(new MatrixPriorityStrategy(), system, at(1));
    i.assignTo(neha, system, at(2));
    i.acknowledge(neha, at(3));
    i.startWork(neha, at(4));
    i.addNote(neha, NoteType.INVESTIGATION, "Pods restarting with OOM", at(5));
    i.requestInfo(neha, "Which browser are you using?", at(6));
    i.provideInfo(i.reporter(), "Chrome 130", at(7));
    i.resolve(neha, "Raised memory limit", "Memory leak in v2.3", at(10));
    i.giveFeedback(i.reporter(), 5, "Quick fix, thanks", at(11));
    i.verify(i.reporter(), at(12));
    i.close(i.reporter(), "Confirmed working", at(13));
    incidents.save(i);
    reload();

    Incident loaded = incidents.get(i.id());
    assertThat(loaded.snapshot()).isEqualTo(i.snapshot());
    assertThat(loaded.status()).isEqualTo(IncidentStatus.CLOSED);
    assertThat(loaded.notes()).hasSize(3);
    assertThat(loaded.notes().get(0).author()).isEqualTo(neha);
    assertThat(loaded.resolution()).get().extracting("summary").isEqualTo("Raised memory limit");
    assertThat(loaded.feedback()).get().extracting("rating").isEqualTo(5);
    assertThat(loaded.pendingEvents()).isEmpty(); // loading is not an event
    assertThat(loaded.allowedActions()).isEmpty();
  }

  @Test
  @DisplayName("TC-80 technician workload is recomputed from open incidents in the database")
  void workloadFromDatabase() {
    UserId arjunId = new UserId("T202");
    for (int n = 0; n < 2; n++) {
      Incident i = report("E102", "VPN", "VPN drops every hour " + n, Level.HIGH);
      i.classify(categories.get("NETWORK"), system, at(1));
      i.applyPriority(Priority.P3, system, at(1));
      i.assignTo((Technician) users.get(arjunId), system, at(2));
      incidents.save(i);
    }
    reload();
    Technician arjun = (Technician) users.get(arjunId);
    assertThat(arjun.activeWorkload()).isEqualTo(2);

    Incident first = incidents.search(IncidentQuery.all().assignedTo(arjunId)).get(0);
    first.acknowledge(arjun, at(3));
    first.startWork(arjun, at(4));
    first.resolve(arjun, "Replaced router firmware", null, at(5));
    incidents.save(first);
    reload();
    assertThat(((Technician) users.get(arjunId)).activeWorkload()).isEqualTo(1);
    assertThat(users.findTechnicians())
        .filteredOn(t -> t.id().equals(arjunId))
        .singleElement()
        .extracting(Technician::activeWorkload)
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "TC-81 incident search supports id, status, priority, category, service, technician,"
          + " reporter, date and text")
  void search() {
    Incident pay = report("E101", "PAYMENT", "Card payments failing", Level.CRITICAL);
    pay.classify(categories.get("APPLICATION"), system, at(1));
    pay.applyPriority(Priority.P1, system, at(1));
    pay.assignTo((Technician) users.get(new UserId("T201")), system, at(2));
    incidents.save(pay);

    Incident mail =
        factory.create(
            new NewIncident(
                users.get(new UserId("E103")),
                "Mailbox quota 100% full",
                "Cannot receive mail since the quota hit 100%",
                services.get("EMAIL"),
                Level.LOW,
                Level.MEDIUM,
                1));
    mail.classify(categories.get("SOFTWARE"), system, at(1));
    mail.applyPriority(Priority.P4, system, at(1));
    incidents.save(mail);

    Incident wiki = report("E103", "WIKI", "Wiki search returns nothing", Level.LOW);
    incidents.save(wiki);
    reload();

    IncidentQuery mine = IncidentQuery.all().createdBetween(T0.minusSeconds(1), T0.plusSeconds(1));
    assertThat(incidents.search(mine.withId(pay.id())))
        .extracting(Incident::id)
        .containsExactly(pay.id());
    assertThat(incidents.search(mine.withStatuses(Set.of(IncidentStatus.NEW))))
        .extracting(Incident::id)
        .containsExactly(wiki.id());
    assertThat(incidents.search(mine.withPriorities(Set.of(Priority.P1, Priority.P4))))
        .extracting(Incident::id)
        .containsExactlyInAnyOrder(pay.id(), mail.id());
    assertThat(incidents.search(mine.withCategory("software")))
        .extracting(Incident::id)
        .containsExactly(mail.id());
    assertThat(incidents.search(mine.withService("PAYMENT")))
        .extracting(Incident::id)
        .containsExactly(pay.id());
    assertThat(incidents.search(mine.assignedTo(new UserId("T201"))))
        .extracting(Incident::id)
        .containsExactly(pay.id());
    assertThat(incidents.search(mine.reportedBy(new UserId("E103")))).hasSize(2);
    // '%' in the search text is a literal character, not a wildcard
    assertThat(incidents.search(mine.containing("100%")))
        .extracting(Incident::id)
        .containsExactly(mail.id());
    assertThat(incidents.search(mine.containing("PAYMENTS")))
        .extracting(Incident::id)
        .containsExactly(pay.id());
    // combined filters + count + paging
    assertThat(incidents.count(mine.reportedBy(new UserId("E103")).withService("WIKI")))
        .isEqualTo(1);
    assertThat(incidents.count(mine)).isEqualTo(3);
    assertThat(incidents.search(mine.page(2, 0))).hasSize(2);
    assertThat(incidents.search(mine.page(2, 2))).hasSize(1);
    assertThat(incidents.findOpen())
        .extracting(Incident::id)
        .contains(pay.id(), mail.id(), wiki.id());
    assertThat(incidents.find(new IncidentId("INC99999999"))).isEmpty();
    assertThatThrownBy(() -> incidents.get(new IncidentId("INC99999999")))
        .isInstanceOf(EntityNotFoundException.class);
  }

  @Test
  @DisplayName(
      "TC-82 users: generated ids, unique e-mail, phone and active flag persist; SYSTEM resolves")
  void users() {
    UserId id = users.nextId(Role.EMPLOYEE);
    assertThat(id.value()).matches("E\\d+");
    User newcomer =
        UserFactory.create(
            new NewUser(
                id,
                Role.EMPLOYEE,
                "New Joiner",
                "new.joiner@servicedesk.example",
                "hash",
                "Legal",
                null,
                0,
                null));
    newcomer.changePhone("+919811112222");
    users.save(newcomer);
    reload();

    User loaded = users.findByEmail("New.Joiner@servicedesk.example").orElseThrow();
    assertThat(loaded).isInstanceOf(Employee.class).isEqualTo(newcomer);
    assertThat(((Employee) loaded).department()).isEqualTo("Legal");
    assertThat(loaded.phone()).contains("+919811112222");

    loaded.deactivate();
    users.save(loaded);
    reload();
    assertThat(users.get(id).isActive()).isFalse();

    User clash =
        UserFactory.create(
            new NewUser(
                users.nextId(Role.EMPLOYEE),
                Role.EMPLOYEE,
                "Clash",
                "asha.rao@servicedesk.example",
                "hash",
                null,
                null,
                0,
                null));
    assertThatThrownBy(() -> users.save(clash))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("already registered");
    assertThat(users.get(new UserId("SYSTEM"))).isSameAs(SystemActor.INSTANCE);
    assertThatThrownBy(() -> users.save(SystemActor.INSTANCE))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  @DisplayName(
      "TC-83 audit trail, in-app inbox and SLA alerts are stored and queried from the database")
  void auditInboxAndAlerts() {
    IncidentEventPublisher publisher =
        new IncidentEventPublisher().subscribe(new AuditTrailListener(auditLog));
    Incident i = report("E104", "ERP", "ERP month-end job stuck", Level.HIGH);
    incidents.save(i);
    i.classify(categories.get("APPLICATION"), system, at(1));
    i.applyPriority(Priority.P2, system, at(1));
    incidents.save(i);
    publisher.publishAll(i.pullEvents());
    reload();

    assertThat(auditLog.historyOf(i.id()))
        .extracting(e -> e.action())
        .containsExactly("CREATED", "CLASSIFIED", "PRIORITY_CHANGED");
    assertThat(auditLog.historyOf(i.id()).get(1).previousStatus()).isEqualTo(IncidentStatus.NEW);
    assertThat(auditLog.search(AuditQuery.forIncident(i.id()).byActor(new UserId("E104"))))
        .hasSize(1);

    UserId asha = new UserId("E101");
    var item =
        inbox.add(
            new Notification(
                asha, "IN_APP", "INC update", "Body", i.id(), EventType.ASSIGNED, false, at(2)));
    inbox.add(
        new Notification(
            asha, "IN_APP", "Second", "Body", i.id(), EventType.RESOLVED, true, at(3)));
    reload();
    assertThat(inbox.itemsFor(asha))
        .extracting(x -> x.notification().subject())
        .containsExactly("Second", "INC update");
    assertThat(inbox.unreadCount(asha)).isEqualTo(2);
    assertThat(inbox.markRead(new UserId("E102"), item.id())).isFalse();
    assertThat(inbox.markRead(asha, item.id())).isTrue();
    reload();
    assertThat(inbox.unreadCount(asha)).isEqualTo(1);

    SlaAlert warning =
        new SlaAlert(
            SlaAlert.Type.WARNING, i.id(), SlaKind.RESPONSE, Priority.P2, at(15), at(12), null);
    slaAlerts.record(warning);
    slaAlerts.record(warning); // second time is ignored
    slaAlerts.record(
        new SlaAlert(
            SlaAlert.Type.VIOLATION,
            i.id(),
            SlaKind.RESPONSE,
            Priority.P2,
            at(15),
            at(16),
            new UserId("T201")));
    reload();
    assertThat(slaAlerts.hasRaised(i.id(), SlaKind.RESPONSE, SlaAlert.Type.WARNING)).isTrue();
    assertThat(slaAlerts.forIncident(i.id())).hasSize(2);
    assertThat(slaAlerts.violations()).extracting(SlaAlert::incidentId).contains(i.id());
  }

  @Test
  @DisplayName("TC-84 SLA policies can be added, changed and removed by configuration")
  void slaPolicyConfiguration() {
    slaPolicies.save(
        SlaPolicy.forService("VPN Priority", "VPN")
            .setTarget(Priority.P1, SlaTarget.of(Duration.ofMinutes(3), Duration.ofMinutes(20))));
    SlaPolicy standard = SlaPolicy.standard();
    standard.setWarningThreshold(0.8);
    slaPolicies.save(standard); // replaces the existing default, does not add a second one
    reload();

    SlaPolicyCatalog catalog = slaPolicies.loadCatalog();
    assertThat(catalog.all()).hasSize(3);
    assertThat(catalog.policyFor(services.get("VPN")).name()).isEqualTo("VPN Priority");
    assertThat(catalog.defaultPolicy())
        .get()
        .extracting(SlaPolicy::warningThreshold)
        .isEqualTo(0.8);

    slaPolicies.deleteServicePolicy("vpn");
    reload();
    assertThat(slaPolicies.loadCatalog().policyFor(services.get("VPN")).name())
        .isEqualTo("Standard SLA");
  }
}
