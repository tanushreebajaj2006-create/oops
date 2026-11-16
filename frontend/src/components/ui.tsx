"use client";

import { PRIORITY_LABEL, humanize } from "@/lib/format";
import type { IncidentStatus, Priority, SlaStatus } from "@/lib/types";
import type { ReactNode } from "react";

/** Small presentational building blocks shared by every page. */

export function Card({
  title,
  actions,
  children,
  className,
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`card ${className ?? ""}`}>
      {(title || actions) && (
        <header className="card-header">
          {title && <h2 className="card-title">{title}</h2>}
          {actions && <div className="row gap-s">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="row gap-s">{actions}</div>}
    </div>
  );
}

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return <span className={`badge status-${status.toLowerCase()}`}>{humanize(status)}</span>;
}

export function PriorityBadge({ priority }: { priority: Priority | null }) {
  if (!priority) {
    return <span className="badge muted-badge">Not prioritised</span>;
  }
  return (
    <span className={`badge priority-${priority.toLowerCase()}`} title={PRIORITY_LABEL[priority]}>
      {priority}
    </span>
  );
}

export function SlaBadge({ status }: { status: SlaStatus | null }) {
  if (!status) {
    return <span className="muted">—</span>;
  }
  return <span className={`badge sla-${status.toLowerCase()}`}>{humanize(status)}</span>;
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return (
    <output className="spinner">
      <span className="spinner-dot" aria-hidden="true" />
      {label}
    </output>
  );
}

export function ErrorBanner({
  message,
  onClose,
}: { message: string | null; onClose?: () => void }) {
  if (!message) {
    return null;
  }
  return (
    <div className="alert alert-error" role="alert">
      <span>{message}</span>
      {onClose && (
        <button type="button" className="link-button" onClick={onClose}>
          Dismiss
        </button>
      )}
    </div>
  );
}

export function SuccessBanner({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return <output className="alert alert-success">{message}</output>;
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Stat({
  label,
  value,
  tone,
}: { label: string; value: ReactNode; tone?: "danger" | "warn" | "good" }) {
  return (
    <div className={`stat ${tone ? `stat-${tone}` : ""}`}>
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

/** Horizontal bar chart made of plain divs: no chart library needed. */
export function BarList({
  rows,
  max,
  unit = "",
}: {
  rows: { key: string; label: ReactNode; value: number; tone?: string; hint?: ReactNode }[];
  max?: number;
  unit?: string;
}) {
  const top = max ?? Math.max(1, ...rows.map((r) => r.value));
  return (
    <ul className="bars">
      {rows.map((r) => (
        <li key={r.key} className="bar-row">
          <span className="bar-label">{r.label}</span>
          <span className="bar-track">
            <span
              className={`bar-fill ${r.tone ?? ""}`}
              style={{ width: `${Math.min(100, (r.value / top) * 100)}%` }}
            />
          </span>
          <span className="bar-value">
            {r.value}
            {unit}
            {r.hint && <span className="muted small"> {r.hint}</span>}
          </span>
        </li>
      ))}
    </ul>
  );
}

/** Progress meter for an SLA clock. */
export function SlaMeter({
  label,
  percent,
  status,
}: { label: string; percent: number; status: SlaStatus }) {
  return (
    <div className="sla-meter">
      <div className="row space-between small">
        <span>{label}</span>
        <SlaBadge status={status} />
      </div>
      <div className="bar-track">
        <span
          className={`bar-fill sla-fill-${status.toLowerCase()}`}
          style={{ width: `${Math.min(100, Math.max(2, percent))}%` }}
        />
      </div>
    </div>
  );
}

export function Field({
  label,
  htmlFor,
  hint,
  children,
}: { label: string; htmlFor: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {hint && <span className="hint">{hint}</span>}
    </div>
  );
}
