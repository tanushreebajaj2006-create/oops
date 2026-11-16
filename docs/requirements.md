# Requirements Specification

Every requirement is traceable to the code that implements it and the tests that prove it
(see [test-cases.md](test-cases.md)).

## 1. User roles

| Role | Description | Key permissions |
|---|---|---|
| **Employee** | Reports and follows their own incidents | REPORT_INCIDENT |
| **Technician** | Works on incidents assigned to them | + WORK_ON_INCIDENTS, CLASSIFY_INCIDENTS |
| **Team lead** | Senior technician who triages, assigns and receives escalations | + ASSIGN_INCIDENTS, VIEW_ALL_INCIDENTS, VIEW_REPORTS, RECEIVE_ESCALATIONS |
| **Administrator / IT manager** | Configures the system and oversees everything | all permissions incl. MANAGE_USERS, CONFIGURE_SYSTEM, VIEW_AUDIT_LOG |
| **System** (automated actor) | Performs automatic classification, assignment, escalation | CLASSIFY, ASSIGN, VIEW_ALL, CONFIGURE |

Permissions are declared polymorphically by each `User` subclass (`Employee`, `Technician`,
`TeamLead`, `Administrator`, `SystemActor`).

## 2. Functional requirements

### FR-1 User management
| ID | Requirement | Implementation |
|---|---|---|
| FR-1.1 | Employees can register and log in | `AuthController`, `AuthService` |
| FR-1.2 | Administrators create users of any role, edit them, deactivate/activate them | `AdminController /admin/users`, `UserService` |
| FR-1.3 | Administrators manage technicians' skills, experience, maximum workload, availability | `UserService.update` |
| FR-1.4 | Technicians mark themselves available/unavailable | `PUT /technicians/me/availability` |

### FR-2 Incident management
| ID | Requirement | Implementation |
|---|---|---|
| FR-2.1 | Report an incident: title, description, affected service, impact, urgency, people affected, optional category | `POST /incidents`, `IncidentFactory` |
| FR-2.2 | View an incident with status, priority, SLA, notes, resolution, history | `GET /incidents/{id}` |
| FR-2.3 | Update details while open (reporter or supervisor); priority recalculated if impact/urgency change | `PATCH /incidents/{id}`, `Incident.updateDetails` |
| FR-2.4 | Assign manually or by the active strategy; see ranked recommendations | `POST /incidents/{id}/assign`, `/recommendations` |
| FR-2.5 | Acknowledge, start investigation, add notes (internal or visible), ask/answer questions | lifecycle endpoints |
| FR-2.6 | Resolve with summary and root cause; verify or reopen; close; cancel | lifecycle endpoints |
| FR-2.7 | Reporter rates the resolution (1-5) once | `POST /incidents/{id}/feedback` |

### FR-3 Classification and priority
| ID | Requirement | Implementation |
|---|---|---|
| FR-3.1 | Categories are data (Hardware, Software, Network, Database, Cloud, Security, Authentication, Application, Storage) and can be added without code changes | `Category`, `CategoryRepository`, admin UI |
| FR-3.2 | Priority calculated automatically from impact, urgency, business criticality (and people affected) | `MatrixPriorityStrategy`, `WeightedScorePriorityStrategy` |
| FR-3.3 | The priority algorithm can be switched at runtime; its reasoning is recorded | `StrategyRegistry`, `PriorityAssessment` |
| FR-3.4 | Supervisors and technicians can override priority | `POST /incidents/{id}/priority` |

### FR-4 Technician assignment
| ID | Requirement | Implementation |
|---|---|---|
| FR-4.1 | At least two assignment strategies - provided: skill-based, load-based, round-robin, priority-based, smart | `domain.assignment` |
| FR-4.2 | Only active, available technicians below their maximum workload are eligible | `TechnicianAssigner.eligible` |
| FR-4.3 | Strategy changeable without redesign; choice persisted | `AdminService.changeStrategies`, `app_settings` |

### FR-5 SLA management
| ID | Requirement | Implementation |
|---|---|---|
| FR-5.1 | SLA targets per priority (default: P1 5 min/30 min, P2 15 min/2 h, P3 1 h/8 h, P4 4 h/24 h), with per-service overrides | `SlaPolicy`, `SlaPolicyCatalog` |
| FR-5.2 | Response and resolution deadlines calculated and tracked | `SlaCalculator` |
| FR-5.3 | Warning before the deadline (configurable threshold, default 75 %) | `SlaMonitor` |
| FR-5.4 | Violations detected, recorded once and notified | `SlaMonitor`, `SlaAlertLog`, `sla_alerts` table |
| FR-5.5 | Continuous background monitoring (every minute) | `SlaMonitorScheduler` |

### FR-6 Notifications
| ID | Requirement | Implementation |
|---|---|---|
| FR-6.1 | Notify on creation, assignment, priority change, info request, SLA warning/violation, resolution, closure, escalation | `DefaultNotificationPolicy` |
| FR-6.2 | At least two mechanisms - provided: in-app, e-mail (simulated), SMS (simulated) | `InAppChannel`, `EmailChannel`, `SmsChannel` |
| FR-6.3 | New mechanisms addable without changing existing code | `NotificationChannel` interface |

### FR-7 Audit
| ID | Requirement | Implementation |
|---|---|---|
| FR-7.1 | Every important action recorded with timestamp, user, action, incident, previous and new state, description | `AuditTrailListener`, `AuditEntry` |
| FR-7.2 | Administrative changes audited too | `AuditEntry.adminAction` |
| FR-7.3 | Audit log searchable by incident, user, action, time | `GET /admin/audit` |

### FR-8 Search and reporting
| ID | Requirement | Implementation |
|---|---|---|
| FR-8.1 | Search by id, date, status, priority, category, service, technician, reporter, text, SLA status (≥ 5 required) | `GET /incidents`, `IncidentQuery` |
| FR-8.2 | Overview: total, open, critical, resolved, SLA violations, compliance, average resolution time, rating | `GET /reports/overview` |
| FR-8.3 | Technician workload, service analysis, category analysis, SLA report | `/reports/*` |

### FR-9 Intelligent features (≥ 2 required - 6 provided)
| ID | Feature (brief letter) | Implementation |
|---|---|---|
| FR-9.1 | Duplicate incident detection (A) | `DuplicateDetector` |
| FR-9.2 | Incident correlation (B) | `IncidentCorrelator` |
| FR-9.3 | Automatic escalation Technician → Team Lead → Administrator (C) | `EscalationPolicy`, `SlaMonitoringService` |
| FR-9.4 | Smart technician recommendation (D) | `SmartAssignmentStrategy` |
| FR-9.5 | SLA risk detection / prediction (E) | `SlaRiskPredictor` |
| FR-9.6 | Automatic categorisation (F) | `NaiveBayesCategoryClassifier` |
| FR-9.7 | Suggested resolution (G) | `ResolutionSuggester` |

## 3. Non-functional requirements

| ID | Quality | Requirement | How it is met |
|---|---|---|---|
| NFR-1 | Maintainability | Business rules in one place, independent of frameworks | Pure-Java domain enforced by ArchUnit tests |
| NFR-2 | Extensibility | New category, strategy, notification channel or SLA policy without changing existing classes | Data-driven categories/policies; Strategy and Observer patterns; interfaces |
| NFR-3 | Security | Authentication, role-based authorisation, hashed passwords, validated input | JWT, BCrypt, 3-layer authorisation, Bean Validation (see [security.md](security.md)) |
| NFR-4 | Reliability | One failure must not cascade | Listener fault isolation, per-delivery notification failures, SLA scan skips bad incidents, optimistic locking |
| NFR-5 | Auditability | Complete, unalterable history | Append-only audit log with no update/delete path |
| NFR-6 | Performance | Interactive responses for hundreds of open incidents | Indexed queries, database-side filtering and paging, cached history for intelligence |
| NFR-7 | Usability | Different screens per role; actions offered only when valid | Role-based navigation; `allowedActions` from the State pattern |
| NFR-8 | Testability | Automated tests at every layer | 140 tests, 89.7 % line coverage, controllable clock |
| NFR-9 | Portability | Runs locally and in the cloud | Docker Compose; PostgreSQL / Supabase; stateless API |
| NFR-10 | Accessibility | Usable with keyboard and screen readers | Labelled form fields, semantic elements, Biome a11y lint rules |

## 4. Business rules

| ID | Rule | Enforced by |
|---|---|---|
| BR-1 | Lifecycle: NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS (↔ AWAITING_INFO) → RESOLVED → VERIFIED → CLOSED. Re-assigning an ACKNOWLEDGED or IN_PROGRESS incident returns it to ASSIGNED; reopening a RESOLVED incident returns it to IN_PROGRESS; only NEW, CLASSIFIED and ASSIGNED incidents can be cancelled; closing requires VERIFIED. Any other transition is rejected | State classes (`NewState` … `ClosedState`) |
| BR-2 | An incident needs a category and priority before it can be assigned | `Incident.assignTo` |
| BR-3 | Only the assigned technician can acknowledge, start, ask the reporter, and resolve | `Incident.requireAssignee` |
| BR-4 | Only the reporter (or an administrator) can verify, reopen or close; the system can auto-close | `Incident.verify/reopen/close` |
| BR-5 | Reporters never see internal investigation notes | `Incident.notesVisibleTo` |
| BR-6 | A technician cannot exceed their maximum open incidents; unavailable or deactivated technicians receive no work | `Technician.takeOn` |
| BR-7 | Feedback only after resolution, once, rating 1-5 | `Incident.giveFeedback`, `Feedback` |
| BR-8 | Higher priorities never get looser SLA targets than lower ones | `SlaPolicy.setTarget` |
| BR-9 | A service's own SLA policy overrides the default; missing priorities fall back to the default | `SlaPolicyCatalog` |
| BR-10 | Each SLA warning and violation is raised once per incident and clock | `SlaAlertLog`, unique constraint |
| BR-11 | Unacknowledged P1/P2 incidents escalate to a team lead when the response deadline passes and to an administrator after a further full response period | `EscalationPolicy` |
| BR-12 | Automatic categorisation is applied only when the classifier is confident (≥ 60 % and 25 points ahead of the next guess) | `CategoryClassifier.isConfident` |
| BR-13 | Retired services and categories cannot be used for new incidents but stay in history | `ItService.requireActive`, `Category.active` |
| BR-14 | Self-registration creates employees only; passwords need 8-72 characters with a letter and a digit | `AuthService` |
| BR-15 | Administrators cannot deactivate themselves | `UserService.update` |
