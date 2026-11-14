package com.servicedesk.domain.intelligence;

/**
 * How alike two incident texts are, from 0 (nothing in common) to 1 (same content).
 *
 * <p>An interface on purpose: the built-in {@link CosineTextSimilarity} is a classic, explainable
 * information-retrieval measure; an AI-based implementation (e.g. sentence embeddings from an
 * external model) could replace it without touching duplicate detection or resolution suggestion.
 */
@FunctionalInterface
public interface TextSimilarity {

  double similarity(TextProfile a, TextProfile b);
}
