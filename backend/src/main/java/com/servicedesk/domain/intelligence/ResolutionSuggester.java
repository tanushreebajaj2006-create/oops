package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.Resolution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Suggests fixes from similar resolved incidents (brief: "Suggested Resolution").
 *
 * <pre>
 *   score = 0.70 x text similarity + 0.20 x same category + 0.10 x same service
 * </pre>
 *
 * Suggestions scoring at least {@value #MIN_SCORE} are returned, best first, at most three; among
 * equal scores, fixes the reporter rated higher come first. Reopened-and-refixed incidents only
 * count with their final resolution, because the domain clears a resolution on reopen.
 */
public final class ResolutionSuggester {

  static final double MIN_SCORE = 0.30;

  private final TextSimilarity similarity;

  public ResolutionSuggester(TextSimilarity similarity) {
    this.similarity = Require.notNull(similarity, "Text similarity");
  }

  public List<ResolutionSuggestion> suggest(Incident target, IncidentHistory history) {
    Require.notNull(target, "Incident");
    TextProfile profile = TextProfile.of(target.title(), target.description());
    String category = target.category().map(c -> c.code()).orElse(null);
    List<ResolutionSuggestion> out = new ArrayList<>();
    for (Incident past : history.resolvedIncidents()) {
      Optional<Resolution> resolution = past.resolution();
      if (past.equals(target) || resolution.isEmpty()) {
        continue;
      }
      double score =
          0.70 * similarity.similarity(profile, TextProfile.of(past.title(), past.description()))
              + (category != null
                      && past.category().map(c -> c.code()).filter(category::equals).isPresent()
                  ? 0.20
                  : 0)
              + (past.service().code().equals(target.service().code()) ? 0.10 : 0);
      if (score >= MIN_SCORE) {
        out.add(
            new ResolutionSuggestion(
                past,
                Math.round(score * 100) / 100.0,
                resolution.get().summary(),
                resolution.get().rootCause()));
      }
    }
    out.sort(
        Comparator.comparingDouble(ResolutionSuggestion::score)
            .reversed()
            .thenComparing(s -> -s.source().feedback().map(f -> f.rating()).orElse(0)));
    return out.subList(0, Math.min(3, out.size()));
  }
}
