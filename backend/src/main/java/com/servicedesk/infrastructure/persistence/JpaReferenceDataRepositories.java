package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.priority.Level;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementations of the two reference-data repositories. They are tiny, so they share this
 * file as public nested classes; Spring picks them up through the {@code @Repository} annotation.
 */
public final class JpaReferenceDataRepositories {

  private JpaReferenceDataRepositories() {}

  /** {@link CategoryRepository} backed by the {@code categories} table. */
  @Repository
  @Transactional
  public static class Categories implements CategoryRepository {

    private final SpringDataCategoryRepository jpa;

    public Categories(SpringDataCategoryRepository jpa) {
      this.jpa = jpa;
    }

    @Override
    public Category save(Category category) {
      CategoryEntity e =
          jpa.findById(category.code()).orElseGet(() -> new CategoryEntity(category.code()));
      e.name = category.name();
      e.active = category.active();
      jpa.save(e);
      return category;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> find(String code) {
      return code == null
          ? Optional.empty()
          : jpa.findById(code.toUpperCase()).map(Categories::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
      return jpa.findAllByOrderByName().stream().map(Categories::toDomain).toList();
    }

    private static Category toDomain(CategoryEntity e) {
      return new Category(e.code, e.name, e.active);
    }
  }

  /** {@link ItServiceRepository} backed by the {@code it_services} table. */
  @Repository
  @Transactional
  public static class Services implements ItServiceRepository {

    private final SpringDataItServiceRepository jpa;

    public Services(SpringDataItServiceRepository jpa) {
      this.jpa = jpa;
    }

    @Override
    public ItService save(ItService service) {
      ItServiceEntity e =
          jpa.findById(service.code()).orElseGet(() -> new ItServiceEntity(service.code()));
      e.name = service.name();
      e.criticality = service.criticality().name();
      e.active = service.active();
      jpa.save(e);
      return service;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItService> find(String code) {
      return code == null
          ? Optional.empty()
          : jpa.findById(code.toUpperCase()).map(Services::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItService> findAll() {
      return jpa.findAllByOrderByName().stream().map(Services::toDomain).toList();
    }

    private static ItService toDomain(ItServiceEntity e) {
      return new ItService(e.code, e.name, Level.valueOf(e.criticality), e.active);
    }
  }
}
