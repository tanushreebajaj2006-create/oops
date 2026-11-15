# Problem Analysis

## 1. The problem

Organisations depend on IT services - payment systems, e-mail, VPN, databases, single sign-on.
When one fails, employees report it, and an IT support team must find out what is wrong, decide how
urgent it is, give it to the right person, fix it and confirm the fix. In a large organisation
this happens hundreds of times a day.

Done by e-mail, phone calls and spreadsheets, this breaks down:

| Symptom | Consequence |
|---|---|
| Reports arrive in many channels with missing details | Time lost asking basic questions; incidents forgotten |
| Urgency is decided by whoever shouts loudest | A payment outage can wait behind a printer jam |
| Work is handed out by habit | Some technicians are overloaded, others idle; specialists get the wrong problems |
| Nobody watches the clock | Service level agreements (SLAs) are broken without anyone noticing in time |
| Ten people report the same outage | Ten tickets, ten technicians, no one sees the bigger picture |
| No history | Recurring problems are not recognised; the same fix is rediscovered again and again |
| No audit trail | Nobody can say who did what, when, or why a deadline was missed |

## 2. Stakeholders

| Stakeholder | Interest | What they need from the system |
|---|---|---|
| **Employee / customer** | Get back to work quickly | Easy reporting, visible progress, answers to questions, a say in whether it is really fixed |
| **Technician / support engineer** | A manageable queue of problems they can solve | Clear assignments matching their skills, all information in one place, suggestions from past fixes |
| **Team lead** | Keep the team effective and deadlines met | Workload overview, warnings before SLAs break, escalations, ability to reassign |
| **Administrator / IT manager** | Reliable service at known cost | Configuration (services, categories, SLAs, rules), reports, audit trail, user management |
| **Business owners of services** | Their service is restored fast | Priority that reflects business criticality; dedicated SLAs (e.g. payments) |
| **Auditors** | Accountability | Complete, unalterable history of every action |

## 3. Existing challenges (and how the system answers them)

| Challenge (from the brief) | Answer in ServiceDesk |
|---|---|
| Delayed response | Automatic classification, prioritisation and assignment the moment an incident is reported; SLA warnings and automatic escalation |
| Incorrect prioritisation | Priority computed from impact × urgency × business criticality (or a weighted formula including people affected), with the reasoning recorded |
| Inefficient technician allocation | Five interchangeable assignment strategies (skill, load, round-robin, priority-aware, smart) respecting capacity and availability |
| SLA violations | Per-service SLA policies, live deadline tracking, warnings at a configurable threshold, recorded violations, predicted breaches |
| Poor visibility into service health | Dashboard: open/critical incidents, SLA compliance, workload per technician, incidents per service and category |
| Duplicate incident reports | Duplicate detection while the user is still typing; correlation of bursts into "possible major incident" |
| Incomplete incident history | Every change is an event; every event is written to an append-only audit log |
| Difficulty identifying recurring problems | Similar past incidents and their fixes are suggested to technicians; category trends in reports |

## 4. Proposed solution

**ServiceDesk** - an Intelligent IT Service Management & Incident Resolution System - is a web
application with a Java/Spring Boot REST backend, a PostgreSQL database and a Next.js frontend.

It covers the full lifecycle from the brief:

> **Report → Classify → Prioritise → Assign → Investigate → Resolve → Verify → Close**

and the 14-step scenario ("The organisation's payment application is unavailable"): the system
creates the incident, classifies it, identifies the service and its business criticality,
calculates priority, applies the right SLA, picks and assigns a technician, notifies everyone
concerned, tracks investigation and resolution, monitors the SLA, records every action, closes the
incident after verification and includes it in reports.

Its core is a **framework-free, object-oriented domain model**: the business rules live in plain
Java classes (incidents, lifecycle states, strategies, policies, events) that know nothing about
web, database or Spring. That is what makes the system maintainable (rules in one place),
extensible (new categories, strategies, notification channels without touching existing code) and
testable (89.7 % line coverage, mostly by fast unit tests).

## 5. Assumptions

1. **One organisation**, one service desk. Multi-tenant hosting is out of scope.
2. **SLA clocks run 24×7** from the moment the incident is reported - no business-hours calendar,
   no pause while waiting for the reporter. Reopening continues the original clock (the customer's
   wait did not reset).
3. **Response** means the assigned technician has acknowledged the incident; **resolution** means a
   fix has been recorded (status RESOLVED).
4. **Impact and urgency** are chosen by the reporter and can be corrected by the reporter or a
   supervisor; business criticality belongs to the service and is set by administrators.
5. **Automatic escalation** applies to P1 and P2 incidents only; lower priorities rely on SLA
   warnings.
6. **E-mail and SMS** delivery is simulated (logged and stored), as the brief allows; the design
   uses adapters so a real provider can be plugged in. In-app notifications are real.
7. **Team leads are technicians** with extra rights (they can still be assigned work). The brief's
   "IT manager" is the administrator role.
8. **Self-registration** creates employees only; technicians, team leads and administrators are
   created by an administrator.
9. **Duplicates are flagged, not blocked** - a second report may contain new information.
10. The intelligent features use **explainable classical algorithms** (Naive Bayes, cosine
    similarity, median-based prediction) rather than an external AI service; they sit behind
    interfaces so an AI service could replace them.
