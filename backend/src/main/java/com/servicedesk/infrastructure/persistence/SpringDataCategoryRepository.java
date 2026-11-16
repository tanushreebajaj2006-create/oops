package com.servicedesk.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code categories}. */
public interface SpringDataCategoryRepository extends JpaRepository<CategoryEntity, String> {

  List<CategoryEntity> findAllByOrderByName();
}
