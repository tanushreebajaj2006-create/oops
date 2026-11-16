"use client";

import { IncidentTable } from "@/components/IncidentTable";
import { Card, ErrorBanner, Field, PageHeader, Spinner } from "@/components/ui";
import { type IncidentFilters, api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { ALL_STATUSES, PRIORITIES, SLA_STATUSES, humanize, isStaff } from "@/lib/format";
import {
  type Category,
  type IncidentSummary,
  OPEN_STATUSES,
  type Service,
  type User,
} from "@/lib/types";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

const PAGE_SIZE = 20;

interface FormState {
  q: string;
  id: string;
  status: string;
  priority: string;
  service: string;
  category: string;
  technician: string;
  sla: string;
  from: string;
  to: string;
}

const EMPTY: FormState = {
  q: "",
  id: "",
  status: "OPEN",
  priority: "",
  service: "",
  category: "",
  technician: "",
  sla: "",
  from: "",
  to: "",
};

function toFilters(f: FormState, offset: number): IncidentFilters {
  return {
    q: f.q || undefined,
    id: f.id || undefined,
    status: f.status === "OPEN" ? OPEN_STATUSES : f.status ? [f.status] : undefined,
    priority: f.priority ? [f.priority] : undefined,
    service: f.service || undefined,
    category: f.category || undefined,
    technician: f.technician || undefined,
    sla: f.sla || undefined,
    from: f.from ? new Date(`${f.from}T00:00:00`).toISOString() : undefined,
    to: f.to ? new Date(`${f.to}T23:59:59`).toISOString() : undefined,
    limit: PAGE_SIZE,
    offset,
  };
}

/** Search & filter incidents. The server limits results to what the signed-in user may see. */
export default function IncidentsPage() {
  const { user } = useAuth();
  const staff = isStaff(user?.role);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [applied, setApplied] = useState<FormState>(EMPTY);
  const [offset, setOffset] = useState(0);
  const [items, setItems] = useState<IncidentSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [services, setServices] = useState<Service[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [technicians, setTechnicians] = useState<User[]>([]);

  useEffect(() => {
    api
      .services()
      .then(setServices)
      .catch(() => {});
    api
      .categories()
      .then(setCategories)
      .catch(() => {});
    if (user && (user.role === "TEAM_LEAD" || user.role === "ADMINISTRATOR")) {
      api
        .technicians()
        .then(setTechnicians)
        .catch(() => {});
    }
  }, [user]);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    api
      .searchIncidents(toFilters(applied, offset))
      .then((p) => {
        setItems(p.items);
        setTotal(p.total);
      })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [applied, offset]);

  useEffect(() => {
    load();
  }, [load]);

  const set = (key: keyof FormState) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <>
      <PageHeader
        title={user?.role === "EMPLOYEE" ? "My incidents" : "Incidents"}
        subtitle={
          user?.role === "EMPLOYEE"
            ? "Everything you have reported and where it stands."
            : user?.role === "TECHNICIAN"
              ? "Incidents assigned to you."
              : "All incidents in the organisation."
        }
        actions={
          <Link href="/report" className="btn btn-primary">
            Report incident
          </Link>
        }
      />
      <Card>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            setOffset(0);
            setApplied(form);
          }}
        >
          <Field label="Search text" htmlFor="q">
            <input id="q" value={form.q} onChange={set("q")} placeholder="e.g. payment" />
          </Field>
          <Field label="Incident ID" htmlFor="fid">
            <input id="fid" value={form.id} onChange={set("id")} placeholder="INC1001" />
          </Field>
          <Field label="Status" htmlFor="fstatus">
            <select id="fstatus" value={form.status} onChange={set("status")}>
              <option value="OPEN">Open (any)</option>
              <option value="">All statuses</option>
              {ALL_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {humanize(s)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Priority" htmlFor="fpriority">
            <select id="fpriority" value={form.priority} onChange={set("priority")}>
              <option value="">Any</option>
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Service" htmlFor="fservice">
            <select id="fservice" value={form.service} onChange={set("service")}>
              <option value="">Any</option>
              {services.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Category" htmlFor="fcategory">
            <select id="fcategory" value={form.category} onChange={set("category")}>
              <option value="">Any</option>
              {categories.map((c) => (
                <option key={c.code} value={c.code}>
                  {c.name}
                </option>
              ))}
            </select>
          </Field>
          {technicians.length > 0 && (
            <Field label="Technician" htmlFor="ftech">
              <select id="ftech" value={form.technician} onChange={set("technician")}>
                <option value="">Anyone</option>
                {technicians.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.fullName}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Field label="SLA status" htmlFor="fsla">
            <select id="fsla" value={form.sla} onChange={set("sla")}>
              <option value="">Any</option>
              {SLA_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {humanize(s)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Reported from" htmlFor="ffrom">
            <input id="ffrom" type="date" value={form.from} onChange={set("from")} />
          </Field>
          <Field label="Reported to" htmlFor="fto">
            <input id="fto" type="date" value={form.to} onChange={set("to")} />
          </Field>
          <div className="row gap-s">
            <button type="submit" className="btn btn-primary">
              Search
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => {
                setForm(EMPTY);
                setApplied(EMPTY);
                setOffset(0);
              }}
            >
              Reset
            </button>
          </div>
        </form>
      </Card>
      <ErrorBanner message={error} />
      <Card
        title={`${total} incident${total === 1 ? "" : "s"}`}
        actions={
          total > PAGE_SIZE && (
            <>
              <button
                type="button"
                className="btn btn-small"
                disabled={offset === 0}
                onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}
              >
                Previous
              </button>
              <span className="muted small">
                {offset + 1}-{Math.min(total, offset + PAGE_SIZE)} of {total}
              </span>
              <button
                type="button"
                className="btn btn-small"
                disabled={offset + PAGE_SIZE >= total}
                onClick={() => setOffset(offset + PAGE_SIZE)}
              >
                Next
              </button>
            </>
          )
        }
      >
        {loading ? (
          <Spinner />
        ) : (
          <IncidentTable incidents={items} showReporter={staff} showAssignee />
        )}
      </Card>
    </>
  );
}
