# Technology Selection Report

## 1. Stack at a glance

| Layer | Technology | Version |
|---|---|---|
| Language (backend) | Java | 21 (LTS) |
| Backend framework | Spring Boot (Web, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator) | 3.3.5 |
| Persistence | Hibernate / JPA, Flyway migrations | Hibernate 6.5, Flyway 10 |
| Database | PostgreSQL (local Docker, cloud Supabase); H2 in PostgreSQL mode for tests | 16 |
| Security | JWT (Nimbus JOSE, HS256), BCrypt | - |
| Frontend | Next.js (App Router), React, TypeScript (strict) | 15.0.3, 18.3.1, 5.6 |
| Frontend tooling | Bun (package manager/runtime), Biome (lint + format) | 1.x, 1.9.4 |
| Testing | JUnit 5, AssertJ, Spring Boot Test / MockMvc, ArchUnit, zonky embedded-postgres, JaCoCo | - |
| DevOps | Docker, Docker Compose, GitHub Actions, Lefthook git hooks, Spotless (google-java-format) | - |
| Hosting targets | Render (backend), Vercel (frontend), Supabase (database) | - |

## 2. Answers to the brief's questions

**1. Why Java?**
The course is about object-oriented design, and Java makes OOP concepts explicit: classes,
interfaces, abstract classes, access modifiers, checked inheritance. Java 21 adds features that
make the model cleaner: **records** for immutable value objects (`IncidentId`, `SlaTarget`,
`AuditEntry`), **sealed classes** so the compiler knows every `IncidentEvent` type, and **pattern
matching for `switch`** for exhaustive handling. It is statically typed (errors at compile time),
has mature tooling and is widely used for enterprise back-ends.

**2. Why Next.js / React / TypeScript for the frontend?**
The brief asks for different GUIs per user type. React's component model lets us build each
role's screens from shared pieces (incident table, badges, SLA meters). TypeScript in strict mode
mirrors the backend's JSON contracts (`lib/types.ts`), catching mismatches at build time. Next.js
gives routing, production builds and an easy deployment to Vercel. We added no UI library -
plain CSS keeps the bundle small (~115 kB per page) and the code readable.

**3. Why Spring Boot?**
It provides exactly the infrastructure an enterprise service desk needs - HTTP, validation,
transactions, JPA, security, scheduling, health checks - without us writing it, so our effort goes
into the domain. Dependency injection fits our design: the domain declares interfaces, Spring
wires the implementations (`DomainConfig`). Crucially, Spring does **not** force itself into the
domain: our domain classes have no annotations.

**4. Why PostgreSQL?**
The data is highly relational (incidents ↔ users ↔ services ↔ categories ↔ SLA policies) and must
stay consistent - foreign keys, check constraints and unique constraints enforce rules even if
code is wrong (e.g. one SLA alert per incident/clock/type). Reports need grouping and counting,
which SQL does natively. PostgreSQL is free, standard, runs in Docker locally and is available as
a managed service (Supabase). Flyway versions the schema so every environment is identical.

**5. Why this architecture?**
A layered monolith with a framework-free domain (see [architecture.md](architecture.md)): one
deployable unit suits a 3-5 person team and a two-week timeline, while strict module boundaries
(enforced by ArchUnit) keep the code maintainable and leave a clear path to split services later.
The stateless REST API with JWT lets the web UI, a future mobile app and integrations share one
backend.

**6. Alternatives considered**

| Choice | Alternative | Why not |
|---|---|---|
| Java / Spring Boot | Node.js / Express | Weaker typing and OOP structure for a course centred on OOP design; we would hand-build transactions, validation, security |
| Java / Spring Boot | Python / Django or FastAPI | Dynamic typing; less explicit interfaces/inheritance; ORM-centric models encourage logic in persistence classes |
| PostgreSQL | MongoDB | Relationships and constraints are central; documents would push integrity checks into code |
| PostgreSQL | MySQL | Comparable; PostgreSQL has better standards support and a free managed tier on Supabase |
| Next.js | Angular | Heavier framework and learning curve; React has a larger ecosystem and the team knows it |
| Next.js | Server-side templates (Thymeleaf) | Harder to build interactive, role-specific screens (live suggestions, polling bell) |
| JWT | Server sessions | Sessions need sticky routing and CSRF protection; JWT keeps the API stateless |
| Monolith | Microservices | Network, deployment and data-consistency overhead with no benefit at this scale |
| Hand-written algorithms | External AI API (e.g. LLM) | Cost, latency, privacy of incident text, non-deterministic tests; ours are explainable and swappable behind interfaces |

**7. Advantages and limitations**

| Advantages | Limitations |
|---|---|
| Strong typing end to end (Java + strict TypeScript) | Two languages to learn (Java and TypeScript) |
| Domain is pure Java: fast tests (89.7 % coverage), clear OOP model | Mapping layer (entities ↔ domain via snapshots) is extra code |
| Mature, secure, well-documented frameworks | Spring Boot start-up time and memory are higher than lightweight frameworks |
| Schema migrations and validation catch drift early | JPA has a learning curve (lazy loading, flushing) |
| Free tiers for the whole stack (Render, Vercel, Supabase) | E-mail/SMS are simulated; a real provider needs an account and an adapter |
| No UI or chart libraries to maintain | Charts are simple bars; complex visualisations would need a library |

**8. How does the technology support object-oriented design?**
- Java's classes, interfaces, abstract classes, `sealed` hierarchies and records express the model
  directly (see [design-patterns.md](design-patterns.md)).
- Spring's dependency injection lets us *program to interfaces*: services receive
  `IncidentRepository`, `AssignmentStrategy`, `EmailSender` - never concrete classes.
- JPA is confined to the infrastructure layer, so domain classes keep encapsulation (no setters,
  no annotations); the Memento snapshot bridges the two.
- ArchUnit turns the architecture rule "domain depends on nothing" into a failing test.
- TypeScript interfaces mirror the backend views, keeping the client's model consistent.

**9. How would the system evolve if users increased significantly?**
- **Horizontal scaling:** the API is stateless (JWT), so more backend instances can run behind a
  load balancer; add a connection pooler for PostgreSQL.
- **Database:** indexes already cover the main filters; next steps are read replicas for reports,
  archiving closed incidents, and full-text search behind `IncidentRepository`.
- **Asynchronous work:** publish domain events to a message broker (Kafka/RabbitMQ) and send
  notifications from a worker - a new `IncidentEventListener`, no domain change.
- **Scheduling:** run the SLA monitor on one node via a distributed lock, or as a separate worker.
- **Caching:** reference data (services, categories, SLA policies) and the intelligence history are
  already small and cacheable; a shared cache (Redis) would serve multiple instances.
- **Splitting services:** the packages (incidents, SLA, notifications, intelligence) are natural
  service boundaries if the team and load ever justify microservices.
