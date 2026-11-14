package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A group of open incidents that probably share one cause.
 *
 * @param pattern what kind of group it is
 * @param key the shared service code or category code
 * @param incidents the incidents, oldest first
 * @param highestPriority most urgent priority in the group (null if none prioritised yet)
 * @param commonTerms words that appear in most of the reports
 * @param summary one human-readable sentence for the dashboard
 */
public record IncidentCluster(
    Pattern pattern,
    String key,
    List<Incident> incidents,
    Priority highestPriority,
    Set<String> commonTerms,
    String summary) {

  /** How incidents are connected. */
  public enum Pattern {
    /**
     * Several reports against one service in a short time: probably one outage (major incident).
     */
    SERVICE_OUTAGE,
    /** Same category across different services: probably a shared component (e.g. SSO, network). */
    SHARED_COMPONENT
  }

  public IncidentCluster {
    Require.notNull(pattern, "Pattern");
    incidents = List.copyOf(incidents);
    commonTerms = Set.copyOf(commonTerms);
  }

  public Instant firstReportedAt() {
    return incidents.get(0).createdAt();
  }

  public Instant lastReportedAt() {
    return incidents.get(incidents.size() - 1).createdAt();
  }
}
