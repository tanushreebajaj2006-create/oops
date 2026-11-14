package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.SlaConfigurationMissingException;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.priority.Priority;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the SLA policy that applies to a service: the service's own policy if one exists, otherwise
 * the organisation-wide default.
 *
 * <p>Service policies are kept in a {@link HashMap} keyed by service code for O(1) lookup, because
 * this is called for every open incident on every SLA scan.
 */
public class SlaPolicyCatalog {

  private final Map<String, SlaPolicy> byService = new HashMap<>();
  private SlaPolicy defaultPolicy;

  public SlaPolicyCatalog() {}

  public SlaPolicyCatalog(SlaPolicy defaultPolicy) {
    register(defaultPolicy);
  }

  /** Catalog containing only the brief's standard table as the default. */
  public static SlaPolicyCatalog standard() {
    return new SlaPolicyCatalog(SlaPolicy.standard());
  }

  /** Adds or replaces a policy (the default one, or the one for its service). */
  public synchronized SlaPolicyCatalog register(SlaPolicy policy) {
    Require.notNull(policy, "SLA policy");
    if (policy.isDefault()) {
      this.defaultPolicy = policy;
    } else {
      byService.put(policy.serviceCode().orElseThrow(), policy);
    }
    return this;
  }

  public synchronized void removeServicePolicy(String serviceCode) {
    byService.remove(Require.notBlank(serviceCode, "Service code").toUpperCase());
  }

  /**
   * @throws SlaConfigurationMissingException if the service has no policy and there is no default
   */
  public synchronized SlaPolicy policyFor(ItService service) {
    Require.notNull(service, "Service");
    SlaPolicy specific = byService.get(service.code());
    if (specific != null) {
      return specific;
    }
    if (defaultPolicy == null) {
      throw new SlaConfigurationMissingException(
          "No SLA policy for service " + service.code() + " and no default policy configured");
    }
    return defaultPolicy;
  }

  /**
   * Convenience: the target for a service and priority, falling back to the default policy if the
   * service policy exists but does not define this priority.
   *
   * @throws SlaConfigurationMissingException if no policy defines a target
   */
  public synchronized SlaTarget targetFor(ItService service, Priority priority) {
    SlaPolicy policy = policyFor(service);
    if (!policy.hasTargetFor(priority) && defaultPolicy != null && policy != defaultPolicy) {
      return defaultPolicy.targetFor(priority);
    }
    return policy.targetFor(priority);
  }

  public synchronized Optional<SlaPolicy> defaultPolicy() {
    return Optional.ofNullable(defaultPolicy);
  }

  public synchronized List<SlaPolicy> all() {
    List<SlaPolicy> all = new ArrayList<>();
    if (defaultPolicy != null) {
      all.add(defaultPolicy);
    }
    all.addAll(byService.values());
    return Collections.unmodifiableList(all);
  }
}
