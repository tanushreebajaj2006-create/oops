package com.servicedesk.domain.intelligence;

import java.util.List;

/**
 * Suggests incident categories from the reporter's own words (brief: "Automatically suggest an
 * incident category based on the incident description").
 *
 * <p>The built-in {@link NaiveBayesCategoryClassifier} needs no external service. Because callers
 * depend only on this interface, an LLM- or ML-service-backed classifier could be plugged in
 * instead.
 */
@FunctionalInterface
public interface CategoryClassifier {

  /** Best suggestions first; empty if the text gives no usable clue. */
  List<CategorySuggestion> suggest(String title, String description);

  /** Whether the top suggestion is clear enough to apply without asking a person. */
  static boolean isConfident(List<CategorySuggestion> suggestions) {
    if (suggestions.isEmpty()) {
      return false;
    }
    double top = suggestions.get(0).confidence();
    double second = suggestions.size() > 1 ? suggestions.get(1).confidence() : 0;
    return top >= 0.60 && top - second >= 0.25;
  }
}
