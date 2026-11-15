"use client";

import {
  BarList,
  Card,
  EmptyState,
  ErrorBanner,
  PageHeader,
  PriorityBadge,
  Spinner,
  Stat,
} from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import {
  ALL_STATUSES,
  PRIORITIES,
  formatDateTime,
  humanize,
  minutes,
  relative,
} from "@/lib/format";
import type { Breakdown, Cluster, Overview, SlaReport, SlaRisk, TechnicianLoad } from "@/lib/types";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

interface DashboardData {
  overview: Overview;
  technicians: TechnicianLoad[];
  services: Breakdown[];
  categories: Breakdown[];
  sla: SlaReport;
  risks: SlaRisk[];
  clusters: Cluster[];
}

const PRIORITY_TONE: Record<string, string> = { P1: "danger", P2: "warn", P3: "", P4: "good" };

/** Operational dashboard for team leads and administrators. */
export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshedAt, setRefreshedAt] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([
      api.overview(),
      api.technicianReport(),
      api.serviceReport(),
      api.categoryReport(),
      api.slaReport(),
      api.slaRisk(),
      api.correlations(),
    ])
      .then(([overview, technicians, services, categories, sla, risks, clusters]) => {
        setData({ overview, technicians, services, categories, sla, risks, clusters });
        setRefreshedAt(new Date().toISOString());
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, 60_000);
    return () => window.clearInterval(timer);
  }, [load]);

  if (!data) {
    return error ? <ErrorBanner message={error} /> : <Spinner label="Loading dashboard…" />;
  }
  const o = data.overview;
  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={`Live view of IT service health · refreshed ${refreshedAt ? formatDateTime(refreshedAt) : ""}`}
        actions={
          <button type="button" className="btn" onClick={load}>
            Refresh
          </button>
        }
      />
      <ErrorBanner message={error} />

      <div className="stats">
        <Stat label="Total incidents" value={o.total} />
        <Stat label="Open" value={o.open} />
        <Stat
          label="Critical open (P1)"
          value={o.criticalOpen}
          tone={o.criticalOpen > 0 ? "danger" : "good"}
        />
        <Stat label="Resolved" value={o.resolved} tone="good" />
        <Stat
          label="SLA violations"
          value={o.slaViolations}
          tone={o.slaViolations > 0 ? "danger" : "good"}
        />
        <Stat label="At risk now" value={o.atRiskNow} tone={o.atRiskNow > 0 ? "warn" : undefined} />
        <Stat
          label="SLA compliance"
          value={o.slaCompliancePercent === null ? "—" : `${o.slaCompliancePercent}%`}
          tone={o.slaCompliancePercent !== null && o.slaCompliancePercent < 90 ? "warn" : "good"}
        />
        <Stat label="Avg. time to resolve" value={minutes(o.averageResolutionMinutes)} />
        <Stat
          label="Avg. rating"
          value={o.averageFeedbackRating === null ? "—" : `${o.averageFeedbackRating}★`}
        />
      </div>

      {data.clusters.length > 0 && (
        <Card title="Possible major incidents">
          <ul className="list">
            {data.clusters.map((c) => (
              <li key={`${c.pattern}-${c.key}-${c.firstReportedAt}`} className="list-item">
                <div className="row space-between">
                  <span className="row gap-s">
                    <PriorityBadge priority={c.highestPriority} />
                    <strong>{c.summary}</strong>
                  </span>
                  <span className="muted small">{humanize(c.pattern)}</span>
                </div>
                <div className="small">
                  {c.incidents.map((i) => (
                    <Link key={i.id} href={`/incidents/${i.id}`} className="tag">
                      {i.id} {i.title}
                    </Link>
                  ))}
                </div>
                {c.commonTerms.length > 0 && (
                  <div className="muted small">Common words: {c.commonTerms.join(", ")}</div>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}

      <div className="grid-2">
        <Card title="Incidents by priority">
          <BarList
            rows={PRIORITIES.map((p) => ({
              key: p,
              label: p,
              value: o.byPriority[p] ?? 0,
              tone: PRIORITY_TONE[p],
            }))}
          />
        </Card>
        <Card title="Incidents by status">
          <BarList
            rows={ALL_STATUSES.filter((s) => (o.byStatus[s] ?? 0) > 0).map((s) => ({
              key: s,
              label: humanize(s),
              value: o.byStatus[s] ?? 0,
            }))}
          />
        </Card>
      </div>

      <Card title="Predicted SLA breaches">
        {data.risks.length === 0 ? (
          <EmptyState>No open incident is predicted to miss its deadline.</EmptyState>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Risk</th>
                  <th>Incident</th>
                  <th>Due</th>
                  <th>Projected</th>
                  <th>Why</th>
                </tr>
              </thead>
              <tbody>
                {data.risks.map((r) => (
                  <tr key={r.incident.id}>
                    <td>
                      <span className={`badge risk-${r.level.toLowerCase()}`}>
                        {humanize(r.level)}
                      </span>
                      <div className="muted small">
                        {r.projectedPercentOfAllowance}% of allowance
                      </div>
                    </td>
                    <td>
                      <Link href={`/incidents/${r.incident.id}`} className="strong">
                        {r.incident.id}
                      </Link>{" "}
                      <PriorityBadge priority={r.incident.priority} />
                      <div>{r.incident.title}</div>
                    </td>
                    <td className="small">{relative(r.dueAt)}</td>
                    <td className="small">{relative(r.projectedResolution)}</td>
                    <td className="small">{r.reasons.join("; ")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <div className="grid-2">
        <Card title="Technician workload">
          <BarList
            max={Math.max(1, ...data.technicians.map((t) => t.maxWorkload))}
            rows={data.technicians.map((t) => ({
              key: t.technician.id,
              label: `${t.technician.name}${t.available ? "" : " (away)"}`,
              value: t.activeIncidents,
              tone:
                t.utilisationPercent >= 100
                  ? "danger"
                  : t.utilisationPercent >= 80
                    ? "warn"
                    : "good",
              hint: `/ ${t.maxWorkload} · ${t.resolvedTotal} resolved`,
            }))}
          />
        </Card>
        <Card title="Service analysis">
          <BarList
            rows={data.services
              .filter((s) => s.total > 0)
              .map((s) => ({
                key: s.code,
                label: s.name,
                value: s.total,
                tone: s.critical > 0 ? "danger" : "",
                hint: `${s.open} open`,
              }))}
          />
        </Card>
      </div>

      <div className="grid-2">
        <Card title="By category">
          <BarList
            rows={data.categories
              .filter((c) => c.total > 0)
              .map((c) => ({ key: c.code, label: c.name, value: c.total, hint: `${c.open} open` }))}
          />
        </Card>
        <Card title="Recent SLA violations">
          {data.sla.recentViolations.length === 0 ? (
            <EmptyState>No SLA violations recorded.</EmptyState>
          ) : (
            <ul className="list">
              {data.sla.recentViolations.slice(0, 8).map((v) => (
                <li key={`${v.incidentId}-${v.kind}`} className="list-item small">
                  <div className="row space-between">
                    <span>
                      <Link href={`/incidents/${v.incidentId}`} className="strong">
                        {v.incidentId}
                      </Link>{" "}
                      <PriorityBadge priority={v.priority} /> {humanize(v.kind)} deadline missed
                    </span>
                    <span className="muted">{relative(v.detectedAt)}</span>
                  </div>
                  <div className="muted">
                    Due {formatDateTime(v.dueAt)}
                    {v.assigneeId ? ` · technician ${v.assigneeId}` : " · unassigned"}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
