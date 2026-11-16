# UML Diagrams

Diagrams are written in [Mermaid](https://mermaid.js.org/) so they render on GitHub and stay in
version control next to the code. Notation in class diagrams:

| Symbol | Meaning |
|---|---|
| `<|--` | inheritance (is-a) |
| `<|..` | implements an interface |
| `*--` | composition (part cannot exist without the whole) |
| `o--` | aggregation (whole has parts with their own life) |
| `-->` | association |
| `..>` | dependency / uses |

## 1. Use case diagram

```mermaid
flowchart LR
  EMP(["👤 Employee"])
  TEC(["👤 Technician"])
  LEAD(["👤 Team lead"])
  ADM(["👤 Administrator /<br/>IT manager"])
  SYS(["⚙ System<br/>(automation, scheduler)"])

  subgraph E["Employee"]
    direction TB
    e1(["Register / log in"])
    e2(["Report incident"])
    e3(["Track own incidents"])
    e4(["Answer questions / add information"])
    e5(["Verify, reopen or close"])
    e6(["Give feedback"])
  end
  subgraph T["Technician"]
    direction TB
    t1(["View queue and workload"])
    t2(["Acknowledge and investigate"])
    t3(["Add investigation notes"])
    t4(["Request information"])
    t5(["Resolve incident"])
    t6(["Set availability"])
    t7(["Classify / override priority"])
  end
  subgraph L["Team lead"]
    direction TB
    l1(["Assign with recommendations"])
    l2(["Dashboard, SLA risk, correlations"])
    l3(["Receive escalations"])
    l4(["Search all incidents"])
  end
  subgraph A["Administrator"]
    direction TB
    a1(["Manage users and technicians"])
    a2(["Manage services and categories"])
    a3(["Configure SLA policies"])
    a4(["Switch strategies"])
    a5(["Audit log and reports"])
  end
  subgraph S["Automated"]
    direction TB
    s1(["Auto-categorise, prioritise, assign"])
    s2(["Monitor SLA, warn, escalate"])
    s3(["Detect duplicates and correlations"])
    s4(["Notify and audit"])
  end

  e1 ~~~ e2 ~~~ e3 ~~~ e4 ~~~ e5 ~~~ e6
  t1 ~~~ t2 ~~~ t3 ~~~ t4 ~~~ t5 ~~~ t6 ~~~ t7
  l1 ~~~ l2 ~~~ l3 ~~~ l4
  a1 ~~~ a2 ~~~ a3 ~~~ a4 ~~~ a5
  s1 ~~~ s2 ~~~ s3 ~~~ s4
  EMP --- E
  TEC --- T
  LEAD --- L
  ADM --- A
  SYS --- S
  LEAD -. "is a" .-> TEC
```

Each actor is linked to the group of use cases it can perform. A **team lead is a technician**
(inherits every technician use case) with extra rights, and the **administrator** has all team-lead
use cases plus configuration. Reporting an incident *includes* the automated classify → prioritise
→ assign → notify sequence.

## 2. Class diagram - users and the incident aggregate

```mermaid
classDiagram
  direction LR
  class User {
    <<abstract>>
    -UserId id
    -String fullName
    -String email
    -String passwordHash
    -String phone
    -boolean active
    +role()* Role
    #permissions()* Set~Permission~
    +can(Permission) boolean
    +requirePermission(Permission) void
    +changePhone(String) void
    +deactivate() void
  }
  class Employee {
    -String department
    +moveToDepartment(String) void
  }
  class Technician {
    -Map~String,Skill~ skills
    -int experienceYears
    -int maxWorkload
    -int activeWorkload
    -boolean available
    +takeOn() void
    +release() void
    +canTakeMoreWork() boolean
    +utilisation() double
    +skillFor(String) Optional~Skill~
  }
  class TeamLead
  class Administrator
  class SystemActor {
    +INSTANCE$ SystemActor
  }
  class Skill {
    <<record>>
    String categoryCode
    int proficiency
  }
  class UserFactory {
    +create(NewUser)$ User
  }
  User <|-- Employee
  User <|-- Technician
  Technician <|-- TeamLead
  User <|-- Administrator
  User <|-- SystemActor
  Technician o-- "0..*" Skill
  UserFactory ..> User : creates

  class Incident {
    -IncidentId id
    -String title
    -String description
    -Level impact
    -Level urgency
    -int affectedUsers
    -Priority priority
    -IncidentState state
    -Instant createdAt
    -Instant acknowledgedAt
    -Instant resolvedAt
    -int escalationLevel
    -List~IncidentEvent~ pendingEvents
    +classify(Category, User, Instant) void
    +prioritize(PriorityStrategy, User, Instant) PriorityAssessment
    +assignTo(Technician, User, Instant, String) void
    +acknowledge(User, Instant) void
    +startWork(User, Instant) void
    +requestInfo(User, String, Instant) void
    +resolve(User, String, String, Instant) void
    +verify(User, Instant) void
    +reopen(User, String, Instant) void
    +close(User, String, Instant) void
    +escalate(int, User, String, User, Instant) void
    +updateDetails(...) boolean
    +allowedActions() Set~IncidentAction~
    +pullEvents() List~IncidentEvent~
    +snapshot() IncidentSnapshot
    +restore(IncidentSnapshot, IncidentReferences)$ Incident
  }
  class Note {
    <<record>>
    User author
    NoteType type
    String text
    Instant createdAt
  }
  class Resolution {
    <<record>>
    String summary
    String rootCause
  }
  class Feedback {
    <<record>>
    int rating
    String comment
  }
  class Closure {
    <<record>>
    Instant closedAt
    String note
  }
  class ItService {
    <<record>>
    String code
    String name
    Level criticality
    boolean active
  }
  class Category {
    <<record>>
    String code
    String name
    boolean active
  }
  class IncidentFactory {
    -IncidentIdGenerator idGenerator
    -Clock clock
    +create(NewIncident) Incident
  }
  Incident *-- "0..*" Note
  Incident *-- "0..1" Resolution
  Incident *-- "0..1" Feedback
  Incident *-- "0..1" Closure
  Incident --> "1" User : reporter
  Incident --> "0..1" Technician : assignee
  Incident --> "1" ItService : affects
  Incident --> "0..1" Category
  IncidentFactory ..> Incident : creates
```

## 3. Class diagram - State pattern (incident lifecycle)

```mermaid
classDiagram
  class IncidentState {
    <<interface>>
    +status() IncidentStatus
    +allowedActions() Set~IncidentAction~
    +classify() IncidentState
    +assign() IncidentState
    +acknowledge() IncidentState
    +startWork() IncidentState
    +requestInfo() IncidentState
    +provideInfo() IncidentState
    +resolve() IncidentState
    +verify() IncidentState
    +reopen() IncidentState
    +close() IncidentState
    +cancel() IncidentState
  }
  class AbstractIncidentState {
    <<abstract>>
    every action: throw InvalidStateTransitionException
  }
  IncidentState <|.. AbstractIncidentState
  AbstractIncidentState <|-- NewState
  AbstractIncidentState <|-- ClassifiedState
  AbstractIncidentState <|-- AssignedState
  AbstractIncidentState <|-- AcknowledgedState
  AbstractIncidentState <|-- InProgressState
  AbstractIncidentState <|-- AwaitingInfoState
  AbstractIncidentState <|-- ResolvedState
  AbstractIncidentState <|-- VerifiedState
  AbstractIncidentState <|-- ClosedState
  AbstractIncidentState <|-- CancelledState
  class Incident {
    -IncidentState state
  }
  Incident --> IncidentState : current state
  AbstractIncidentState ..> InvalidStateTransitionException : throws
```

### Lifecycle (state machine)

```mermaid
stateDiagram-v2
  [*] --> NEW : report
  NEW --> CLASSIFIED : classify
  CLASSIFIED --> ASSIGNED : assign
  ASSIGNED --> ACKNOWLEDGED : acknowledge
  ACKNOWLEDGED --> IN_PROGRESS : start work
  IN_PROGRESS --> AWAITING_INFO : request info
  AWAITING_INFO --> IN_PROGRESS : provide info
  IN_PROGRESS --> RESOLVED : resolve
  RESOLVED --> VERIFIED : verify
  RESOLVED --> IN_PROGRESS : reopen
  VERIFIED --> CLOSED : close
  ACKNOWLEDGED --> ASSIGNED : reassign / escalate
  IN_PROGRESS --> ASSIGNED : reassign
  NEW --> CANCELLED : cancel
  CLASSIFIED --> CANCELLED : cancel
  ASSIGNED --> CANCELLED : cancel
  CLOSED --> [*]
  CANCELLED --> [*]
```

## 4. Class diagram - Strategy pattern (priority and assignment)

```mermaid
classDiagram
  class NamedStrategy {
    <<interface>>
    +name() String
    +description() String
  }
  class StrategyRegistry~S~ {
    -Map~String,S~ strategies
    -S active
    +register(S) StrategyRegistry~S~
    +activate(String) S
    +active() S
    +available() Collection~S~
  }
  StrategyRegistry o-- NamedStrategy

  class PriorityStrategy {
    <<interface>>
    +assess(PriorityFactors) PriorityAssessment
  }
  NamedStrategy <|-- PriorityStrategy
  PriorityStrategy <|.. MatrixPriorityStrategy
  PriorityStrategy <|.. WeightedScorePriorityStrategy
  class PriorityFactors {
    <<record>>
    Level impact
    Level urgency
    Level businessCriticality
    int affectedUsers
  }
  class PriorityAssessment {
    <<record>>
    Priority priority
    double score
    String rationale
  }
  PriorityStrategy ..> PriorityFactors
  PriorityStrategy ..> PriorityAssessment

  class AssignmentStrategy {
    <<interface>>
    +rank(Incident, List~Technician~) List~AssignmentCandidate~
    +onAssigned(Technician) void
  }
  NamedStrategy <|-- AssignmentStrategy
  AssignmentStrategy <|.. SkillBasedStrategy
  AssignmentStrategy <|.. LoadBasedStrategy
  AssignmentStrategy <|.. RoundRobinStrategy
  AssignmentStrategy <|.. PriorityBasedStrategy
  AssignmentStrategy <|.. SmartAssignmentStrategy
  PriorityBasedStrategy *-- LoadBasedStrategy : reuses
  SmartAssignmentStrategy ..> IncidentHistory : learns from
  class TechnicianAssigner {
    -StrategyRegistry~AssignmentStrategy~ strategies
    +recommend(Incident, Collection~Technician~) List~AssignmentCandidate~
    +assign(Incident, Collection~Technician~, User, Instant) AssignmentDecision
  }
  TechnicianAssigner --> StrategyRegistry
```

## 5. Class diagram - Observer pattern, notifications, audit, adapters

```mermaid
classDiagram
  class IncidentEvent {
    <<abstract sealed>>
    -EventHeader header
    +type()* EventType
    +description()* String
  }
  IncidentEvent <|-- IncidentCreated
  IncidentEvent <|-- IncidentClassified
  IncidentEvent <|-- IncidentDetailsUpdated
  IncidentEvent <|-- PriorityChanged
  IncidentEvent <|-- IncidentAssigned
  IncidentEvent <|-- IncidentStatusChanged
  IncidentEvent <|-- NoteAdded
  IncidentEvent <|-- IncidentResolved
  IncidentEvent <|-- IncidentClosed
  IncidentEvent <|-- FeedbackGiven
  IncidentEvent <|-- SlaAlertRaised
  IncidentEvent <|-- IncidentEscalated

  class IncidentEventPublisher {
    -List~IncidentEventListener~ listeners
    +subscribe(IncidentEventListener) IncidentEventPublisher
    +publish(IncidentEvent) int
  }
  class IncidentEventListener {
    <<interface>>
    +onEvent(IncidentEvent) void
  }
  IncidentEventPublisher o-- "0..*" IncidentEventListener
  IncidentEventListener <|.. AuditTrailListener
  IncidentEventListener <|.. NotificationDispatcher
  IncidentEventListener <|.. IncidentHistoryProvider

  class AuditLog {
    <<interface>>
    +append(AuditEntry) AuditEntry
    +search(AuditQuery) List~AuditEntry~
  }
  AuditTrailListener --> AuditLog

  class NotificationDispatcher {
    +dispatch(IncidentEvent) List~DeliveryResult~
  }
  class NotificationPolicy {
    <<interface>>
    +rulesFor(IncidentEvent) List~NotificationRule~
  }
  class NotificationChannel {
    <<interface>>
    +name() String
    +canReach(User) boolean
    +deliver(User, Notification) void
  }
  NotificationDispatcher *-- NotificationPolicy
  NotificationDispatcher *-- NotificationFactory
  NotificationDispatcher o-- "1..*" NotificationChannel
  NotificationPolicy <|.. DefaultNotificationPolicy
  NotificationChannel <|.. InAppChannel
  NotificationChannel <|.. EmailChannel
  NotificationChannel <|.. SmsChannel
  class EmailSender {
    <<interface>>
    +send(String, String, String) void
  }
  class SmsSender {
    <<interface>>
    +send(String, String) void
  }
  EmailChannel --> EmailSender
  SmsChannel --> SmsSender
  EmailSender <|.. MailServiceEmailAdapter
  SmsSender <|.. SmsGatewayAdapter
  MailServiceEmailAdapter --> SimulatedMailService : adapts
  SmsGatewayAdapter --> SimulatedSmsGateway : adapts
```

## 6. Class diagram - SLA and intelligent features

```mermaid
classDiagram
  class SlaPolicy {
    -String name
    -String serviceCode
    -Map~Priority,SlaTarget~ targets
    -double warningThreshold
    +setTarget(Priority, SlaTarget) SlaPolicy
    +targetFor(Priority) SlaTarget
  }
  class SlaPolicyCatalog {
    +policyFor(ItService) SlaPolicy
    +targetFor(ItService, Priority) SlaTarget
  }
  class SlaCalculator {
    +evaluate(Incident) SlaSnapshot
  }
  class SlaMonitor {
    +scan(Collection~Incident~) SlaScanResult
  }
  class SlaAlertLog {
    <<interface>>
    +hasRaised(...) boolean
    +record(SlaAlert) void
  }
  class EscalationPolicy {
    +requiredLevel(Incident, SlaSnapshot) int
  }
  SlaPolicyCatalog o-- "1..*" SlaPolicy
  SlaPolicy *-- "1..4" SlaTarget
  SlaCalculator --> SlaPolicyCatalog
  SlaCalculator ..> SlaSnapshot : produces
  SlaSnapshot *-- "2" SlaMeasure
  SlaMonitor --> SlaCalculator
  SlaMonitor --> SlaAlertLog
  EscalationPolicy ..> SlaSnapshot

  class CategoryClassifier {
    <<interface>>
    +suggest(String, String) List~CategorySuggestion~
  }
  class TextSimilarity {
    <<interface>>
    +similarity(TextProfile, TextProfile) double
  }
  CategoryClassifier <|.. NaiveBayesCategoryClassifier
  TextSimilarity <|.. CosineTextSimilarity
  class DuplicateDetector {
    +findRelated(IncidentFingerprint, Collection~Incident~, Instant) List~RelatedIncident~
  }
  class ResolutionSuggester {
    +suggest(Incident, IncidentHistory) List~ResolutionSuggestion~
  }
  class IncidentCorrelator {
    +correlate(Collection~Incident~) List~IncidentCluster~
  }
  class SlaRiskPredictor {
    +assess(Incident, SlaSnapshot, IncidentHistory, Instant) Optional~SlaRisk~
  }
  class IncidentHistory {
    +resolvedInCategory(UserId, String) int
    +typicalResolutionTime(String, Priority) Optional~Duration~
  }
  DuplicateDetector --> TextSimilarity
  ResolutionSuggester --> TextSimilarity
  ResolutionSuggester ..> IncidentHistory
  SlaRiskPredictor ..> IncidentHistory
  NaiveBayesCategoryClassifier ..> TextAnalyzer
  TextProfile ..> TextAnalyzer
```

## 7. Class diagram - layers, Repository pattern and Memento

```mermaid
classDiagram
  direction LR
  class IncidentController {
    +report(ReportIncident) IncidentDetail
    +search(...) PageView
  }
  class IncidentService {
    +report(UserId, ReportCommand) Incident
    +assign(UserId, IncidentId, UserId) Incident
    +resolve(...) Incident
  }
  class IncidentRepository {
    <<interface>>
    +save(Incident) Incident
    +find(IncidentId) Optional~Incident~
    +search(IncidentQuery) List~Incident~
    +nextId() IncidentId
  }
  class UserRepository {
    <<interface>>
    +save(User) User
    +findTechnicians() List~Technician~
  }
  class UserDirectory {
    <<interface>>
    +find(UserId) Optional~User~
    +withPermission(Permission) List~User~
  }
  UserDirectory <|-- UserRepository
  IncidentController --> IncidentService
  IncidentService --> IncidentRepository
  IncidentService --> UserRepository
  IncidentService --> TechnicianAssigner
  IncidentService --> IncidentEventPublisher
  IncidentRepository <|.. JpaIncidentRepository
  UserRepository <|.. JpaUserRepository
  JpaIncidentRepository ..> IncidentMapper
  IncidentMapper ..> IncidentEntity
  IncidentMapper ..> IncidentSnapshot
  class IncidentSnapshot {
    <<record, Memento>>
  }
  Incident ..> IncidentSnapshot : snapshot() / restore()
  note for IncidentRepository "domain interface - no JPA"
  note for JpaIncidentRepository "infrastructure - Spring Data JPA"
```
