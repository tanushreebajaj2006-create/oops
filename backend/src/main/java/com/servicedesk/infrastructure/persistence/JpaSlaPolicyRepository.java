package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaPolicyRepository;
import com.servicedesk.domain.sla.SlaTarget;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link SlaPolicyRepository} backed by {@code sla_policies} and {@code sla_targets}. */
@Repository
@Transactional
public class JpaSlaPolicyRepository implements SlaPolicyRepository {

  private final SpringDataSlaPolicyRepository jpa;

  public JpaSlaPolicyRepository(SpringDataSlaPolicyRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public SlaPolicy save(SlaPolicy policy) {
    String serviceCode = policy.serviceCode().orElse(null);
    SlaPolicyEntity e =
        (serviceCode == null
                ? jpa.findFirstByServiceCodeIsNull()
                : jpa.findByServiceCode(serviceCode))
            .orElseGet(() -> new SlaPolicyEntity(serviceCode));
    e.name = policy.name();
    e.warningThreshold = policy.warningThreshold();
    e.targets.clear();
    for (Map.Entry<Priority, SlaTarget> t : policy.targets().entrySet()) {
      e.targets.add(
          new SlaPolicyEntity.TargetValue(
              t.getKey().name(),
              t.getValue().response().toSeconds(),
              t.getValue().resolution().toSeconds()));
    }
    jpa.save(e);
    return policy;
  }

  @Override
  @Transactional(readOnly = true)
  public List<SlaPolicy> findAll() {
    return jpa.findAll().stream().map(JpaSlaPolicyRepository::toDomain).toList();
  }

  @Override
  public void deleteServicePolicy(String serviceCode) {
    jpa.findByServiceCode(serviceCode.toUpperCase()).ifPresent(jpa::delete);
  }

  private static SlaPolicy toDomain(SlaPolicyEntity e) {
    SlaPolicy p =
        e.serviceCode == null
            ? SlaPolicy.defaultPolicy(e.name)
            : SlaPolicy.forService(e.name, e.serviceCode);
    p.setWarningThreshold(e.warningThreshold);
    // add in P1..P4 order so the "higher priority is never looser" rule is checked naturally
    e.targets.stream()
        .sorted(Comparator.comparing(t -> Priority.valueOf(t.priority)))
        .forEach(
            t ->
                p.setTarget(
                    Priority.valueOf(t.priority),
                    SlaTarget.of(
                        Duration.ofSeconds(t.responseSeconds),
                        Duration.ofSeconds(t.resolutionSeconds))));
    return p;
  }
}
