package com.servicedesk.domain.intelligence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns free text into normalised <i>terms</i> for the intelligent features.
 *
 * <p>Pipeline: lower-case -> join common compounds ("wi-fi" -> "wifi", "log in" -> "login") ->
 * split on anything that is not a letter or digit -> drop stop words and 1-letter tokens -> light
 * stemming ("failing" -> "fail", "queries" -> "query") -> map IT synonyms to one concept
 * ("unavailable", "down", "unable", "outage" -> "fail").
 *
 * <p>The synonym step is what lets the brief's example match: "Payment application
 * <b>unavailable</b>", "Payment transaction <b>failing</b>" and "<b>Unable</b> to make payment" all
 * share the terms {@code payment} and {@code fail}.
 */
public final class TextAnalyzer {

  private TextAnalyzer() {}

  private static final Set<String> STOP_WORDS =
      Set.of(
          "a",
          "an",
          "the",
          "and",
          "or",
          "but",
          "if",
          "of",
          "to",
          "in",
          "on",
          "at",
          "for",
          "from",
          "by",
          "with",
          "is",
          "are",
          "was",
          "were",
          "be",
          "been",
          "being",
          "it",
          "its",
          "this",
          "that",
          "these",
          "those",
          "i",
          "me",
          "my",
          "we",
          "our",
          "you",
          "your",
          "he",
          "she",
          "they",
          "them",
          "their",
          "there",
          "here",
          "has",
          "have",
          "had",
          "do",
          "does",
          "did",
          "not",
          "no",
          "so",
          "as",
          "since",
          "after",
          "before",
          "when",
          "while",
          "all",
          "any",
          "some",
          "every",
          "can",
          "could",
          "will",
          "would",
          "should",
          "please",
          "help",
          "again",
          "still",
          "just",
          "also",
          "very",
          "too",
          "about",
          "into",
          "than",
          "then",
          "now",
          "today",
          "yesterday",
          "morning",
          "afternoon",
          "make",
          "get",
          "got",
          "keep",
          "keeps",
          "shows",
          "show",
          "showing",
          "users",
          "user",
          "people",
          "everyone",
          "nobody",
          "issue",
          "problem",
          "only");

  /** Concept map applied after stemming: many words, one idea. */
  private static final Map<String, String> SYNONYMS =
      Map.ofEntries(
          // something does not work
          Map.entry("unavailable", "fail"),
          Map.entry("unable", "fail"),
          Map.entry("down", "fail"),
          Map.entry("outage", "fail"),
          Map.entry("broken", "fail"),
          Map.entry("failure", "fail"),
          Map.entry("crash", "fail"),
          Map.entry("cannot", "fail"),
          Map.entry("offline", "fail"),
          Map.entry("timeout", "fail"),
          Map.entry("tim", "fail"), // "timing out" after stemming
          // slowness
          Map.entry("slowness", "slow"),
          Map.entry("lag", "slow"),
          Map.entry("latency", "slow"),
          Map.entry("delay", "slow"),
          Map.entry("sluggish", "slow"),
          // payments
          Map.entry("pay", "payment"),
          Map.entry("checkout", "payment"),
          Map.entry("card", "payment"),
          // sign-in
          Map.entry("signin", "login"),
          Map.entry("logon", "login"),
          Map.entry("sso", "login"),
          Map.entry("authentication", "login"),
          Map.entry("authenticate", "login"),
          Map.entry("password", "login"),
          Map.entry("credential", "login"),
          // mail
          Map.entry("mail", "email"),
          Map.entry("mailbox", "email"),
          Map.entry("outlook", "email"),
          Map.entry("inbox", "email"),
          // data
          Map.entry("db", "database"),
          Map.entry("sql", "database"),
          Map.entry("replica", "database"),
          Map.entry("replication", "database"),
          // network
          Map.entry("wireless", "wifi"),
          Map.entry("internet", "network"),
          Map.entry("connectivity", "network"),
          Map.entry("connect", "network"),
          Map.entry("connection", "network"),
          Map.entry("disconnect", "network"),
          // printing
          Map.entry("printer", "print"),
          Map.entry("printing", "print"));

  /** Normalised terms in reading order (duplicates kept, so frequency can be counted). */
  public static List<String> terms(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    String t =
        text.toLowerCase(Locale.ROOT)
            .replace("wi-fi", "wifi")
            .replace("e-mail", "email")
            .replace("log in", "login")
            .replace("sign in", "signin")
            .replace("can't", "cannot")
            .replace("not working", "fail")
            .replace("doesn't work", "fail");
    List<String> out = new ArrayList<>();
    for (String raw : t.split("[^a-z0-9]+")) {
      if (raw.length() < 2 || STOP_WORDS.contains(raw)) {
        continue;
      }
      String stem = stem(raw);
      String term = SYNONYMS.getOrDefault(stem, SYNONYMS.getOrDefault(raw, stem));
      if (!STOP_WORDS.contains(term)) {
        out.add(term);
      }
    }
    return out;
  }

  /**
   * Very small suffix stripper - enough to merge "fail / fails / failed / failing" and "query /
   * queries" without a full linguistic stemmer. Words of 4 letters or fewer are left alone.
   */
  static String stem(String w) {
    if (w.length() <= 4 || Character.isDigit(w.charAt(0))) {
      return w;
    }
    if (w.endsWith("ies") && w.length() > 5) {
      return w.substring(0, w.length() - 3) + "y";
    }
    if (w.endsWith("ing") && w.length() > 5) {
      return undouble(w.substring(0, w.length() - 3));
    }
    if (w.endsWith("ed") && w.length() > 4) {
      return undouble(w.substring(0, w.length() - 2));
    }
    if (w.endsWith("es") && (w.endsWith("sses") || w.endsWith("ches") || w.endsWith("shes"))) {
      return w.substring(0, w.length() - 2);
    }
    if (w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) {
      return w.substring(0, w.length() - 1);
    }
    return w;
  }

  /** "stopp" -> "stop", "jamm" -> "jam" after removing -ed / -ing. */
  private static String undouble(String w) {
    int n = w.length();
    if (n >= 3 && w.charAt(n - 1) == w.charAt(n - 2) && "lsz".indexOf(w.charAt(n - 1)) < 0) {
      return w.substring(0, n - 1);
    }
    return w;
  }
}
