package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Category;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multinomial <b>Naive Bayes</b> text classifier - a classic, explainable machine-learning method.
 *
 * <p>For each category it learns how often each term appears; a new text is scored per category by
 * {@code sum(weight(term) x log P(term | category))} with Laplace smoothing, and the scores are
 * turned into probabilities with softmax. Categories start equally likely (uniform prior), so a
 * category that happens to have many past incidents is not favoured for that reason alone.
 *
 * <p>Knowledge comes from two places:
 *
 * <ul>
 *   <li><b>seed keywords</b> ({@link #defaultKeywords()}) so it works on day one, each keyword
 *       counting as {@value #KEYWORD_WEIGHT} pseudo-observations;
 *   <li><b>history</b>: every incident already classified (by people or the system) is a training
 *       example, so it adapts to the organisation's vocabulary and to new categories that
 *       administrators add.
 * </ul>
 *
 * Immutable once built; build a fresh one when the history changes.
 */
public final class NaiveBayesCategoryClassifier implements CategoryClassifier {

  static final double KEYWORD_WEIGHT = 3.0;
  private static final double ALPHA = 1.0; // Laplace smoothing

  private final Map<String, String> names; // code -> display name, active categories only
  private final Map<String, Map<String, Double>> counts; // code -> term -> count
  private final Map<String, Double> totals; // code -> total count
  private final Set<String> vocabulary;

  private NaiveBayesCategoryClassifier(
      Map<String, String> names,
      Map<String, Map<String, Double>> counts,
      Map<String, Double> totals,
      Set<String> vocabulary) {
    this.names = names;
    this.counts = counts;
    this.totals = totals;
    this.vocabulary = vocabulary;
  }

  public static Builder builder(Collection<Category> categories) {
    return new Builder(categories);
  }

  /** Builder: add keywords and training examples, then {@link #build()}. */
  public static final class Builder {
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, Map<String, Double>> counts = new HashMap<>();

    private Builder(Collection<Category> categories) {
      Require.notNull(categories, "Categories");
      for (Category c : categories) {
        if (c.active()) {
          names.put(c.code(), c.name());
          counts.put(c.code(), new HashMap<>());
        }
      }
    }

    /** Seed knowledge; keywords for unknown or retired categories are ignored. */
    public Builder withKeywords(Map<String, List<String>> keywordsByCategory) {
      keywordsByCategory.forEach(
          (code, words) -> {
            Map<String, Double> c = counts.get(code);
            if (c != null) {
              for (String w : words) {
                for (String term : TextAnalyzer.terms(w)) {
                  c.merge(term, KEYWORD_WEIGHT, Double::sum);
                }
              }
            }
          });
      return this;
    }

    /** One labelled example (e.g. a past incident and the category it ended up in). */
    public Builder train(String title, String description, String categoryCode) {
      Map<String, Double> c = counts.get(categoryCode);
      if (c != null) {
        TextProfile.of(title, description).weights().forEach((t, w) -> c.merge(t, w, Double::sum));
      }
      return this;
    }

    public NaiveBayesCategoryClassifier build() {
      Map<String, Double> totals = new HashMap<>();
      Set<String> vocabulary = new HashSet<>();
      counts.forEach(
          (code, terms) -> {
            totals.put(code, terms.values().stream().mapToDouble(Double::doubleValue).sum());
            vocabulary.addAll(terms.keySet());
          });
      return new NaiveBayesCategoryClassifier(names, counts, totals, vocabulary);
    }
  }

  @Override
  public List<CategorySuggestion> suggest(String title, String description) {
    if (names.isEmpty()) {
      return List.of();
    }
    Map<String, Double> doc = new HashMap<>(TextProfile.of(title, description).weights());
    doc.keySet().retainAll(vocabulary); // words never seen in any category carry no evidence
    if (doc.isEmpty()) {
      return List.of();
    }
    double v = vocabulary.size();
    Map<String, Double> logScore = new LinkedHashMap<>();
    for (String code : names.keySet()) {
      Map<String, Double> c = counts.get(code);
      double denominator = totals.get(code) + ALPHA * v;
      double s = 0;
      for (Map.Entry<String, Double> e : doc.entrySet()) {
        s += e.getValue() * Math.log((c.getOrDefault(e.getKey(), 0.0) + ALPHA) / denominator);
      }
      logScore.put(code, s);
    }
    // softmax (subtract the max first for numerical stability)
    double max = logScore.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    double sum = logScore.values().stream().mapToDouble(s -> Math.exp(s - max)).sum();
    List<CategorySuggestion> out = new ArrayList<>();
    for (Map.Entry<String, Double> e : logScore.entrySet()) {
      double p = Math.exp(e.getValue() - max) / sum;
      out.add(
          new CategorySuggestion(e.getKey(), names.get(e.getKey()), p, evidence(e.getKey(), doc)));
    }
    out.sort(Comparator.comparingDouble(CategorySuggestion::confidence).reversed());
    return out.subList(0, Math.min(3, out.size()));
  }

  /** Terms of the text this category has seen, strongest first. */
  private List<String> evidence(String code, Map<String, Double> doc) {
    Map<String, Double> c = counts.get(code);
    return doc.keySet().stream()
        .filter(c::containsKey)
        .sorted(Comparator.comparingDouble((String t) -> c.get(t)).reversed().thenComparing(t -> t))
        .limit(5)
        .toList();
  }

  /** Starter vocabulary for the nine categories in the brief. */
  public static Map<String, List<String>> defaultKeywords() {
    return Map.of(
        "HARDWARE",
        List.of(
            "laptop",
            "desktop",
            "monitor",
            "keyboard",
            "mouse",
            "printer",
            "scanner",
            "hardware",
            "device",
            "battery",
            "screen",
            "charger",
            "jam",
            "toner",
            "label",
            "docking",
            "headset",
            "projector"),
        "SOFTWARE",
        List.of(
            "software",
            "install",
            "update",
            "upgrade",
            "license",
            "excel",
            "word",
            "office",
            "crash",
            "bug",
            "version",
            "wiki",
            "search",
            "sync",
            "calendar",
            "email",
            "teams",
            "client"),
        "NETWORK",
        List.of(
            "network",
            "wifi",
            "vpn",
            "router",
            "switch",
            "firewall",
            "dns",
            "ip",
            "bandwidth",
            "slow",
            "proxy",
            "cable",
            "ethernet",
            "packet",
            "access point",
            "disconnect"),
        "DATABASE",
        List.of(
            "database",
            "query",
            "table",
            "index",
            "deadlock",
            "backup",
            "report",
            "schema",
            "oracle",
            "postgres",
            "mysql",
            "record",
            "replication"),
        "CLOUD",
        List.of(
            "cloud",
            "aws",
            "azure",
            "gcp",
            "kubernetes",
            "container",
            "pod",
            "vm",
            "instance",
            "serverless",
            "cluster",
            "region",
            "deployment"),
        "SECURITY",
        List.of(
            "security",
            "virus",
            "malware",
            "phishing",
            "suspicious",
            "breach",
            "attack",
            "ransomware",
            "unauthorized",
            "spam",
            "hack",
            "vulnerability",
            "compromised",
            "intrusion"),
        "AUTHENTICATION",
        List.of(
            "login",
            "mfa",
            "otp",
            "account",
            "locked",
            "lockout",
            "token",
            "session",
            "permission",
            "access denied",
            "sso",
            "2fa",
            "password reset"),
        "APPLICATION",
        List.of(
            "application",
            "app",
            "payment",
            "portal",
            "erp",
            "crm",
            "website",
            "page",
            "button",
            "form",
            "transaction",
            "api",
            "payslip",
            "order",
            "approval",
            "checkout"),
        "STORAGE",
        List.of(
            "storage",
            "disk",
            "drive",
            "quota",
            "file",
            "folder",
            "share",
            "space",
            "upload",
            "sharepoint",
            "onedrive",
            "nas"));
  }
}
