# Object-Oriented Design & Design Patterns

The brief's central question: *how can object-oriented thinking produce a maintainable, extensible
and intelligent incident-management system?* This document shows where each OOP principle and
design pattern is used, why, and what it buys us. Class diagrams are in
[diagrams/uml.md](diagrams/uml.md).

## 1. OOP principles

| Principle | Where | Why it matters here |
|---|---|---|
| **Encapsulation** | `Incident` has only private fields and **no setters**; every change goes through a business method (`assignTo`, `resolve`, …) that checks *who* (permission / relationship), *when* (lifecycle state) and *what* (valid data) **before** changing anything. `Technician` workload changes only via `takeOn()` / `release()`. Persistence uses a snapshot (Memento) instead of opening the fields. | An incident can never be in an impossible state (e.g. RESOLVED without a resolution, or a technician above capacity), whoever calls it. A failed action leaves the object unchanged (TC-11). |
| **Abstraction** | `User`, `IncidentState`, `PriorityStrategy`, `AssignmentStrategy`, `NotificationChannel`, `TextSimilarity`, repositories - callers work with the concept, not the implementation. | `IncidentService` assigns work without knowing *how* a technician is chosen; the notifier sends without knowing *how* SMS is delivered. |
| **Inheritance (true is-a only)** | `User` → `Employee`, `Technician` → `TeamLead`, `Administrator`, `SystemActor`. `IncidentEvent` → 12 event types. `AbstractIncidentState` → 10 states. `DomainException` → 12 exception types. | A team lead *is a* technician (can still be assigned work) with extra rights. Nothing inherits just to reuse code - e.g. `Incident` does not extend anything. |
| **Polymorphism** | `user.can(permission)` - each subclass answers from its own permission set. `state.resolve()` - each state returns the next state or refuses. `strategy.rank(...)`, `channel.deliver(...)`, `event.description()`. | No `switch` on role or status in business code; adding a role, state or event type means adding a class. |
| **Interfaces** | 22 domain interfaces (strategies, channels, listeners, repositories, ports such as `EmailSender` and `SmsSender`) plus application ports (`PasswordHasher`, `AccessTokenIssuer`, `SettingsStore`). Narrow by design (interface segregation): `UserDirectory` (look-ups only) vs `UserRepository` (full). | Contracts between layers; each side can be replaced or faked in tests. |
| **Composition** | `Incident` *owns* its `Note`s, `Resolution`, `Feedback`, `Closure` - they cannot exist without it and are deleted with it. `PriorityBasedStrategy` *contains* a `LoadBasedStrategy` and reuses it. `NotificationDispatcher` is composed of a policy, a factory, a user directory and channels. | Behaviour is built by combining objects rather than deep inheritance. |
| **Association / aggregation** | `Incident` → reporter (`User`), assignee (`Technician`), `ItService`, `Category` (associations: independent lifecycles). `Technician` aggregates `Skill`s. `SlaPolicyCatalog` aggregates `SlaPolicy`s. | Models real relationships: deleting an incident does not delete the service or the technician. |
| **Exception handling** | Hierarchy `DomainException` (abstract) → `InvalidStateTransitionException`, `UnauthorizedOperationException`, `TechnicianUnavailableException`, `WorkloadExceededException`, `ServiceNotFoundException`, `SlaConfigurationMissingException`, `DuplicateIncidentException`, `InvalidCredentialsException`, `NotificationDeliveryException`, `InvalidInputException`, `InvalidIncidentException`, `EntityNotFoundException`. Each has a stable code; one handler maps them to HTTP. | Callers can react precisely; users get meaningful messages; nothing internal leaks. Covers every scenario listed in the brief (see test-cases §6). |
| **Collections / data structures** | `EnumMap<Priority, SlaTarget>` (fixed small key set), `LinkedHashMap` for skills and strategy registry (O(1) lookup, stable order), `HashSet` for "already alerted" keys (O(1) de-duplication), `CopyOnWriteArrayList` for event listeners (read-mostly, thread-safe), `ArrayList` for append-only notes/audit (chronological), `TreeMap`/`TreeSet` for sorted explanations, immutable `List.copyOf` for value objects. | Each choice is documented in its class with the reason (lookup speed, ordering, concurrency, immutability). |
| **Immutability / value objects** | Records: `IncidentId`, `UserId`, `Category`, `ItService`, `Skill`, `SlaTarget`, `PriorityFactors`, `AuditEntry`, `Notification`, events… - validated in their constructors. | Safe to share across threads and layers; invalid values cannot exist. |
| **Generics** | `StrategyRegistry<S extends NamedStrategy>` - one runtime-switchable registry for priority *and* assignment strategies. | Reuse without casting or duplication. |

## 2. Design patterns (8 used; the brief requires 2)

### 2.1 State - incident lifecycle
- **Classes:** `IncidentState` (interface), `AbstractIncidentState` (default: refuse every action),
  `NewState`, `ClassifiedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`,
  `AwaitingInfoState`, `ResolvedState`, `VerifiedState`, `ClosedState`, `CancelledState`.
- **How:** `Incident` delegates each action to its current state: `IncidentState next =
  state.resolve();` - the state returns the next state or throws `InvalidStateTransitionException`.
  States also list their `allowedActions()`, which the UI uses to show only valid buttons.
- **Why:** the brief requires preventing illegal transitions (e.g. NEW → CLOSED). Without the
  pattern that logic becomes a growing `if/switch` inside every method. With it, each state's rules
  are in one small class and a new state is a new class.

### 2.2 Strategy - priority calculation and technician assignment
- **Priority:** `PriorityStrategy` → `MatrixPriorityStrategy` (impact × urgency × criticality),
  `WeightedScorePriorityStrategy` (adds people affected).
- **Assignment:** `AssignmentStrategy` → `SkillBasedStrategy`, `LoadBasedStrategy`,
  `RoundRobinStrategy`, `PriorityBasedStrategy`, `SmartAssignmentStrategy`.
- **Notification selection:** `NotificationPolicy` → `DefaultNotificationPolicy`.
- **Context:** `StrategyRegistry<S>` holds the available strategies and the active one; administrators
  switch at runtime (`PUT /admin/strategies`), the choice is stored in `app_settings`.
- **Why:** the brief asks that "the assignment strategy can be changed without redesigning the
  application". Callers only ever call `registry.active()`.

### 2.3 Factory - incidents, users, notifications
- `IncidentFactory` - the only way to create an incident; validates the report, checks permission
  and the service, assigns the id (from `IncidentIdGenerator`) and the time (from `Clock`), records
  the creation event.
- `UserFactory` - builds the right `User` subclass for a role (also used when loading from the
  database).
- `NotificationFactory` - writes the message for an event *and* a particular reader ("assigned to
  you" vs "being handled by T205").
- **Why:** creation rules live in one place; callers never call subclass constructors.

### 2.4 Observer - domain events
- **Subject:** `Incident` records `IncidentEvent`s; `IncidentEventPublisher` delivers them.
- **Observers (`IncidentEventListener`):** `AuditTrailListener` (audit log),
  `NotificationDispatcher` (in-app/e-mail/SMS), `IncidentHistoryProvider` (refreshes what the
  intelligent features learn from).
- **Why:** complete audit and notifications without the incident knowing about either; a failing
  observer (e.g. e-mail down) cannot break the business action (fault isolation, TC-67).
  `IncidentEvent` is a **sealed** class, so the compiler checks that the notification policy
  handles every event type.

### 2.5 Repository - persistence
- **Domain interfaces:** `IncidentRepository`, `UserRepository`, `ItServiceRepository`,
  `CategoryRepository`, `SlaPolicyRepository`, `AuditLog`, `InAppInbox`, `SlaAlertLog`.
- **Implementations:** `Jpa*` classes in `infrastructure.persistence` (Spring Data JPA) and
  `InMemory*` classes for tests.
- **Why:** the domain talks about "a collection of incidents", not SQL; the database can change
  without touching business code.

### 2.6 Adapter - external providers
- `MailServiceEmailAdapter` adapts the provider-style `SimulatedMailService` (request/response
  objects, HTTP status codes, HTML) to the domain's `EmailSender` port.
- `SmsGatewayAdapter` adapts `SimulatedSmsGateway` (MSISDN numbers, result objects) to `SmsSender`.
- **Why:** a real provider (SendGrid, Twilio) means one new adapter; provider errors become a domain
  `NotificationDeliveryException`.

### 2.7 Memento - saving the incident without breaking encapsulation
- `Incident.snapshot()` exports an `IncidentSnapshot`; `Incident.restore(snapshot, references)`
  rebuilds it. The JPA layer only ever sees the snapshot.
- **Why:** the incident keeps private fields and no setters, yet can be stored and loaded.

### 2.8 Template method (light) and Null Object
- `AbstractIncidentState` implements every action as "refuse"; subclasses override only the
  actions they allow - the skeleton is defined once.
- `SystemActor` is a real `User` for automatic actions, so audit entries always have an actor and no
  code needs `null` checks.

## 3. SOLID in practice

| Principle | Example |
|---|---|
| Single responsibility | `SlaCalculator` computes deadlines; `SlaMonitor` decides what is new; `SlaMonitoringService` orchestrates; the scheduler only triggers it |
| Open/closed | New assignment strategy, notification channel, event listener, category or SLA policy: added, not modified |
| Liskov substitution | Any `Technician` (incl. `TeamLead`) can be assigned; any `AssignmentStrategy` works in `TechnicianAssigner` |
| Interface segregation | `UserDirectory` for notification look-ups vs full `UserRepository`; small single-method ports |
| Dependency inversion | Domain defines `IncidentRepository`, `EmailSender`, `PasswordHasher`; infrastructure implements them |

## 4. Extensibility checklist

| Change | Work required |
|---|---|
| New incident category | Admin adds it in the UI - no code |
| New IT service or SLA policy | Admin adds it in the UI - no code |
| New assignment or priority strategy | One class implementing the interface + one `register(...)` line |
| New notification channel (Slack, push) | One `NotificationChannel` class (+ adapter) + one line in `DomainConfig` |
| New reaction to events (e.g. analytics, webhook) | One `IncidentEventListener` subscribed in `DomainConfig` |
| AI-based categorisation or similarity | Implement `CategoryClassifier` / `TextSimilarity` |
