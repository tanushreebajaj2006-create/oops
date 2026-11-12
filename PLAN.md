# ServiceDesk – Implementation Plan (for review)

Deadline: **22 Nov 2026** (report PDF + 15–20 min video). Today: 12 Nov → ~10 days.
Rubric: Design 10 · **Functionality (MVP) 60** · UX 10 · Tech depth 15 · Testing 5 · Innovation 10 · Docs 10 = 120.

We keep your teammate's stack (Java 21 + Spring Boot, Next.js, PostgreSQL, Flyway) and their key rule:
**`domain/` is pure Java** (no Spring/JPA), enforced by the existing ArchUnit test. That rule is the backbone of
our OOP story: the domain is the "object model", Spring is just the delivery mechanism.

```
Next.js UI  →  api/ (REST controllers, DTOs)  →  application/ (use-case services)
            →  domain/ (pure OOP model + interfaces)  ←  infrastructure/ (JPA repos, JWT, notifiers)
```

Each phase = one reviewable commit (or a few). After each phase I stop, show you what changed and the OOP
idea it demonstrates, and wait for your OK.

---

## Phase 1 — Domain model core (pure Java)  *[OOP heart of the project]*
| Package | Classes | OOP concept shown |
|---|---|---|
| `domain.user` | abstract `User` → `Employee`, `Technician`, `Administrator` (+ `TeamLead` for escalation); `Role` enum; `Skill`; `UserFactory` | **Inheritance** (true is-a), **abstraction**, **Factory** pattern |
| `domain.incident` | `Incident` (private fields, intention-revealing methods like `assignTo()`, `resolve()` – no public setters), `IncidentId`, `Category` (value object, configurable, not hard-coded enum), `ItService` (with business criticality), `Impact`/`Urgency` enums, `Note`, `Resolution`, `Feedback` | **Encapsulation**, **composition** (Incident *owns* notes/resolution), **association** (Incident → reporter, technician, service) |
| `domain.incident.state` | `IncidentState` interface + `NewState`, `ClassifiedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`, `AwaitingInfoState`, `ResolvedState`, `VerifiedState`, `ClosedState` | **State pattern**, **polymorphism** – illegal moves (e.g. NEW→CLOSED) throw `InvalidStateTransitionException` |
| `domain.exception` | `DomainException` base + `InvalidStateTransitionException`, `TechnicianUnavailableException`, `WorkloadExceededException`, `ServiceNotFoundException`, `SlaConfigurationMissingException`, `DuplicateIncidentException`, `UnauthorizedOperationException`, … | **Exception hierarchy** (all 12 cases in the PDF) |

Tests: unit tests for every legal/illegal transition, encapsulation rules.

## Phase 2 — Priority, SLA, Assignment (pure Java, Strategy pattern)
- `domain.priority`: `PriorityStrategy` interface → `MatrixPriorityStrategy` (Impact × Urgency × Criticality, the PDF formula) and `WeightedScorePriorityStrategy` (adds affected-user count). `Priority` enum P1–P4 with documented thresholds.
- `domain.sla`: `SlaPolicy` (per service + priority: response/resolution targets, default table from the PDF), `SlaClock` (deadlines, uses injected `Clock` → testable), `SlaStatus` (ON_TRACK / AT_RISK / BREACHED), `SlaViolation`.
- `domain.assignment`: `AssignmentStrategy` interface → `SkillBasedStrategy`, `LoadBasedStrategy`, `RoundRobinStrategy`, `ExperienceBasedStrategy` (≥2 required; we give 4). Admin can switch the active one at runtime → **"change strategy without redesign"**.
- Technician max-workload rule → `WorkloadExceededException`.

Tests: boundary tests for priority scores, SLA deadline math, each strategy.

## Phase 3 — Events, Notifications, Audit (Observer + Adapter)
- `domain.incident.event`: `IncidentEvent` sealed hierarchy (Created, Classified, PriorityChanged, Assigned, StatusChanged, SlaWarning, SlaViolated, Resolved, Closed, Escalated).
- `IncidentEventPublisher` + `IncidentEventListener` interface → **Observer pattern**.
- `domain.notification`: `NotificationChannel` interface → `InAppChannel` (stored in DB, shown in UI bell), `EmailChannel` (simulated / console log via an **Adapter** around a fake `ExternalEmailGateway`), `SmsChannel` (simulated). `NotificationFactory`. Adding a new channel = one new class.
- `domain.audit`: `AuditEntry` (timestamp, user, action, incident, previous/new state, description) — an `AuditListener` records **every** event → complete history.

## Phase 4 — Persistence (Repository pattern + Flyway)
- `domain` defines repository **interfaces** (`IncidentRepository`, `UserRepository`, …).
- `infrastructure.persistence` implements them with JPA entities + mappers (domain stays JPA-free).
- Flyway `V2__schema.sql` (users, technicians_skills, services, categories, sla_policies, incidents, notes, audit_log, notifications, sla_violations) and `V3__seed_data.sql` (demo users of each role, services like Payment/Network/DB, ~30 sample incidents for reports).
- H2 for tests, Postgres via docker-compose for the demo.

## Phase 5 — Application services + REST API + Security
- `application`: `IncidentService` (orchestrates the 14-step scenario from the PDF), `UserService`, `AdminConfigService`, `ReportService`, `SearchService`.
- `api`: REST endpoints `/api/v1/auth`, `/incidents`, `/incidents/{id}/actions/...`, `/technicians/me/workload`, `/admin/...`, `/reports/...`, `/notifications`.
- Security: register/login with **BCrypt** passwords, **JWT** tokens, **role-based** `@PreAuthorize`, bean validation on inputs, `GlobalExceptionHandler` mapping domain exceptions → clean HTTP errors (400/401/403/404/409).
- Search with ≥5 filters: id, date range, status, priority, category, service, technician, SLA status, free text.
- Scheduled job (every minute): SLA monitor → warnings, violations, **auto-escalation** Technician → Team Lead → Admin.

## Phase 6 — Intelligent features (≥2 required; we do 4–5)
1. **Auto-categorization** – keyword/TF-IDF scoring of description → suggested category.
2. **Duplicate / related incident detection** – text similarity (Jaccard/cosine) + same service + time window.
3. **Smart technician recommendation** – combined score of skill, load, experience, past work on that service.
4. **SLA risk prediction** – flags incidents likely to breach (elapsed % + technician queue).
5. **Suggested resolution** – reuse resolutions of most similar closed incidents.
All behind interfaces so a real AI/ML API could be plugged in later (innovation marks).

## Phase 7 — Frontend (Next.js) – role-specific GUIs
- Login/register; role-based nav (the PDF asks GUIs differ per user).
- **Employee**: report incident (with live category suggestion + "possible duplicates" warning), my incidents, timeline, add info, feedback.
- **Technician**: my queue/workload, acknowledge, notes, request info, resolve.
- **Admin**: dashboard (overview, technician workload, service analysis, SLA violations, charts), users/technicians, services, categories, SLA policies, assignment strategy switcher, audit log, search.
- Notification bell (in-app channel).

## Phase 8 — Testing (≥15 documented cases; we target ~40 automated)
Normal ops, boundaries, invalid input, exceptions, authorization (403s), business rules — JUnit 5 + Mockito unit tests, `@SpringBootTest` + MockMvc integration tests, ArchUnit kept green. `docs/test-cases.md` table maps each test to the requirement.

## Phase 9 — Docs & report
Fill the empty `docs/*.md`: problem analysis, requirements (FR/NFR/business rules), architecture diagram, UML **use-case** + **class** diagrams (PlantUML/Mermaid, exported to PNG), ER diagram + schema, design-patterns justification, tech-stack report (answers all 9 questions), security, demo script for the 3 required workflows. Then assemble the PDF report.

---

### Design patterns used (PDF asks ≥2): State, Strategy (×2), Observer, Factory, Repository, Adapter.

### Suggested team split (PDF requires identifiable responsibility)
UI/UX → Phase 7 · Backend/API → Phase 5 · Domain/OOP → Phases 1–3 · Database → Phase 4 · Testing/DevOps/Intelligence → Phases 6, 8.
*(You can tell me who owns what; I can write commit messages / docs accordingly.)*

### Rough schedule
| Days | Work |
|---|---|
| 12–13 Nov | Phases 1–2 |
| 14–15 Nov | Phases 3–4 |
| 16–17 Nov | Phase 5 |
| 18 Nov | Phase 6 |
| 19–20 Nov | Phase 7 |
| 21 Nov | Phases 8–9, demo rehearsal |
| 22 Nov | Record video, submit |

### Open questions for you
1. Keep this stack (Spring Boot + Next.js)? (Recommended – already set up.)
2. Work on a branch in your fork and open PRs to your teammate's repo, or commit directly to your fork's `main`? (Recommended: feature branch per phase.)
3. I need **push permission** to your fork when you're ready to save work to GitHub.
