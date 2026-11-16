package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import java.util.List;

/**
 * One suggested category for a piece of text.
 *
 * @param categoryCode the category
 * @param categoryName its display name
 * @param confidence probability-like score between 0 and 1 (suggestions for one text sum to 1)
 * @param matchedTerms the words that pointed to this category - shown to the user as "because"
 */
public record CategorySuggestion(
    String categoryCode, String categoryName, double confidence, List<String> matchedTerms) {

  public CategorySuggestion {
    Require.notBlank(categoryCode, "Category code");
    matchedTerms = List.copyOf(matchedTerms);
  }
}
