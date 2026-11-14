package com.servicedesk.domain.intelligence;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Term-frequency vector of an incident's text. The title counts double: people put the essence of
 * the problem in the title, and descriptions vary much more in wording and length.
 */
public final class TextProfile {

  public static final double TITLE_WEIGHT = 2.0;

  private final Map<String, Double> weights;

  private TextProfile(Map<String, Double> weights) {
    this.weights = Collections.unmodifiableMap(weights);
  }

  public static TextProfile of(String title, String description) {
    Map<String, Double> w = new HashMap<>();
    add(w, TextAnalyzer.terms(title), TITLE_WEIGHT);
    add(w, TextAnalyzer.terms(description), 1.0);
    return new TextProfile(w);
  }

  private static void add(Map<String, Double> w, List<String> terms, double weight) {
    for (String t : terms) {
      w.merge(t, weight, Double::sum);
    }
  }

  public Map<String, Double> weights() {
    return weights;
  }

  public boolean isEmpty() {
    return weights.isEmpty();
  }

  /** Terms both profiles contain, alphabetically - used to explain why two texts match. */
  public Set<String> sharedTerms(TextProfile other) {
    Set<String> shared = new TreeSet<>(weights.keySet());
    shared.retainAll(other.weights.keySet());
    return shared;
  }
}
