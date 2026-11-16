"use client";

import { IncidentTable } from "@/components/IncidentTable";
import { Card, ErrorBanner, PageHeader, Spinner, Stat } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import type { IncidentSummary, MyWorkload, Priority, SlaStatus } from "@/lib/types";
import { useCallback, useEffect, useState } from "react";

const SLA_ORDER: Record<SlaStatus, number> = {
  BREACHED: 0,
  AT_RISK: 1,
  ON_TRACK: 2,
  MET: 3,
  NOT_APPLICABLE: 4,
};
const PRIORITY_ORDER: Record<Priority, number> = { P1: 0, P2: 1, P3: 2, P4: 3 };

/** Most urgent first: breached SLA, then at risk, then by priority, then oldest. */
function byUrgency(a: IncidentSummary, b: IncidentSummary): number {
  const sla =
    SLA_ORDER[a.slaStatus ?? "NOT_APPLICABLE"] - SLA_ORDER[b.slaStatus ?? "NOT_APPLICABLE"];
  if (sla !== 0) {
    return sla;
  }
  const p = PRIORITY_ORDER[a.priority ?? "P4"] - PRIORITY_ORDER[b.priority ?? "P4"];
  return p !== 0 ? p : a.createdAt.localeCompare(b.createdAt);
}

/** A technician's own queue: open work, capacity and availability. */
export default function QueuePage() {
  const [data, setData] = useState<MyWorkload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api
      .myWorkload()
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(() => {
    load();
  }, [load]);

  async function toggleAvailability() {
    if (!data) {
      return;
    }
    setBusy(true);
    try {
      await api.setAvailability(!data.available);
      load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  if (!data) {
    return error ? <ErrorBanner message={error} /> : <Spinner />;
  }
  const sorted = [...data.incidents].sort(byUrgency);
  const breached = sorted.filter((i) => i.slaStatus === "BREACHED").length;
  const atRisk = sorted.filter((i) => i.slaStatus === "AT_RISK").length;
  const toAcknowledge = sorted.filter((i) => i.status === "ASSIGNED").length;

  return (
    <>
      <PageHeader
        title="My queue"
        subtitle="Your open incidents, most urgent first."
        actions={
          <button
            type="button"
            className={`btn ${data.available ? "" : "btn-primary"}`}
            disabled={busy}
            onClick={toggleAvailability}
          >
            {data.available ? "Mark me unavailable" : "Mark me available"}
          </button>
        }
      />
      <ErrorBanner message={error} />
      {!data.available && (
        <div className="alert alert-warn">
          You are marked unavailable: no new incidents will be assigned to you.
        </div>
      )}
      <div className="stats">
        <Stat
          label="Open incidents"
          value={`${data.activeIncidents} / ${data.maxWorkload}`}
          tone={data.activeIncidents >= data.maxWorkload ? "warn" : undefined}
        />
        <Stat
          label="To acknowledge"
          value={toAcknowledge}
          tone={toAcknowledge > 0 ? "warn" : undefined}
        />
        <Stat label="SLA at risk" value={atRisk} tone={atRisk > 0 ? "warn" : undefined} />
        <Stat label="SLA breached" value={breached} tone={breached > 0 ? "danger" : "good"} />
      </div>
      <Card title="Open incidents">
        <IncidentTable
          incidents={sorted}
          showAssignee={false}
          empty="Nothing in your queue right now."
        />
      </Card>
    </>
  );
}
