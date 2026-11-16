package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentReferences;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Repository pattern</b> implementation of {@link IncidentRepository}.
 *
 * <p>Saving: {@code Incident.snapshot()} -> {@link IncidentMapper} -> row. Loading: row -> snapshot
 * -> {@code Incident.restore()}, with a {@link LoadContext} that resolves user, service and
 * category references once per load (so a technician appearing on twenty incidents in a search
 * result is one object with one workload).
 *
 * <p>Searching uses the JPA Criteria API: each optional filter of {@link IncidentQuery} adds one
 * predicate, so the database does the filtering, sorting and paging.
 */
@Repository
@Transactional
public class JpaIncidentRepository implements IncidentRepository {

  private final SpringDataIncidentRepository jpa;
  private final UserRepository users;
  private final ItServiceRepository services;
  private final CategoryRepository categories;
  private final DatabaseSequences sequences;

  @PersistenceContext private EntityManager em;

  public JpaIncidentRepository(
      SpringDataIncidentRepository jpa,
      UserRepository users,
      ItServiceRepository services,
      CategoryRepository categories,
      DatabaseSequences sequences) {
    this.jpa = jpa;
    this.users = users;
    this.services = services;
    this.categories = categories;
    this.sequences = sequences;
  }

  @Override
  public IncidentId nextId() {
    return IncidentId.of(sequences.next("incident_seq"));
  }

  @Override
  public Incident save(Incident incident) {
    String id = incident.id().value();
    IncidentEntity entity = jpa.findById(id).orElseGet(() -> new IncidentEntity(id));
    IncidentMapper.copy(incident.snapshot(), entity);
    jpa.save(entity);
    return incident;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Incident> find(IncidentId id) {
    LoadContext ctx = new LoadContext();
    return jpa.findById(id.value()).map(e -> Incident.restore(IncidentMapper.toSnapshot(e), ctx));
  }

  @Override
  @Transactional(readOnly = true)
  public List<Incident> search(IncidentQuery query) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<IncidentEntity> cq = cb.createQuery(IncidentEntity.class);
    Root<IncidentEntity> root = cq.from(IncidentEntity.class);
    cq.where(matching(query).toPredicate(root, cq, cb));
    cq.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
    List<IncidentEntity> rows =
        em.createQuery(cq)
            .setFirstResult(query.offset())
            .setMaxResults(query.limit())
            .getResultList();
    LoadContext ctx = new LoadContext();
    return rows.stream().map(e -> Incident.restore(IncidentMapper.toSnapshot(e), ctx)).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long count(IncidentQuery query) {
    return jpa.count(matching(query));
  }

  /** One predicate per filter that is set; all combined with AND. */
  static Specification<IncidentEntity> matching(IncidentQuery q) {
    return (root, cq, cb) -> {
      List<Predicate> p = new ArrayList<>();
      if (q.incidentId() != null) {
        p.add(cb.equal(root.get("id"), q.incidentId().value()));
      }
      if (!q.statuses().isEmpty()) {
        p.add(root.get("status").in(q.statuses().stream().map(Enum::name).toList()));
      }
      if (!q.priorities().isEmpty()) {
        p.add(root.get("priority").in(q.priorities().stream().map(Enum::name).toList()));
      }
      if (q.categoryCode() != null) {
        p.add(cb.equal(root.get("categoryCode"), q.categoryCode()));
      }
      if (q.serviceCode() != null) {
        p.add(cb.equal(root.get("serviceCode"), q.serviceCode()));
      }
      if (q.assigneeId() != null) {
        p.add(cb.equal(root.get("assigneeId"), q.assigneeId().value()));
      }
      if (q.reporterId() != null) {
        p.add(cb.equal(root.get("reporterId"), q.reporterId().value()));
      }
      if (q.createdFrom() != null) {
        p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.createdFrom()));
      }
      if (q.createdTo() != null) {
        p.add(cb.lessThan(root.get("createdAt"), q.createdTo()));
      }
      if (q.text() != null) {
        String pattern = "%" + escapeLike(q.text().toLowerCase()) + "%";
        p.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\')));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  /** Stops user text such as "100%" from acting as a LIKE wildcard. */
  static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** Resolves references for one load operation, caching each lookup. */
  private final class LoadContext implements IncidentReferences {
    private final Map<UserId, User> userCache = new HashMap<>();
    private final Map<String, ItService> serviceCache = new HashMap<>();
    private final Map<String, Category> categoryCache = new HashMap<>();

    @Override
    public User user(UserId id) {
      return userCache.computeIfAbsent(id, users::get);
    }

    @Override
    public ItService service(String code) {
      return serviceCache.computeIfAbsent(code, services::get);
    }

    @Override
    public Category category(String code) {
      return categoryCache.computeIfAbsent(code, categories::get);
    }
  }
}
