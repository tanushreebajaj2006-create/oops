package com.servicedesk.domain.intelligence;

import static com.servicedesk.domain.support.TestData.minutes;
import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.domain.assignment.AssignmentCandidate;
import com.servicedesk.domain.assignment.SmartAssignmentStrategy;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.incident.NewIncident;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaPolicyCatalog;
import com.servicedesk.domain.support.TestData;
import com.servicedesk.domain.user.Technician;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Intelligent features (pure algorithms)")
class IntelligenceAlgorithmsTest {

  private final TestData data = new TestData();
  private final ItService vpn = new ItService("VPN", "Remote Access VPN", Level.HIGH);
  private final ItService wikiService = data.wiki;
  private final Category application = new Category("APPLICATION", "Application");
  private final List<Category> categories =
      NaiveBayesCategoryClassifier.defaultKeywords().keySet().stream()
          .map(code -> new Category(code, code.charAt(0) + code.substring(1).toLowerCase()))
          .toList();

  private Incident report(ItService service, String title, String description, Instant at) {
    data.clock.set(at);
    return data.factory.create(
        new NewIncident(data.employee, title, description, service, Level.HIGH, Level.HIGH, 20));
  }

  /** Classified, prioritised, assigned to {@code tech} and resolved {@code fixMinutes} later. */
  private Incident resolved(
      ItService service,
      String title,
      String description,
      Category category,
      Priority priority,
      Technician tech,
      Instant at,
      int fixMinutes,
      String fix,
      int rating) {
    Incident i = report(service, title, description, at);
    i.classify(category, data.system, at);
    i.applyPriority(priority, data.system, at);
    i.assignTo(tech, data.system, at.plusSeconds(30));
    i.acknowledge(tech, at.plusSeconds(60));
    i.startWork(tech, at.plusSeconds(90));
    i.resolve(tech, fix, null, at.plus(Duration.ofMinutes(fixMinutes)));
    if (rating > 0) {
      i.giveFeedback(data.employee, rating, null, at.plus(Duration.ofMinutes(fixMinutes + 1)));
    }
    return i;
  }

  @Test
  @DisplayName(
      "TC-100 text analysis: stop words, stemming and IT synonyms merge different wordings")
  void textAnalysis() {
    assertThat(TextAnalyzer.terms("Payment application unavailable"))
        .containsExactly("payment", "application", "fail");
    assertThat(TextAnalyzer.terms("Payment transaction failing"))
        .containsExactly("payment", "transaction", "fail");
    assertThat(TextAnalyzer.terms("Unable to make payment")).containsExactly("fail", "payment");
    assertThat(TextAnalyzer.terms("Wi-Fi drops; the queries are slow!"))
        .containsExactly("wifi", "drop", "query", "slow");
    assertThat(TextAnalyzer.terms("  ")).isEmpty();
  }

  @Test
  @DisplayName("TC-101 cosine similarity: identical 1, unrelated 0, related wordings in between")
  void cosine() {
    CosineTextSimilarity cos = new CosineTextSimilarity();
    TextProfile a = TextProfile.of("Payment application unavailable", "users cannot pay");
    TextProfile b = TextProfile.of("Payment transaction failing", "card payments fail");
    TextProfile c = TextProfile.of("Wiki search empty", "no results in wiki search");
    assertThat(cos.similarity(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(cos.similarity(a, c)).isZero();
    assertThat(cos.similarity(a, b)).isBetween(0.5, 0.99);
    assertThat(a.sharedTerms(b)).contains("payment", "fail");
  }

  @Test
  @DisplayName(
      "TC-102 category classifier: suggests the right category with its evidence, learns new"
          + " categories")
  void classifier() {
    NaiveBayesCategoryClassifier nb =
        NaiveBayesCategoryClassifier.builder(categories)
            .withKeywords(NaiveBayesCategoryClassifier.defaultKeywords())
            .build();
    List<CategorySuggestion> vpnIssue =
        nb.suggest("Cannot connect to VPN from home", "The VPN client drops the connection");
    assertThat(vpnIssue.get(0).categoryCode()).isEqualTo("NETWORK");
    assertThat(vpnIssue.get(0).matchedTerms()).contains("vpn");
    assertThat(CategoryClassifier.isConfident(vpnIssue)).isTrue();

    assertThat(
            nb.suggest("Laptop screen flickering", "The monitor of my laptop flickers")
                .get(0)
                .categoryCode())
        .isEqualTo("HARDWARE");
    assertThat(
            nb.suggest("Deadlock in orders database", "Query on the orders table hangs")
                .get(0)
                .categoryCode())
        .isEqualTo("DATABASE");
    assertThat(
            nb.suggest("Phishing e-mail received", "Suspicious message asking for my password")
                .get(0)
                .categoryCode())
        .isEqualTo("SECURITY");
    assertThat(nb.suggest("xyzzy plugh", "qwerty")).isEmpty();
    assertThat(vpnIssue.stream().mapToDouble(CategorySuggestion::confidence).sum())
        .isLessThanOrEqualTo(1.0001);

    // an administrator adds a category the keywords know nothing about; history teaches it
    List<Category> withNew = new java.util.ArrayList<>(categories);
    withNew.add(new Category("PRINTING_3D", "3D printing"));
    NaiveBayesCategoryClassifier trained =
        NaiveBayesCategoryClassifier.builder(withNew)
            .withKeywords(NaiveBayesCategoryClassifier.defaultKeywords())
            .train(
                "Filament jammed in 3D printer",
                "The extruder clogs on the 3D printer",
                "PRINTING_3D")
            .train(
                "3D printer bed not heating",
                "Extruder and bed of the 3D printer are cold",
                "PRINTING_3D")
            .build();
    assertThat(
            trained
                .suggest("Extruder clogged", "the 3D printer extruder is blocked")
                .get(0)
                .categoryCode())
        .isEqualTo("PRINTING_3D");

    // retired categories are never suggested
    List<Category> retired =
        categories.stream().map(c -> c.code().equals("NETWORK") ? c.retire() : c).toList();
    assertThat(
            NaiveBayesCategoryClassifier.builder(retired)
                .withKeywords(NaiveBayesCategoryClassifier.defaultKeywords())
                .build()
                .suggest("VPN down", "VPN not working"))
        .extracting(CategorySuggestion::categoryCode)
        .doesNotContain("NETWORK");
  }

  @Test
  @DisplayName(
      "TC-103 duplicate detection finds the brief's three payment reports and explains why")
  void duplicates() {
    Incident first =
        report(
            data.payment,
            "Payment application unavailable",
            "The payment application shows a 503 error and users are unable to complete"
                + " transactions.",
            minutes(0));
    Incident second =
        report(
            data.payment,
            "Payment transaction failing",
            "Every card transaction fails at the confirmation step with service unavailable.",
            minutes(10));
    Incident unrelated =
        report(
            wikiService,
            "Wiki search returns nothing",
            "Searching the wiki gives no results.",
            minutes(12));
    Incident oldFixed =
        resolved(
            data.payment,
            "Payment application unavailable",
            "Payment app down for everyone",
            application,
            Priority.P1,
            TestData.tech("T1", 5, 3, TestData.skill("APPLICATION", 5)),
            minutes(-60 * 24 * 10),
            30,
            "Restarted pods",
            0);

    DuplicateDetector detector = new DuplicateDetector(new CosineTextSimilarity());
    IncidentFingerprint draft =
        IncidentFingerprint.draft(
            "Unable to make payment",
            "I am unable to make a payment, the page keeps loading.",
            "PAYMENT",
            minutes(15));
    List<RelatedIncident> found =
        detector.findRelated(draft, List.of(first, second, unrelated, oldFixed), minutes(15));

    assertThat(found)
        .extracting(RelatedIncident::incident)
        .containsExactlyInAnyOrder(first, second);
    assertThat(found)
        .allSatisfy(
            r -> {
              assertThat(r.verdict()).isEqualTo(RelatedIncident.Verdict.LIKELY_DUPLICATE);
              assertThat(r.reasons()).contains("same service PAYMENT");
              assertThat(r.reasons())
                  .anyMatch(s -> s.startsWith("shared terms") && s.contains("payment"));
            });
    // an existing incident is never reported as a duplicate of itself
    assertThat(
            detector.findRelated(
                IncidentFingerprint.of(first), List.of(first, second), minutes(15)))
        .extracting(RelatedIncident::incident)
        .containsExactly(second);
  }

  @Test
  @DisplayName(
      "TC-104 correlation: a burst on one service is a possible major incident; a category across"
          + " services is a shared component")
  void correlation() {
    Incident p1 =
        report(data.payment, "Payment application unavailable", "503 at checkout page", minutes(0));
    Incident p2 =
        report(data.payment, "Payment transaction failing", "card payments fail", minutes(5));
    Incident p3 = report(data.payment, "Unable to make payment", "page keeps loading", minutes(9));
    p1.classify(application, data.system, minutes(1));
    p1.applyPriority(Priority.P1, data.system, minutes(1));
    Incident lateWiki = report(wikiService, "Wiki slow", "wiki pages load slowly", minutes(0));
    Incident laterWiki = report(wikiService, "Wiki slow again", "wiki is slow again", minutes(300));

    Category auth = new Category("AUTHENTICATION", "Authentication");
    Incident a1 = report(vpn, "VPN login rejected", "authentication failed on VPN", minutes(20));
    Incident a2 = report(data.wiki, "Wiki login loop", "login page keeps returning", minutes(25));
    Incident a3 =
        report(
            new ItService("EMAIL", "Email", Level.HIGH),
            "Outlook password prompt",
            "keeps asking for password",
            minutes(30));
    for (Incident a : List.of(a1, a2, a3)) {
      a.classify(auth, data.system, minutes(31));
    }

    List<IncidentCluster> clusters =
        new IncidentCorrelator().correlate(List.of(p1, p2, p3, lateWiki, laterWiki, a1, a2, a3));
    assertThat(clusters.get(0).pattern()).isEqualTo(IncidentCluster.Pattern.SERVICE_OUTAGE);
    assertThat(clusters.get(0).key()).isEqualTo("PAYMENT");
    assertThat(clusters.get(0).incidents()).containsExactly(p1, p2, p3);
    assertThat(clusters.get(0).highestPriority()).isEqualTo(Priority.P1);
    assertThat(clusters.get(0).summary()).contains("possible major incident");
    assertThat(clusters.get(0).commonTerms()).contains("payment");
    assertThat(clusters)
        .anySatisfy(
            c -> {
              assertThat(c.pattern()).isEqualTo(IncidentCluster.Pattern.SHARED_COMPONENT);
              assertThat(c.key()).isEqualTo("AUTHENTICATION");
              assertThat(c.incidents()).hasSize(3);
            });
    // two wiki reports five hours apart are not one burst
    assertThat(clusters)
        .noneMatch(
            c ->
                c.key().equals("WIKI")
                    && c.incidents().size() == 2
                    && c.incidents().contains(lateWiki)
                    && c.incidents().contains(laterWiki));
  }

  @Test
  @DisplayName(
      "TC-105 SLA risk: predicted from typical fix time, workload and state before the deadline is"
          + " missed")
  void slaRiskPrediction() {
    Category db = new Category("DATABASE", "Database");
    Technician dba = TestData.tech("T3", 2, 5, TestData.skill("DATABASE", 4));
    ItService core = new ItService("CORE_DB", "Core DB", Level.HIGH);
    // history: P2 database incidents usually take ~3 hours to fix (SLA allows 2 h)
    List<Incident> past =
        List.of(
            resolved(
                core,
                "Slow query",
                "report query slow",
                db,
                Priority.P2,
                dba,
                minutes(-3000),
                170,
                "Index",
                0),
            resolved(
                core,
                "Replica lag",
                "replica behind",
                db,
                Priority.P2,
                dba,
                minutes(-2000),
                190,
                "Resync",
                0),
            resolved(
                core,
                "Deadlock",
                "deadlock on orders",
                db,
                Priority.P2,
                dba,
                minutes(-1000),
                180,
                "Kill session",
                0));
    IncidentHistory history = IncidentHistory.of(past);
    assertThat(history.typicalResolutionTime("DATABASE", Priority.P2))
        .contains(Duration.ofMinutes(180));
    assertThat(history.resolvedInCategory(dba.id(), "DATABASE")).isEqualTo(3);

    Incident open = report(core, "Reports timing out", "database reports time out", minutes(0));
    open.classify(db, data.system, minutes(0));
    open.applyPriority(Priority.P2, data.system, minutes(0));
    data.clock.set(minutes(20));
    SlaCalculator calc = new SlaCalculator(SlaPolicyCatalog.standard(), data.clock);
    SlaRiskPredictor predictor = new SlaRiskPredictor();

    SlaRisk risk = predictor.assess(open, calc.evaluate(open), history, minutes(20)).orElseThrow();
    assertThat(risk.level()).isEqualTo(SlaRisk.Level.HIGH);
    assertThat(risk.reasons()).anyMatch(r -> r.contains("usually fixed in 3 h"));
    assertThat(risk.reasons()).contains("not assigned to anyone yet");
    assertThat(risk.projectedResolution()).isAfter(risk.dueAt());

    // a P4 with the generous 24 h SLA and no history is low risk
    Incident minor = report(core, "Typo on report header", "header says 'Reprot'", minutes(20));
    minor.classify(db, data.system, minutes(20));
    minor.applyPriority(Priority.P4, data.system, minutes(20));
    assertThat(
            predictor
                .assess(minor, calc.evaluate(minor), history, minutes(20))
                .orElseThrow()
                .level())
        .isEqualTo(SlaRisk.Level.LOW);
  }

  @Test
  @DisplayName("TC-106 resolution suggestions reuse the fix of the most similar resolved incident")
  void resolutionSuggestions() {
    Category network = data.network;
    Technician arjun = TestData.tech("T2", 5, 3, TestData.skill("NETWORK", 4));
    Incident fixedVpn =
        resolved(
            vpn,
            "VPN disconnects every 30 minutes",
            "Remote staff are dropped from the VPN",
            network,
            Priority.P3,
            arjun,
            minutes(-500),
            60,
            "Increased VPN session timeout",
            5);
    Incident fixedWiki =
        resolved(
            wikiService,
            "Wiki search broken",
            "no search results",
            new Category("SOFTWARE", "Software"),
            Priority.P4,
            arjun,
            minutes(-400),
            30,
            "Rebuilt search index",
            0);
    Incident current =
        report(
            vpn,
            "VPN keeps disconnecting",
            "I am dropped from the VPN every half hour",
            minutes(0));
    current.classify(network, data.system, minutes(0));

    List<ResolutionSuggestion> s =
        new ResolutionSuggester(new CosineTextSimilarity())
            .suggest(current, IncidentHistory.of(List.of(fixedVpn, fixedWiki)));
    assertThat(s).isNotEmpty();
    assertThat(s.get(0).source()).isEqualTo(fixedVpn);
    assertThat(s.get(0).summary()).isEqualTo("Increased VPN session timeout");
    assertThat(s).extracting(ResolutionSuggestion::source).doesNotContain(fixedWiki);
  }

  @Test
  @DisplayName(
      "TC-107 SMART strategy prefers proven experience on the same kind of incident and explains"
          + " its score")
  void smartAssignment() {
    Technician veteran = TestData.tech("T10", 5, 4, TestData.skill("APPLICATION", 4));
    Technician newcomer = TestData.tech("T11", 5, 4, TestData.skill("APPLICATION", 4));
    List<Incident> past =
        List.of(
            resolved(
                data.payment,
                "Checkout errors",
                "payment errors",
                application,
                Priority.P2,
                veteran,
                minutes(-900),
                40,
                "Fixed config",
                5),
            resolved(
                data.payment,
                "Card declined wrongly",
                "payments declined",
                application,
                Priority.P2,
                veteran,
                minutes(-800),
                40,
                "Fixed rules",
                5));
    IncidentHistory history = IncidentHistory.of(past);
    // both technicians are idle again (the history incidents are resolved)
    Incident outage =
        report(data.payment, "Payment application unavailable", "503 for everyone", minutes(0));
    outage.classify(application, data.system, minutes(0));
    outage.applyPriority(Priority.P1, data.system, minutes(0));

    List<AssignmentCandidate> ranked =
        new SmartAssignmentStrategy(() -> history).rank(outage, List.of(newcomer, veteran));
    assertThat(ranked.get(0).technician()).isEqualTo(veteran);
    assertThat(ranked.get(0).score()).isGreaterThan(ranked.get(1).score());
    assertThat(ranked.get(0).reason())
        .contains("2 similar fixed (2 on PAYMENT)")
        .contains("rated 5.0");
  }
}
