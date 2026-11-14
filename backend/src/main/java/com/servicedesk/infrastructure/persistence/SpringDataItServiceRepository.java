package com.servicedesk.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code it_services}. */
public interface SpringDataItServiceRepository extends JpaRepository<ItServiceEntity, String> {

  List<ItServiceEntity> findAllByOrderByName();
}
