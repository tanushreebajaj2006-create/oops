package com.servicedesk.domain.intelligence;

import java.util.Map;

/**
 * Cosine similarity of two term-frequency vectors: {@code (a . b) / (|a| x |b|)}.
 *
 * <p>Insensitive to text length (a one-line and a ten-line description of the same outage still
 * score high) and cheap enough to compare a new report against hundreds of incidents instantly.
 */
public final class CosineTextSimilarity implements TextSimilarity {

  @Override
  public double similarity(TextProfile a, TextProfile b) {
    if (a.isEmpty() || b.isEmpty()) {
      return 0.0;
    }
    Map<String, Double> small =
        a.weights().size() <= b.weights().size() ? a.weights() : b.weights();
    Map<String, Double> large = small == a.weights() ? b.weights() : a.weights();
    double dot = 0;
    for (Map.Entry<String, Double> e : small.entrySet()) {
      Double other = large.get(e.getKey());
      if (other != null) {
        dot += e.getValue() * other;
      }
    }
    return dot == 0 ? 0.0 : dot / (norm(a.weights()) * norm(b.weights()));
  }

  private static double norm(Map<String, Double> v) {
    double sum = 0;
    for (double x : v.values()) {
      sum += x * x;
    }
    return Math.sqrt(sum);
  }
}
