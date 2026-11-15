"use client";

import { relative } from "@/lib/format";
import type { IncidentSummary } from "@/lib/types";
import Link from "next/link";
import { EmptyState, PriorityBadge, SlaBadge, StatusBadge } from "./ui";

/** Incident list used on the incidents page, the technician queue and the dashboard. */
export function IncidentTable({
  incidents,
  showReporter = true,
  showAssignee = true,
  empty = "No incidents match.",
}: {
  incidents: IncidentSummary[];
  showReporter?: boolean;
  showAssignee?: boolean;
  empty?: string;
}) {
  if (incidents.length === 0) {
    return <EmptyState>{empty}</EmptyState>;
  }
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Incident</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Service</th>
            {showReporter && <th>Reporter</th>}
            {showAssignee && <th>Technician</th>}
            <th>SLA</th>
            <th>Reported</th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((i) => (
            <tr key={i.id}>
              <td>
                <Link href={`/incidents/${i.id}`} className="strong">
                  {i.id}
                </Link>
                <div>{i.title}</div>
                {i.category && <span className="tag">{i.category}</span>}
                {i.escalationLevel > 0 && (
                  <span className="badge sla-breached" title="Escalated">
                    Escalated L{i.escalationLevel}
                  </span>
                )}
              </td>
              <td>
                <PriorityBadge priority={i.priority} />
              </td>
              <td>
                <StatusBadge status={i.status} />
              </td>
              <td>{i.serviceName}</td>
              {showReporter && <td>{i.reporter.name}</td>}
              {showAssignee && (
                <td>{i.assignee ? i.assignee.name : <span className="muted">Unassigned</span>}</td>
              )}
              <td>
                <SlaBadge status={i.slaStatus} />
                {i.resolutionDueAt && i.slaStatus !== "MET" && (
                  <div className="muted small">due {relative(i.resolutionDueAt)}</div>
                )}
              </td>
              <td className="small">{relative(i.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
