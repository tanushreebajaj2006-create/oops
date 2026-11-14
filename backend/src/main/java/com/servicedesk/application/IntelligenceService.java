package com.servicedesk.application;

import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.intelligence.CategoryClassifier;
import com.servicedesk.domain.intelligence.CategorySuggestion;
import com.servicedesk.domain.intelligence.DuplicateDetector;
import com.servicedesk.domain.intelligence.IncidentCluster;
import com.servicedesk.domain.intelligence.IncidentCorrelator;
import com.servicedesk.domain.intelligence.IncidentFingerprint;
import com.servicedesk.domain.intelligence.IncidentHistory;
import com.servicedesk.domain.intelligence.NaiveBayesCategoryClassifier;
import com.servicedesk.domain.intelligence.RelatedIncident;
import com.servicedesk.domain.intelligence.ResolutionSuggester;
import com.servicedesk.domain.intelligence.ResolutionSuggestion;
import com.servicedesk.domain.intelligence.SlaRisk;
import com.servicedesk.domain.intelligence.SlaRiskPredictor;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The intelligent / automated features, each a thin use case around a pure domain algorithm:
 *
 * <ul>
 *   <li>category suggestion (Naive Bayes, trained on resolved incidents);
 *   <li>duplicate and related incident detection;
 *   <li>incident correlation (possible major incidents);
 *   <li>resolution suggestions from similar past fixes;
 *   <li>SLA risk prediction.
 * </ul>
 *
 * (Smart technician recommendation is the {@code SMART} assignment strategy; automatic escalation
 * lives in {@link SlaMonitoringService}.)
 */
@Service
@Transactional(readOnly = true)
public class IntelligenceService {

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final CategoryRepository categories;
  private final IncidentHistoryProvider history;
  private final DuplicateDetector duplicates;
  private final ResolutionSuggester resolutions;
  private final IncidentCorrelator correlator;
  private final SlaRiskPredictor riskPredictor;
  private final SlaService sla;
  private final Clock clock;

  public IntelligenceService(
      IncidentRepository incidents,
      UserRepository users,
      CategoryRepository categories,
      IncidentHistoryProvider history,
      DuplicateDetector duplicates,
      ResolutionSuggester resolutions,
      IncidentCorrelator correlator,
      SlaRiskPredictor riskPredictor,
      SlaService sla,
      Clock clock) {
    this.incidents = incidents;
    this.users = users;
    this.categories = categories;
    this.history = history;
    this.duplicates = duplicates;
    this.resolutions = resolutions;
    this.correlator = correlator;
    this.riskPredictor = riskPredictor;
    this.sla = sla;
    this.clock = clock;
  }

  // ---- category suggestion -------------------------------------------------------------

  /**
   * A classifier trained on keyword seeds plus every resolved incident (where the category has been
   * confirmed by the work actually done). Built per call from the cached history - cheap, and
   * always reflects new categories.
   */
  public CategoryClassifier classifier() {
    NaiveBayesCategoryClassifier.Builder b =
        NaiveBayesCategoryClassifier.builder(categories.findAll())
            .withKeywords(NaiveBayesCategoryClassifier.defaultKeywords());
    for (Incident i : history.get().resolvedIncidents()) {
      i.category().ifPresent(c -> b.train(i.title(), i.description(), c.code()));
    }
    return b.build();
  }

  public List<CategorySuggestion> suggestCategories(String title, String description) {
    return classifier().suggest(title, description);
  }

  // ---- duplicates & correlation --------------------------------------------------------

  /** "Has this already been reported?" - checked while the user is still filling in the form. */
  public List<RelatedIncident> duplicateCheck(
      UserId viewerId, String title, String description, String serviceCode) {
    activeUser(viewerId);
    Instant now = clock.instant();
    return duplicates.findRelated(
        IncidentFingerprint.draft(title, description, serviceCode, now), candidates(now), now);
  }

  /** Incidents that may be the same problem as this one. */
  public List<RelatedIncident> related(UserId viewerId, IncidentId id) {
    Incident incident = visible(viewerId, id);
    Instant now = clock.instant();
    return duplicates.findRelated(IncidentFingerprint.of(incident), candidates(now), now);
  }

  /** Groups of open incidents that probably share a cause (supervisors). */
  public List<IncidentCluster> correlations(UserId viewerId) {
    activeUser(viewerId).requirePermission(Permission.VIEW_REPORTS);
    return correlator.correlate(incidents.findOpen());
  }

  // ---- resolution suggestions ----------------------------------------------------------

  /** Fixes from similar past incidents, for the people working on this one. */
  public List<ResolutionSuggestion> resolutionSuggestions(UserId viewerId, IncidentId id) {
    User viewer = activeUser(viewerId);
    if (!viewer.can(Permission.WORK_ON_INCIDENTS) && !viewer.can(Permission.ASSIGN_INCIDENTS)) {
      throw new UnauthorizedOperationException("Resolution suggestions are for support staff");
    }
    return resolutions.suggest(visible(viewerId, id), history.get());
  }

  // ---- SLA risk prediction -------------------------------------------------------------

  /** Open incidents predicted to be at MEDIUM or HIGH risk of missing their deadline. */
  public List<SlaRisk> slaRisks(UserId viewerId) {
    activeUser(viewerId).requirePermission(Permission.VIEW_REPORTS);
    SlaCalculator calculator = sla.calculator();
    IncidentHistory h = history.get();
    Instant now = clock.instant();
    List<SlaRisk> out = new ArrayList<>();
    for (Incident i : incidents.findOpen()) {
      SlaService.snapshot(i, calculator)
          .flatMap(s -> riskPredictor.assess(i, s, h, now))
          .filter(r -> r.level() != SlaRisk.Level.LOW)
          .ifPresent(out::add);
    }
    out.sort(
        Comparator.comparing(SlaRisk::level)
            .reversed()
            .thenComparing(Comparator.comparingDouble(SlaRisk::projectedRatio).reversed()));
    return out;
  }

  /** Prediction for one incident (shown on its detail page). */
  public Optional<SlaRisk> slaRisk(Incident incident) {
    return sla.snapshot(incident)
        .flatMap(s -> riskPredictor.assess(incident, s, history.get(), clock.instant()));
  }

  // ---- helpers -------------------------------------------------------------------------

  /** Open incidents plus those resolved in the last week (the detector narrows to 3 days). */
  private List<Incident> candidates(Instant now) {
    Map<IncidentId, Incident> all = new LinkedHashMap<>();
    incidents.findOpen().forEach(i -> all.put(i.id(), i));
    incidents
        .search(
            IncidentQuery.all()
                .withStatuses(ReportService.RESOLVED)
                .createdBetween(now.minus(Duration.ofDays(7)), now.plusSeconds(1))
                .page(IncidentQuery.MAX_LIMIT, 0))
        .forEach(i -> all.putIfAbsent(i.id(), i));
    return List.copyOf(all.values());
  }

  private Incident visible(UserId viewerId, IncidentId id) {
    User viewer = activeUser(viewerId);
    Incident incident = incidents.get(id);
    if (!incident.isVisibleTo(viewer)) {
      throw new UnauthorizedOperationException(viewer.id() + " cannot view " + id);
    }
    return incident;
  }

  private User activeUser(UserId id) {
    User user = users.get(id);
    if (!user.isActive()) {
      throw new UnauthorizedOperationException("Account " + id + " is deactivated");
    }
    return user;
  }
}
