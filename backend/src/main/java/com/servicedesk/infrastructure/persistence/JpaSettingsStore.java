package com.servicedesk.infrastructure.persistence;

import com.servicedesk.application.port.SettingsStore;
import com.servicedesk.domain.user.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link SettingsStore} in the {@code app_settings} table. */
@Repository
@Transactional
public class JpaSettingsStore implements SettingsStore {

  @PersistenceContext private EntityManager em;

  @Override
  @Transactional(readOnly = true)
  public Optional<String> get(String key) {
    return Optional.ofNullable(em.find(SettingEntity.class, key)).map(s -> s.value);
  }

  @Override
  public void put(String key, String value, UserId changedBy, Instant at) {
    SettingEntity s = em.find(SettingEntity.class, key);
    boolean isNew = s == null;
    if (isNew) {
      s = new SettingEntity();
      s.key = key;
    }
    // fill every column before persisting, so the insert never sees nulls
    s.value = value;
    s.updatedAt = at;
    s.updatedBy = changedBy.value();
    if (isNew) {
      em.persist(s);
    }
  }

  /** Row of {@code app_settings}. */
  @Entity
  @Table(name = "app_settings")
  public static class SettingEntity {

    @Id
    @Column(name = "setting_key")
    String key;

    @Column(name = "setting_value", nullable = false)
    String value;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    String updatedBy;
  }
}
