// Builds the project report from the Markdown docs.
//
//   node docs/report/build-report.mjs            -> docs/report/report.html
//
// Then print to PDF, e.g. with Microsoft Edge or Chrome (headless):
//   msedge --headless=new --disable-gpu --no-pdf-header-footer --virtual-time-budget=30000 \
//          --print-to-pdf=docs/report/ServiceDesk-Report.pdf docs/report/report.html
// or open report.html in a browser and use Print -> Save as PDF.
//
// No npm dependencies: Markdown and Mermaid are rendered in the browser by pinned CDN scripts.
// Fill in the TEAM section below (group number, names, responsibilities) before printing.

import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const docs = join(here, "..");

const TEAM = {
  group: "[Group No.]",
  members: [
    ["[Name]", "[ID]", "UI / UX - Next.js frontend, role-specific screens"],
    ["[Name]", "[ID]", "Backend / APIs - REST controllers, application services, security"],
    ["[Name]", "[ID]", "Domain model / OOP - incident lifecycle, State, Strategy, Observer, Factory"],
    ["[Name]", "[ID]", "Database / persistence - schema, Flyway migrations, JPA repositories"],
    ["[Name]", "[ID]", "Testing / DevOps / intelligent features - tests, CI, algorithms"],
  ],
  repository: "https://github.com/tanushreebajaj2006-create/oops",
};

/** Chapters in report order: [file, chapter title shown in the table of contents]. */
const CHAPTERS = [
  ["problem-analysis.md", "Problem Analysis"],
  ["requirements.md", "Requirements Specification"],
  ["architecture.md", "System Architecture"],
  ["diagrams/uml.md", "UML Diagrams"],
  ["design-patterns.md", "Object-Oriented Design & Design Patterns"],
  ["tech-stack-report.md", "Technology Selection Report"],
  ["database.md", "Database Design"],
  ["security.md", "Security"],
  ["test-cases.md", "Testing & Validation"],
  ["demo-script.md", "Demonstration Workflows"],
];

const chapters = CHAPTERS.map(([file, title]) => ({
  title,
  markdown: readFileSync(join(docs, file), "utf8"),
}));

const teamRows = TEAM.members
  .map(([name, id, role]) => `<tr><td>${name}</td><td>${id}</td><td>${role}</td></tr>`)
  .join("");

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>ServiceDesk - Project Report</title>
<style>
  @page { size: A4; margin: 18mm 16mm; }
  :root { color-scheme: light; }
  body { font-family: "Segoe UI", Roboto, Arial, sans-serif; font-size: 10.5pt; line-height: 1.45; color: #111827; background: #fff; max-width: 190mm; margin: 0 auto; }
  .cover { height: 250mm; display: flex; flex-direction: column; justify-content: center; page-break-after: always; }
  .cover h1 { font-size: 28pt; margin: 0 0 4mm; color: #1d4ed8; border: none; }
  .cover .sub { font-size: 14pt; color: #374151; margin-bottom: 14mm; }
  .cover table { margin-top: 8mm; }
  .toc { page-break-after: always; }
  .toc ol { font-size: 12pt; line-height: 2; }
  section.chapter { page-break-before: always; }
  h1 { font-size: 20pt; color: #1d4ed8; border-bottom: 2px solid #1d4ed8; padding-bottom: 2mm; }
  h2 { font-size: 14pt; margin-top: 7mm; color: #111827; }
  h3 { font-size: 11.5pt; margin-top: 5mm; }
  table { border-collapse: collapse; width: 100%; margin: 3mm 0; font-size: 9pt; page-break-inside: auto; }
  tr { page-break-inside: avoid; }
  th, td { border: 1px solid #d1d5db; padding: 1.5mm 2mm; vertical-align: top; text-align: left; }
  th { background: #eff6ff; }
  code { font-family: Consolas, monospace; font-size: 8.8pt; background: #f3f4f6; padding: 0 1mm; border-radius: 2px; }
  pre { background: #f3f4f6; padding: 3mm; font-size: 8.5pt; white-space: pre-wrap; page-break-inside: avoid; }
  pre code { background: none; padding: 0; }
  blockquote { border-left: 3px solid #1d4ed8; margin: 3mm 0; padding: 1mm 4mm; color: #374151; background: #f8fafc; }
  .mermaid { text-align: center; margin: 4mm 0; page-break-inside: avoid; }
  .mermaid svg { max-width: 100% !important; height: auto; max-height: 235mm; }
  .error { color: #b91c1c; }
</style>
</head>
<body>
  <div class="cover">
    <div>CS F213 / MAC F212 Object-Oriented Programming · Semester I, 2026-2027</div>
    <h1>ServiceDesk</h1>
    <div class="sub">Intelligent IT Service Management &amp; Incident Resolution System<br/>Project Report</div>
    <div><strong>Group:</strong> ${TEAM.group}</div>
    <table>
      <tr><th>Member</th><th>ID</th><th>Responsibility</th></tr>
      ${teamRows}
    </table>
    <p><strong>Source code:</strong> ${TEAM.repository}</p>
    <p><strong>Date:</strong> ${new Date().toISOString().slice(0, 10)}</p>
  </div>
  <div class="toc">
    <h1>Contents</h1>
    <ol>${chapters.map((c) => `<li>${c.title}</li>`).join("")}</ol>
  </div>
  <div id="chapters"></div>
  <script id="data" type="application/json">${JSON.stringify(chapters).replaceAll("</", "<\\/")}</script>
  <script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
  <script>
    const chapters = JSON.parse(document.getElementById("data").textContent);
    const root = document.getElementById("chapters");
    for (const c of chapters) {
      const section = document.createElement("section");
      section.className = "chapter";
      section.innerHTML = marked.parse(c.markdown);
      // turn \`\`\`mermaid code blocks into diagrams
      for (const code of section.querySelectorAll("code.language-mermaid")) {
        const div = document.createElement("div");
        div.className = "mermaid";
        div.textContent = code.textContent;
        code.parentElement.replaceWith(div);
      }
      root.appendChild(section);
    }
    mermaid.initialize({ startOnLoad: false, theme: "default", securityLevel: "strict" });
    mermaid.run({ querySelector: ".mermaid" }).then(
      () => { document.body.dataset.rendered = "ok"; },
      (e) => { document.body.dataset.rendered = "error"; console.error(e); }
    );
  </script>
</body>
</html>
`;

writeFileSync(join(here, "report.html"), html);
console.log(`Wrote ${join(here, "report.html")} (${chapters.length} chapters)`);
