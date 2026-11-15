# Demo Script (recorded video, 15-20 minutes)

The brief asks for a recorded demonstration of at least **three complete workflows**, in which
**every member introduces themselves and explains a module**. Name the file
`<GroupNo>_OOP_S1_2026-2027_Project` (check the exact name with the instructor's template).

## 0. Before recording

1. Start a **fresh** backend so the demo data is new (the payment outage was "just reported"):
   - Docker: `docker compose down -v && docker compose up --build`, **or**
   - No Docker: from `backend/` run
     `./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test`
     then `java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" com.servicedesk.LocalDevApplication`
     (`:` instead of `;` on macOS/Linux).
2. Start the frontend: `cd frontend && bun run dev` → http://localhost:3000
3. Open four browser profiles / private windows, one per role, so you can switch quickly:

   | Role | E-mail | Password |
   |---|---|---|
   | Employee | `asha.rao@servicedesk.example` | see header of `V3__seed_reference_data.sql` |
   | Employee (2nd reporter) | `karan.mehta@servicedesk.example` | same |
   | Technician | `kavya.reddy@servicedesk.example` (Database/Cloud) or `neha.sharma@…` | same |
   | Team lead | `meera.lal@servicedesk.example` | same |
   | Administrator | `admin@servicedesk.example` | same |

4. Wait ~30 s after start-up: the first SLA check runs and escalates the seeded unacknowledged P1
   ("Unable to make payment") - useful for workflow 3.
5. Zoom the browser to 110-125 % so text is readable in the recording.

## 1. Timeline

| Time | Speaker | Content |
|---|---|---|
| 0:00 - 1:30 | Member 1 | Introduction of the team; the problem (manual incident handling, SLA breaches, duplicates); what ServiceDesk does |
| 1:30 - 3:30 | Member 2 | Architecture diagram; layered design; framework-free domain; tech stack and why |
| 3:30 - 7:30 | Member 3 | **Workflow 1** live |
| 7:30 - 11:00 | Member 4 | **Workflow 2** live |
| 11:00 - 14:30 | Member 5 | **Workflow 3** live (intelligent features) |
| 14:30 - 17:00 | Member 2/3 | OOP design: class diagram, State, Strategy (switch strategy live), Observer, Factory, Repository, Adapter |
| 17:00 - 18:30 | Member 4/5 | Testing: 140 tests, coverage report, one test shown; security summary |
| 18:30 - 19:30 | Member 1 | Admin console, conclusions, what we would add next |

Each member says their name and which module they built at the start of their part.

## 2. Workflow 1 - Employee reports → system prioritises → technician assigned → notification

*Shows: Factory, auto-categorisation, Strategy (priority + assignment), SLA, Observer (audit +
notifications).*

1. **Employee (Asha)** → *Report incident*.
2. Type title **"Database reports timing out"**, description "Sales reports from the core
   database time out after 30 seconds since this morning", service **Core Database Cluster**,
   impact **High**, urgency **High**, 40 people. Leave category empty.
3. Point out the **Suggested category** panel (Database, with matched words) - "the system will
   classify it for me".
4. Submit. On the incident page show:
   - status **Assigned**, priority badge, category set automatically;
   - **History**: created → *classified (auto-categorised: … confidence)* → *priority calculated
     (MATRIX: Impact 3 × Urgency 3 × Criticality 4 = 36 → P1)* → *assigned to … (SKILL_BASED:
     DATABASE skill …)*;
   - **SLA** card: response and resolution deadlines from the policy.
5. Switch to the **assigned technician** (shown on the page) → bell shows a new notification →
   open *Notifications*: "… assigned to you", marked urgent for P1.
6. Mention the backend log lines `[SIMULATED EMAIL]` / `[SIMULATED SMS]` (Adapter pattern) - show
   the terminal briefly.

## 3. Workflow 2 - Technician investigates → SLA monitored → resolved → user notified

*Shows: State pattern (only valid buttons), internal notes, information request, SLA meters,
resolution, verification, feedback.*

1. **Technician** → *My queue*: the new incident at the top (most urgent first), workload x/y.
2. Open it → only **Acknowledge** is offered (State pattern). Click it → response SLA turns
   **Met**.
3. **Start investigation** → add an **investigation note (internal)** "Missing index on
   orders.created_at suspected".
4. **Ask reporter** "Which report exactly - daily or monthly?" → status **Awaiting info**.
5. Switch to **employee** → notification "needs more information" → open incident → note: the
   internal note is **not visible** to the employee → **Answer question** "The daily sales report".
6. Back to **technician** → **Resolve** with summary "Re-created the missing index" and root
   cause "Index dropped during migration" → workload drops by one.
7. **Employee** → notification "has been resolved" → **Confirm it is fixed** → give **5★**
   feedback → **Close**. History shows the full trail; SLA resolution **Met**.
8. Optional: try to close a NEW incident to show the rejection message (invalid transition).

## 4. Workflow 3 - Multiple reports → duplicates/correlation → escalation

*Shows: duplicate detection, correlation (major incident), smart recommendation, SLA risk,
automatic escalation, resolution suggestions.*

1. **Employee (Karan)** → *Report incident*, title **"Unable to pay invoices online"**,
   description "The payment page keeps failing at checkout", service **Payment Gateway**.
2. Before submitting, point at **Already reported?**: the three open payment incidents flagged
   **Likely duplicate** with reasons (shared words, same service, time). Explain that the report
   is still allowed (new information may matter).
3. **Team lead (Meera)** → *Dashboard*:
   - **Possible major incidents**: "3 open incidents on Payment Gateway within 15 min - possible
     major incident" (correlation);
   - **Predicted SLA breaches** with reasons (risk prediction);
   - technician workload and service analysis.
4. Open **INC… "Unable to make payment"** (the seeded unacknowledged P1): history shows **SLA
   violated** → **Escalated (level 1) to Team Lead** → reassigned → **Escalated (level 2) to
   Administrator** - automatic escalation by the background SLA monitor.
5. On a payment incident, show **Possibly related** (duplicates) and, as staff, **Suggested fixes**
   from similar resolved incidents.
6. **Triage → Assign**: ranked technician recommendations with reasons.
7. **Administrator** → *Administration → Strategies* → switch assignment to **Smart** → report a
   new incident → its history says "SMART: score … skill … similar fixed …". Switch back.
   (*"The assignment strategy can be changed without redesigning the application."*)
8. *Administration → Audit log*: filter by action `ESCALATED` or by incident.

## 5. Talking points for the OOP section

- **Encapsulation:** `Incident` has no setters; every change checks who/when/what first.
- **Inheritance:** `User → Technician → TeamLead`; 10 state classes; 12 event types.
- **Polymorphism:** `user.can(...)`, `state.resolve()`, `strategy.rank(...)`, `channel.deliver(...)`.
- **Patterns:** State, Strategy, Factory, Observer, Repository, Adapter, Memento (+ template
  method, null object). Show `docs/diagrams/uml.md` class diagrams.
- **Domain isolation:** ArchUnit test fails the build if the domain imports Spring or JPA.

## 6. Backup plan

If something fails live: show the automated tests for that workflow - TC-86 (workflow 1), TC-87
(workflow 2), TC-91 and TC-109 (workflow 3) - run `./mvnw test -Dtest=IncidentWorkflowTest` and
show the green result and the coverage report.
