package com.servicedesk.api;

import com.servicedesk.api.dto.Requests;
import com.servicedesk.api.dto.Views;
import com.servicedesk.application.IntelligenceService;
import com.servicedesk.application.SlaService;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.intelligence.CategoryClassifier;
import com.servicedesk.domain.intelligence.CategorySuggestion;
import com.servicedesk.domain.intelligence.RelatedIncident;
import com.servicedesk.domain.sla.SlaCalculator;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intelligent features: live category suggestions and duplicate warnings while reporting, related
 * incidents and suggested fixes on an incident, and SLA-risk / correlation dashboards.
 */
@RestController
public class IntelligenceController {

  private final IntelligenceService intelligence;
  private final SlaService sla;

  public IntelligenceController(IntelligenceService intelligence, SlaService sla) {
    this.intelligence = intelligence;
    this.sla = sla;
  }

  @PostMapping("/api/v1/intelligence/category-suggestions")
  public List<Views.CategorySuggestionView> suggestCategories(
      @Valid @RequestBody Requests.SuggestCategory body) {
    List<CategorySuggestion> s = intelligence.suggestCategories(body.title(), body.description());
    boolean confident = CategoryClassifier.isConfident(s);
    return s.stream()
        .map(
            c ->
                new Views.CategorySuggestionView(
                    c.categoryCode(),
                    c.categoryName(),
                    (int) Math.round(c.confidence() * 100),
                    c.matchedTerms(),
                    confident && c == s.get(0)))
        .toList();
  }

  @PostMapping("/api/v1/intelligence/duplicate-check")
  public List<Views.RelatedIncidentView> duplicateCheck(
      @Valid @RequestBody Requests.DuplicateCheck body, Authentication auth) {
    return intelligence
        .duplicateCheck(CurrentUser.id(auth), body.title(), body.description(), body.serviceCode())
        .stream()
        .map(IntelligenceController::related)
        .toList();
  }

  @GetMapping("/api/v1/incidents/{id}/related")
  public List<Views.RelatedIncidentView> related(@PathVariable String id, Authentication auth) {
    return intelligence.related(CurrentUser.id(auth), new IncidentId(id)).stream()
        .map(IntelligenceController::related)
        .toList();
  }

  @GetMapping("/api/v1/incidents/{id}/resolution-suggestions")
  public List<Views.ResolutionSuggestionView> resolutionSuggestions(
      @PathVariable String id, Authentication auth) {
    return intelligence.resolutionSuggestions(CurrentUser.id(auth), new IncidentId(id)).stream()
        .map(
            s ->
                new Views.ResolutionSuggestionView(
                    s.source().id().value(),
                    s.source().title(),
                    s.summary(),
                    s.rootCause(),
                    s.source().feedback().map(f -> f.rating()).orElse(null),
                    s.source().resolvedAt().orElse(null),
                    (int) Math.round(s.score() * 100)))
        .toList();
  }

  @GetMapping("/api/v1/reports/sla-risk")
  public List<Views.SlaRiskView> slaRisk(Authentication auth) {
    SlaCalculator calculator = sla.calculator();
    return intelligence.slaRisks(CurrentUser.id(auth)).stream()
        .map(
            r ->
                new Views.SlaRiskView(
                    ApiMapper.summary(r.incident(), calculator),
                    r.level().name(),
                    (int) Math.round(r.projectedRatio() * 100),
                    r.projectedResolution(),
                    r.dueAt(),
                    r.reasons()))
        .toList();
  }

  @GetMapping("/api/v1/reports/correlations")
  public List<Views.ClusterView> correlations(Authentication auth) {
    SlaCalculator calculator = sla.calculator();
    return intelligence.correlations(CurrentUser.id(auth)).stream()
        .map(
            c ->
                new Views.ClusterView(
                    c.pattern().name(),
                    c.key(),
                    c.summary(),
                    c.highestPriority(),
                    c.incidents().size(),
                    c.firstReportedAt(),
                    c.lastReportedAt(),
                    c.commonTerms(),
                    c.incidents().stream().map(i -> ApiMapper.summary(i, calculator)).toList()))
        .toList();
  }

  private static Views.RelatedIncidentView related(RelatedIncident r) {
    Incident i = r.incident();
    return new Views.RelatedIncidentView(
        i.id().value(),
        i.title(),
        i.status(),
        i.priority().orElse(null),
        i.service().code(),
        i.createdAt(),
        (int) Math.round(r.score() * 100),
        r.verdict().name(),
        r.reasons());
  }
}
