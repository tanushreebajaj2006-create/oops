package com.servicedesk.application;

import com.servicedesk.domain.exception.DomainException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaPolicyRepository;
import com.servicedesk.domain.sla.SlaSnapshot;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives every caller an {@link SlaCalculator} built from the policies currently stored, so a change
 * an administrator makes to an SLA policy takes effect on the next request without a restart.
 */
@Service
@Transactional(readOnly = true)
public class SlaService {

  private final SlaPolicyRepository policies;
  private final Clock clock;

  public SlaService(SlaPolicyRepository policies, Clock clock) {
    this.policies = policies;
    this.clock = clock;
  }

  public SlaCalculator calculator() {
    return new SlaCalculator(policies.loadCatalog(), clock);
  }

  /** SLA picture, or empty if the incident has no priority yet or no policy applies. */
  public Optional<SlaSnapshot> snapshot(Incident incident) {
    return snapshot(incident, calculator());
  }

  public static Optional<SlaSnapshot> snapshot(Incident incident, SlaCalculator calculator) {
    if (incident.priority().isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(calculator.evaluate(incident));
    } catch (DomainException e) {
      return Optional.empty();
    }
  }
}
