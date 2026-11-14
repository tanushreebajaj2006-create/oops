package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Finds incidents that probably describe the same underlying problem (brief: "Duplicate Incident
 * Detection").
 *
 * <pre>
 *   score = 0.60 x text similarity          (title counts double)
 *         + 0.30 x same affected service
 *         + 0.10 x reported close in time   (1.0 if simultaneous, 0 after 24 h)
 * </pre>
 *
 * {@code >= 0.60} is a <b>likely duplicate</b>, {@code >= 0.35} is <b>related</b>. Only incidents
 * that are still open, or were resolved in the last 3 days, are compared - an outage fixed last
 * month is not a duplicate of today's.
 *
 * <p>Rationale: wording alone is unreliable ("payment failing" vs "can't pay"), so the affected
 * service and timing add strong, cheap evidence; text still dominates so that two unrelated
 * problems on a busy service are not merged.
 */
public final class DuplicateDetector {

  public static final double DUPLICATE_THRESHOLD = 0.60;
  public static final double RELATED_THRESHOLD = 0.35;
  static final Duration RECENTLY_RESOLVED = Duration.ofDays(3);
  static final Duration TIME_WINDOW = Duration.ofHours(24);
  private static final int MAX_RESULTS = 5;

  private final TextSimilarity similarity;

  public DuplicateDetector(TextSimilarity similarity) {
    this.similarity = Require.notNull(similarity, "Text similarity");
  }

  public List<RelatedIncident> findRelated(
      IncidentFingerprint target, Collection<Incident> candidates, Instant now) {
    Require.notNull(target, "Target");
    TextProfile profile = target.profile();
    List<RelatedIncident> out = new ArrayList<>();
    for (Incident c : candidates) {
      if (c.id().equals(target.id()) || !isComparable(c, now)) {
        continue;
      }
      TextProfile other = TextProfile.of(c.title(), c.description());
      double text = similarity.similarity(profile, other);
      boolean sameService = c.service().code().equalsIgnoreCase(target.serviceCode());
      long minutesApart =
          Math.abs(Duration.between(c.createdAt(), target.reportedAt()).toMinutes());
      double time = Math.max(0, 1.0 - (double) minutesApart / TIME_WINDOW.toMinutes());
      double score = 0.60 * text + (sameService ? 0.30 : 0) + 0.10 * time;
      if (score < RELATED_THRESHOLD) {
        continue;
      }
      List<String> reasons = new ArrayList<>();
      reasons.add("text %d%% similar".formatted(Math.round(text * 100)));
      Set<String> shared = profile.sharedTerms(other);
      if (!shared.isEmpty()) {
        reasons.add("shared terms: " + String.join(", ", shared.stream().limit(6).toList()));
      }
      if (sameService) {
        reasons.add("same service " + c.service().code());
      }
      if (time > 0) {
        reasons.add("reported " + describe(minutesApart) + " apart");
      }
      out.add(
          new RelatedIncident(
              c,
              Math.round(score * 100) / 100.0,
              score >= DUPLICATE_THRESHOLD
                  ? RelatedIncident.Verdict.LIKELY_DUPLICATE
                  : RelatedIncident.Verdict.RELATED,
              reasons));
    }
    out.sort(Comparator.comparingDouble(RelatedIncident::score).reversed());
    return out.subList(0, Math.min(MAX_RESULTS, out.size()));
  }

  private static boolean isComparable(Incident c, Instant now) {
    if (c.status() == IncidentStatus.CANCELLED) {
      return false;
    }
    if (c.isOpen()) {
      return true;
    }
    return c.resolvedAt().map(r -> r.isAfter(now.minus(RECENTLY_RESOLVED))).orElse(false);
  }

  private static String describe(long minutes) {
    if (minutes < 1) {
      return "less than a minute";
    }
    if (minutes < 120) {
      return minutes + " min";
    }
    return (minutes / 60) + " h";
  }
}
