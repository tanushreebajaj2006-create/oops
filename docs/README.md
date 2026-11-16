# ServiceDesk Project Documentation

Technical and design documentation for the ServiceDesk Intelligent IT Service Management &
Incident Resolution System. Together these documents form the project report.

## Documentation index

| # | Document | Contents |
|---|---|---|
| 1 | [Problem Analysis](problem-analysis.md) | Problem, stakeholders, challenges, proposed solution, assumptions |
| 2 | [Requirements](requirements.md) | User roles, functional and non-functional requirements, business rules (traceable to code) |
| 3 | [Architecture](architecture.md) | Layers, component and sequence diagrams, deployment, scalability |
| 4 | [UML Diagrams](diagrams/uml.md) | Use case diagram, class diagrams (6), lifecycle state machine |
| 5 | [Design Patterns](design-patterns.md) | OOP principles and the 8 design patterns used, with justification |
| 6 | [Tech Stack Report](tech-stack-report.md) | Technology choices and the brief's 9 questions |
| 7 | [Database](database.md) | ER diagram, schema, design decisions, sample data |
| 8 | [Security](security.md) | Authentication, authorisation, validation, known risks |
| 9 | [Test Cases](test-cases.md) | Test strategy, coverage, documented test cases, full catalogue |
| 10 | [Demo Script](demo-script.md) | Recording plan and the three demonstration workflows |

## Project report (PDF)

- Draft: [report/ServiceDesk-Report.pdf](report/ServiceDesk-Report.pdf)
- Rebuild after editing any document:
  1. Fill in the group number, names and IDs in the `TEAM` block of
     [report/build-report.mjs](report/build-report.mjs).
  2. `node docs/report/build-report.mjs` → writes `docs/report/report.html`.
  3. Open `report.html` in Chrome or Edge (it needs internet once to load the Markdown and diagram
     renderers) and use **Print → Save as PDF** (A4, no headers/footers, background graphics on).

Diagrams are written in Mermaid, so they also render directly on GitHub.
