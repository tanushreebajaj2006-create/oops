# Testing & Validation

**Result: 140 automated tests, all passing** (113 numbered test cases TC-01 … TC-113 plus
architecture, smoke and context tests). **Line coverage 89.7 %, branch coverage 76.6 %**
(JaCoCo). The frontend passes Biome lint, strict TypeScript and a production build, and the main
workflows were exercised in a browser against the running backend.

---

## 1. Strategy

The design (pure domain model, interfaces for every external concern, injected `Clock`) makes most
logic testable without a server or database. We test at five levels:

| Level | What it proves | Tools | Examples |
|---|---|---|---|
| **Unit (domain)** | Business rules, OOP behaviour, algorithms - fast, no framework | JUnit 5, AssertJ, parameterised tests | `IncidentLifecycleTest`, `PriorityStrategyTest`, `IntelligenceAlgorithmsTest` |
| **Integration (persistence)** | Java objects survive a database round-trip; the SQL migrations match the mapping | Spring Boot test, H2 in PostgreSQL mode running the **real Flyway migrations** with `ddl-auto: validate` | `PersistenceIntegrationTest` |
| **Compatibility** | The same schema and code work on the **production database** | Real PostgreSQL started in-process (zonky embedded-postgres) | `PostgresCompatibilityTest` |
| **Application / API** | Complete use cases and the REST contract: status codes, JSON, JWT security, role checks | MockMvc against the full application context | `IncidentWorkflowTest`, `ApiSecurityAndWorkflowTest`, `IntelligenceIntegrationTest` |
| **Architecture** | The domain layer stays framework-free (the backbone of the OOP design) | ArchUnit | `DomainIsolationTest` |

Supporting techniques:

- **Controllable time** - SLA deadlines, escalation and risk prediction depend on time. Production
  code reads an injected `java.time.Clock`; tests swap in a `MutableClock` and move it forward
  second by second (TC-45, TC-46, TC-91).
- **Isolation** - every database test runs in a transaction that is rolled back; the demo-data test
  uses its own in-memory database.
- **Realistic data** - the seeded demo accounts, services and SLA policies (Flyway V3) and the brief's
  own examples ("Payment application unavailable", the P1 5 min / 30 min SLA, the audit timeline)
  are used as test inputs.
- **Test doubles without mocking frameworks** - small in-memory implementations of the domain's own
  interfaces (`InMemoryAuditLog`, `InMemoryUserDirectory`, a recording `EmailSender`), which is
  possible because the domain depends on interfaces, not on infrastructure.

## 2. How to run

```bash
cd backend
./mvnw verify          # all tests + coverage report: backend/target/site/jacoco/index.html
./mvnw test -Dtest=IncidentWorkflowTest   # one class
```

```bash
cd frontend
bun run lint && bun run typecheck && bun run build
```

GitHub Actions runs both on every pull request (JDK 21, Bun) and uploads the test and coverage
reports as the `backend-test-reports` artifact.

## 3. Coverage

| Layer / package | Lines | Branches |
|---|---|---|
| domain.priority | 99.1 % | 90.0 % |
| domain.audit | 100 % | 93.3 % |
| domain.assignment | 97.8 % | 84.4 % |
| domain.sla | 97.3 % | 84.1 % |
| domain.notification | 97.0 % | 87.6 % |
| domain.intelligence | 96.4 % | 79.6 % |
| domain.common | 94.7 % | 86.4 % |
| domain.user | 93.6 % | 97.6 % |
| domain.incident | 92.7 % | 75.4 % |
| domain.exception | 87.5 % | 100 % |
| infrastructure.security | 99.1 % | 75.0 % |
| infrastructure.persistence | 93.8 % | 85.3 % |
| infrastructure.notification | 92.2 % | 77.3 % |
| infrastructure.config | 87.5 % | 87.5 % |
| application | 78.3 % | 53.9 % |
| api | 72.9 % | 56.5 % |
| **Total** | **89.7 %** | **76.6 %** |

The domain - where the OOP design and business rules live - is covered best. The API and
application layers are lower mainly because not every error path of every endpoint has its own HTTP
test; the underlying rule is in each case tested in the domain.

## 4. Test categories required by the brief

| Category | Count | Representative cases |
|---|---|---|
| Normal operations | 26 | TC-01, TC-20, TC-44, TC-79, TC-86, TC-87 |
| Boundary conditions | 9 | TC-22, TC-23, TC-27, TC-36, TC-38, TC-40, TC-45, TC-46 |
| Invalid inputs | 11 | TC-21, TC-26, TC-41, TC-43, TC-49, TC-75, TC-94 |
| Exceptions / failures | 15 | TC-02, TC-48, TC-51, TC-67, TC-71, TC-76, TC-97, TC-113 |
| Authorization | 15 | TC-13 … TC-19, TC-88, TC-93, TC-95, TC-96 |
| Business rules | 37 | TC-05, TC-07, TC-50, TC-53 … TC-60, TC-89, TC-91 |
| **Total** | **113** | |

(Each test case is counted under its main category; many also cover others.)

## 5. Documented test cases

The brief asks for at least 15 documented test cases. The following 24 cover every required
category and every major module. All were **executed automatically - status: Pass**.

| ID | Category | Module / requirement | Preconditions | Steps / input | Expected result |
|---|---|---|---|---|---|
| TC-01 | Normal | Lifecycle (State pattern) | New incident, technician available | Classify → prioritise → assign → acknowledge → start → resolve → verify → close | Each step moves to the next state; ends CLOSED |
| TC-02 | Exception | Lifecycle: illegal transition | Incident is NEW | Reporter tries to close | `InvalidStateTransitionException`; incident unchanged |
| TC-11 | Business rule | Atomic actions | Assigned incident | Try an illegal action | Nothing changes (status, notes, timestamps) |
| TC-16 | Authorization | Internal notes | Technician added an investigation note | Reporter views notes | Investigation note hidden; comments visible |
| TC-22 | Boundary | Incident validation | - | Titles of 4, 5, 120, 121 characters | 5 and 120 accepted, 4 and 121 rejected |
| TC-27 | Boundary | Workload limit | Technician with max 2 | Assign 2, then a 3rd | 3rd rejected with `WorkloadExceededException`; utilisation 100 % |
| TC-36 | Boundary | Priority = Impact × Urgency × Criticality | - | Scores 64, 36, 32, 18, 12, 8, 6, 1 | P1 ≥ 36, P2 ≥ 18, P3 ≥ 8, else P4 - exactly at each boundary |
| TC-42 | Business rule | Interchangeable priority strategy | Matrix strategy active | Prioritise; switch to Weighted; prioritise again | Same incident becomes P1 then P2; no code change |
| TC-44 | Normal | SLA (brief's table) | P1 incident created at 10:00 | Read deadlines | Response due 10:05, resolution 10:30 |
| TC-46 | Boundary | SLA response deadline | P1 created at 10:00 | Acknowledge at 10:05:00 vs 10:05:01 | First MET, second BREACHED |
| TC-48 | Exception | Missing SLA configuration | No policy for the service | Evaluate SLA | `SlaConfigurationMissingException`, never a guessed deadline |
| TC-53 | Business rule | Skill-based assignment | Team with NETWORK skills 5, 3, 2 and one DBA | Assign a NETWORK incident | Most proficient chosen; DBA excluded; reason recorded |
| TC-56 | Business rule | Round-robin assignment | 4 technicians, one unavailable | Assign 5 incidents | Rotates T101 → T102 → T103 → T101 …; skips unavailable/full |
| TC-64 | Normal | Audit trail (brief format) | - | Run the full scenario | 9 entries: "10:07 - INC2000 - Technician acknowledged incident [ASSIGNED → ACKNOWLEDGED]" … |
| TC-71 | Exception | Notification provider failure ("network failure") | E-mail provider down | Report incident | E-mail delivery FAILED, in-app notification still delivered, report succeeds |
| TC-81 | Normal | Search with ≥ 5 filters | 3 incidents on different services | Search by id, status, priority, category, service, technician, reporter, date, text, paging | Each filter returns exactly the matching incidents; "100%" is matched literally |
| TC-85 | Normal | Persistence on real PostgreSQL | Embedded PostgreSQL | Run migrations, save and reload an incident | Schema validates; snapshot identical incl. microsecond timestamps |
| TC-86 | Normal | **Demo workflow 1**: report → prioritise → assign → notify | Employee E101 | Report "Payment application unavailable" (CRITICAL/HIGH, 300 users) | Auto P1, Payments Gold SLA, assigned to T201 (APPLICATION 5/5), technician and reporter notified, 4 audit entries |
| TC-87 | Normal | **Demo workflow 2**: investigate → resolve → notify | TC-86 incident | Acknowledge, start, note, ask, answer, resolve, verify, rate, close | Workload back to 0, resolution SLA MET, reporter notified, internal note hidden from reporter |
| TC-91 | Business rule | **Demo workflow 3a**: SLA monitoring + escalation | P1 payment incident, 2-min response target | Advance clock 90 s, 3 min, 5 min; run SLA check each time | Warning at 75 %; violation at 3 min and escalation to team lead (reassigned); at 5 min escalation to administrator; nothing repeated |
| TC-95 | Authorization | Role-based API access | Demo accounts | Call admin/report endpoints with no token, forged token, employee, team lead, admin | 401, 401, 403, 403/200, 200 respectively |
| TC-96 | Authorization | Deactivated account | Employee logged in | Admin deactivates them; employee calls API with still-valid token | 401 immediately; login refused; admin cannot deactivate self (400) |
| TC-109 | Business rule | **Demo workflow 3b**: duplicate detection | Open payment outage | Second user drafts "Unable to make payment" | Warned of LIKELY_DUPLICATE with reasons; other reporter's identity not exposed |
| TC-113 | Exception | Error handling | - | Raise every domain exception, a database failure, a concurrent edit | Correct status (401/403/404/409/400/500/502/503) and stable code; no internal details leaked |

## 6. Exception scenarios from the brief

| Scenario (brief) | Handled by | Tests |
|---|---|---|
| Invalid login | `InvalidCredentialsException` → 401, same message for unknown e-mail and wrong password | TC-93 |
| Unauthorized operation | `UnauthorizedOperationException` → 403; Spring Security 401/403 | TC-13 – TC-19, TC-88, TC-95 |
| Invalid incident | `InvalidIncidentException` / `InvalidInputException` → 400 | TC-04, TC-21 – TC-26, TC-97 |
| Invalid state transition | `InvalidStateTransitionException` → 409 | TC-02, TC-03, TC-09, TC-97 |
| Technician unavailable | `TechnicianUnavailableException` → 409 | TC-28, TC-54, TC-60 |
| Technician workload exceeded | `WorkloadExceededException` → 409 | TC-27 |
| Service not found | `ServiceNotFoundException` → 404 | TC-24, TC-97 |
| Missing SLA configuration | `SlaConfigurationMissingException`; the SLA scan skips the incident and continues | TC-48, TC-51 |
| Duplicate incident | Detected and **warned about** (not blocked - a user may know something new); `DuplicateIncidentException` → 409 reserved for explicit duplicate rules | TC-103, TC-109, TC-113 |
| Database failure | `DataAccessException` → 503 with a safe message; concurrent edits → 409 (optimistic locking) | TC-113 |
| Network failure | E-mail/SMS provider errors become `NotificationDeliveryException`; delivery marked FAILED, business action unaffected | TC-71, TC-76, TC-77 |
| Invalid user input | Bean Validation on requests + domain validation → 400 | TC-21 – TC-23, TC-41, TC-43, TC-49, TC-75, TC-94, TC-97 |

## 7. Full catalogue

Categories: **N** normal · **B** boundary · **I** invalid input · **E** exception · **A**
authorization · **R** business rule.

### Domain: users, incident lifecycle, factory (Phase 1)

| ID | Cat. | Test |
|---|---|---|
| TC-01 | N | Full happy path NEW → … → CLOSED |
| TC-02 | E | NEW → CLOSED is rejected |
| TC-03 | E | Cannot resolve before acknowledging and starting work |
| TC-04 | R | Cannot assign before category and priority are known |
| TC-05 | R | Re-assignment moves workload from old to new technician |
| TC-06 | R | Information request pauses work until the reporter answers |
| TC-07 | R | Reopen returns work to the same technician and clears the resolution |
| TC-08 | R | Cancelling an assigned incident frees the technician |
| TC-09 | E | Cannot cancel once work has started |
| TC-10 | R | Allowed actions reflect the current state |
| TC-11 | R | A failed action leaves the incident unchanged |
| TC-12 | R | Feedback only after resolution and only once |
| TC-13 | A | An employee cannot assign incidents |
| TC-14 | A | Only the assigned technician can acknowledge |
| TC-15 | A | Only the reporter or an admin can verify |
| TC-16 | A | Reporter cannot see internal investigation notes |
| TC-17 | A | Unrelated users cannot see or comment on an incident |
| TC-18 | A | A deactivated admin loses all permissions |
| TC-19 | A | The system actor may auto-close but not resolve |
| TC-20 | N | Valid report creates a NEW incident with sequential ids |
| TC-21 | I | Blank or too-short titles are rejected |
| TC-22 | B | Title of exactly 5 and 120 characters accepted, 121 not |
| TC-23 | B | Affected users must be between 1 and 1,000,000 |
| TC-24 | E | Incidents cannot be raised against a retired service |
| TC-25 | A | The system actor is not allowed to report incidents |
| TC-26 | I | Malformed incident ids are rejected |
| TC-27 | B | Workload limit is enforced at the boundary |
| TC-28 | E | Unavailable or deactivated technicians cannot take work |
| TC-29 | B | Release never makes workload negative |
| TC-30 | I | Skills are case-insensitive and proficiency must be 1-5 |
| TC-31 | N | Factory builds the right subclass for each role |
| TC-32 | R | A team lead is a technician with extra permissions |
| TC-33 | A | Permissions differ by role (polymorphism) |
| TC-34 | I | The SYSTEM user and invalid e-mails are rejected |
| TC-35 | R | Users are equal by id; e-mail is normalised |

### Priority, SLA, assignment (Phase 2)

| ID | Cat. | Test |
|---|---|---|
| TC-36 | B | Matrix score = I × U × C with P1/P2/P3 boundaries at 36/18/8 |
| TC-37 | I | Matrix thresholds configurable but must be strictly descending |
| TC-38 | B | Affected-user count maps to a level by order of magnitude |
| TC-39 | R | Weighted score: many affected users raise priority where the matrix does not |
| TC-40 | B | Weighted bands at their extremes and exact boundaries |
| TC-41 | I | Weighted strategy rejects negative weights or weights not summing to 1 |
| TC-42 | R | Incident prioritised by the active strategy; switching needs no code change |
| TC-43 | I | Priority factors reject missing values and zero affected users |
| TC-44 | N | Deadlines follow the brief's table |
| TC-45 | B | Clock goes ON_TRACK → AT_RISK at 75 % → BREACHED after the deadline |
| TC-46 | B | Acknowledging exactly at the deadline meets the SLA; one second late breaches |
| TC-47 | R | A service policy overrides the default; missing priorities fall back |
| TC-48 | E | Missing SLA configuration and unprioritised incidents are reported, not guessed |
| TC-49 | I | SLA policy rejects inconsistent targets and thresholds |
| TC-50 | R | Monitor raises each warning and violation exactly once |
| TC-51 | E | One misconfigured incident does not stop the SLA scan of the others |
| TC-52 | R | Cancelled incident's clocks are NOT_APPLICABLE, not breaches |
| TC-53 | R | Skill-based picks the most proficient, excludes people without the skill |
| TC-54 | E | Skill-based with no specialist fails with TechnicianUnavailable |
| TC-55 | R | Load-based picks the least utilised technician |
| TC-56 | R | Round-robin rotates and skips anyone full or away |
| TC-57 | R | Priority-based: P1 to the expert, P4 to the least busy specialist |
| TC-58 | R | Priority-based falls back to anyone free when no specialist is available |
| TC-59 | R | Switching strategy changes who is chosen for the same incident |
| TC-60 | R | Full, unavailable, deactivated and current technicians are never candidates |

### Events, audit, notifications (Phase 3)

| ID | Cat. | Test |
|---|---|---|
| TC-61 | N | Every lifecycle step records one event with the right before/after status |
| TC-62 | E | A rejected action records no event; pullEvents drains the queue |
| TC-63 | N | Priority events carry the rationale; reassignment names the previous technician |
| TC-64 | N | Audit log holds the complete history in the brief's format |
| TC-65 | N | Audit search filters by actor, action and time window |
| TC-66 | N | Administrative actions are audited without an incident |
| TC-67 | E | A failing listener does not stop the others or the business action |
| TC-68 | N | Reporter gets a creation confirmation by in-app and e-mail |
| TC-69 | R | P1 assignment: technician by in-app, e-mail and SMS; system actor never notified |
| TC-70 | R | P3 assignment sends no SMS; a user without a phone is skipped, not failed |
| TC-71 | E | E-mail provider failure: that delivery FAILED, in-app still arrives |
| TC-72 | R | SLA violation alerts technician and supervisors once each; deactivated users skipped |
| TC-73 | A | In-app inbox: newest first, unread count, only the owner can mark read |
| TC-74 | R | A new channel and a custom policy plug in without changing the dispatcher |
| TC-75 | I | Phone numbers are normalised and validated |
| TC-76 | E | E-mail adapter translates formats and maps provider errors to domain exceptions |
| TC-77 | E | SMS adapter converts +E.164 numbers and reports gateway rejections |

### Persistence (Phase 4)

| ID | Cat. | Test |
|---|---|---|
| TC-78 | N | Seed data: categories, services, SLA policies, one account per role |
| TC-79 | N | An incident survives a full save/reload round-trip |
| TC-80 | R | Technician workload is recomputed from open incidents in the database |
| TC-81 | N | Search by id, status, priority, category, service, technician, reporter, date, text |
| TC-82 | I | Generated user ids, unique e-mail, phone and active flag persist |
| TC-83 | N | Audit trail, inbox and SLA alerts are stored and queried from the database |
| TC-84 | N | SLA policies can be added, changed and removed by configuration |
| TC-85 | N | Migrations, validation and round-trip on real PostgreSQL |

### Application, API, security (Phase 5)

| ID | Cat. | Test |
|---|---|---|
| TC-86 | N | Workflow 1: report → prioritise → assign → notify + audit |
| TC-87 | N | Workflow 2: investigate → resolve → notify, verify, rate, close |
| TC-88 | A | Visibility: employees see their own, technicians their queue; illegal actions refused |
| TC-89 | R | Updating impact recalculates priority; only reporter or supervisor may edit |
| TC-90 | R | Admin switches assignment strategy at runtime; stored and audited |
| TC-91 | R | Workflow 3a: SLA warning, violation, escalation Technician → Team Lead → Admin |
| TC-92 | N | Search by SLA status; dashboard reports reflect the current state |
| TC-93 | A | Login returns a JWT; wrong password and unknown e-mail get the same 401 |
| TC-94 | I | Self-registration; weak passwords and duplicate e-mails rejected |
| TC-95 | A | Role-based access: 401 without/forged token, 403 for the wrong role |
| TC-96 | A | Deactivated account's still-valid token rejected immediately |
| TC-97 | E | Full lifecycle over HTTP with the right status code for each mistake |
| TC-98 | N | Admin configuration over HTTP: category, SLA policy, strategy switch |
| TC-99 | N | Demo data covers every lifecycle state; first SLA run escalates |

### Intelligent features (Phase 6)

| ID | Cat. | Test |
|---|---|---|
| TC-100 | N | Text analysis merges different wordings (stop words, stemming, synonyms) |
| TC-101 | N | Cosine similarity: identical 1, unrelated 0, related in between |
| TC-102 | N | Category classifier suggests the right category, learns new categories, ignores retired ones |
| TC-103 | R | Duplicate detection finds the brief's three payment reports and explains why |
| TC-104 | R | Correlation: service outage burst and shared component across services |
| TC-105 | R | SLA risk predicted from typical fix time, workload and state |
| TC-106 | N | Resolution suggestions reuse the most similar resolved incident's fix |
| TC-107 | R | SMART strategy prefers proven experience and explains its score |
| TC-108 | R | Report without category is auto-categorised when confident, else left for triage |
| TC-109 | R | Workflow 3b: second reporter warned about the likely duplicate |
| TC-110 | N | A fix becomes a suggestion for the next similar incident |
| TC-111 | R | SMART strategy switchable at runtime and explains each assignment |
| TC-112 | A | Correlation and SLA-risk dashboards; supervisors only |

### Error handling (Phase 8)

| ID | Cat. | Test |
|---|---|---|
| TC-113 | E | Every exception maps to the right HTTP status and code; no internals leaked |

### Unnumbered checks

- `DomainIsolationTest` (3 ArchUnit rules): the domain package depends on no Spring, no JPA and no
  infrastructure/API class.
- `HealthControllerTest` (3): health endpoint public, protected endpoints 401, unknown paths denied.
- `ServiceDeskApplicationTests`: the full application context starts with the real migrations.

## 8. Frontend and manual checks

Automated (every CI run): Biome lint (incl. accessibility rules), TypeScript `strict` with
`noUncheckedIndexedAccess`, Next.js production build.

Manual browser checks against the running backend (`LocalDevApplication`, demo data):

| Check | Result |
|---|---|
| Employee signs in → lands on "My incidents" with own incidents only | Pass |
| Report form: live category suggestion (Application, 100 %, matched words shown) | Pass |
| Report form: three existing payment incidents flagged "Likely duplicate" with reasons | Pass |
| Submit → auto-categorised, P3 by matrix, assigned by skill; audit trail shows each step | Pass |
| Incident page: Payments Gold SLA meters, related incidents, role-appropriate actions | Pass |
| Expired token (after 8 h) → automatically signed out | Pass |
| Team lead dashboard: overview, possible major incident, predicted breach, workload, service analysis | Pass |
| Technician queue, admin console, every action button | To do before the demo recording |

## 9. Known limitations

- No automated browser (end-to-end) tests for the frontend; the REST API it uses is tested over
  HTTP instead (TC-93 – TC-98), and the UI was checked manually.
- Load/performance testing was out of scope; queries are indexed and paged (see
  [database.md](database.md)).
- The SLA-risk API endpoint is tested for access control; its predictions are tested in the
  domain (TC-105) because a realistic high-risk case needs controlled time and history.
