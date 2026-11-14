package com.servicedesk.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code sla_policies}. */
public interface SpringDataSlaPolicyRepository extends JpaRepository<SlaPolicyEntity, Long> {

  Optional<SlaPolicyEntity> findByServiceCode(String serviceCode);

  Optional<SlaPolicyEntity> findFirstByServiceCodeIsNull();
}
