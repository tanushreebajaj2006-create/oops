/**
 * Intelligent / automated features - pure, explainable algorithms with no external services.
 *
 * <ul>
 *   <li>{@link com.servicedesk.domain.intelligence.NaiveBayesCategoryClassifier} - suggests a
 *       category from the reporter's words (seed keywords + learning from resolved incidents).
 *   <li>{@link com.servicedesk.domain.intelligence.DuplicateDetector} - likely duplicate / related
 *       incidents (text similarity, same service, time proximity).
 *   <li>{@link com.servicedesk.domain.intelligence.IncidentCorrelator} - bursts of incidents on one
 *       service or category: possible major incidents.
 *   <li>{@link com.servicedesk.domain.intelligence.SlaRiskPredictor} - predicts SLA breaches from
 *       historical fix times, workload and incident state.
 *   <li>{@link com.servicedesk.domain.intelligence.ResolutionSuggester} - fixes from similar past
 *       incidents.
 *   <li>{@link com.servicedesk.domain.intelligence.IncidentHistory} - what past incidents teach,
 *       also used by the {@code SMART} assignment strategy.
 * </ul>
 *
 * Text similarity and category classification sit behind interfaces ({@link
 * com.servicedesk.domain.intelligence.TextSimilarity}, {@link
 * com.servicedesk.domain.intelligence.CategoryClassifier}) so an AI/ML service can replace them.
 */
package com.servicedesk.domain.intelligence;
