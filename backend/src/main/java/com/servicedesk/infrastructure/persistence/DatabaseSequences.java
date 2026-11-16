package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Component;

/**
 * Reads the next value of a database sequence in whatever SQL the current database speaks ({@code
 * nextval('x')} on PostgreSQL, {@code NEXT VALUE FOR x} on H2) by asking Hibernate's dialect, so
 * the same code works in production and in tests.
 */
@Component
public class DatabaseSequences {

  @PersistenceContext private EntityManager em;

  public long next(String sequenceName) {
    Dialect dialect =
        em.getEntityManagerFactory()
            .unwrap(SessionFactoryImplementor.class)
            .getJdbcServices()
            .getDialect();
    String sql = dialect.getSequenceSupport().getSequenceNextValString(sequenceName);
    return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
  }
}
