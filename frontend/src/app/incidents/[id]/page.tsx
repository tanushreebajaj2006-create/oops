"use client";

import {
  Card,
  ErrorBanner,
  Field,
  PageHeader,
  PriorityBadge,
  SlaMeter,
  Spinner,
  StatusBadge,
  SuccessBanner,
} from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import {
  LEVELS,
  PRIORITIES,
  ROLE_LABEL,
  formatDateTime,
  humanize,
  isStaff,
  isSupervisor,
  minutes,
} from "@/lib/format";
import type {
  AuditEntry,
  Candidate,
  Category,
  IncidentAction,
  IncidentDetail,
  Level,
  NoteType,
  Priority,
  RelatedIncident,
  ResolutionSuggestion,
  User,
} from "@/lib/types";
import Link from "next/link";
import { useParams } from "next/navigation";
import { type FormEvent, type ReactNode, useCallback, useEffect, useState } from "react";

type FormName =
  | "requestInfo"
  | "provideInfo"
  | "resolve"
  | "reopen"
  | "close"
  | "cancel"
  | "edit"
  | null;

export default function IncidentPage() {
  const params = useParams<{ id: string }>();
  const incidentId = params.id;
  const { user } = useAuth();
  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [history, setHistory] = useState<AuditEntry[]>([]);
  const [related, setRelated] = useState<RelatedIncident[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const loadExtras = useCallback(() => {
    api
      .history(incidentId)
      .then(setHistory)
      .catch(() => {});
    api
      .related(incidentId)
      .then(setRelated)
      .catch(() => {});
  }, [incidentId]);

  useEffect(() => {
    api
      .getIncident(incidentId)
      .then(setIncident)
      .catch((e) => setError(errorMessage(e)));
    loadExtras();
    if (typeof window !== "undefined" && window.location.search.includes("created=1")) {
      setNotice("Incident submitted. You will be notified as it progresses.");
    }
  }, [incidentId, loadExtras]);

  /** Runs an action, shows the updated incident and refreshes its history. */
  const run = useCallback(
    async (action: () => Promise<IncidentDetail>, success: string) => {
      setBusy(true);
      setError(null);
      setNotice(null);
      try {
        setIncident(await action());
        setNotice(success);
        loadExtras();
        return true;
      } catch (e) {
        setError(errorMessage(e));
        return false;
      } finally {
        setBusy(false);
      }
    },
    [loadExtras]
  );

  if (!incident || !user) {
    return error ? <ErrorBanner message={error} /> : <Spinner />;
  }
  const d = incident;
  const isReporter = user.id === d.reporter.id;
  const isAssignee = d.assignee?.id === user.id;

  return (
    <>
      <PageHeader
        title={`${d.id} · ${d.title}`}
        subtitle={
          <span className="row gap-s">
            <StatusBadge status={d.status} />
            <PriorityBadge priority={d.priority} />
            {d.category && <span className="tag">{d.category}</span>}
            {d.escalationLevel > 0 && (
              <span className="badge sla-breached">
                Escalated to {d.escalationLevel === 1 ? "team lead" : "administrator"}
              </span>
            )}
            <span className="muted small">
              {d.serviceName} · reported by {d.reporter.name} {formatDateTime(d.createdAt)}
            </span>
          </span>
        }
        actions={
          <Link href="/incidents" className="btn btn-ghost">
            ← Back
          </Link>
        }
      />
      <SuccessBanner message={notice} />
      <ErrorBanner message={error} onClose={() => setError(null)} />

      <div className="layout-main-side">
        <div className="col gap-l">
          <Details incident={d} />
          <Conversation incident={d} user={user} isAssignee={isAssignee} busy={busy} run={run} />
          {(d.resolution || d.feedback || d.closure) && <Outcome incident={d} />}
          {isReporter && ["RESOLVED", "VERIFIED", "CLOSED"].includes(d.status) && !d.feedback && (
            <FeedbackForm incidentId={d.id} busy={busy} run={run} />
          )}
          <History entries={history} />
        </div>

        <div className="col gap-l">
          {d.sla && (
            <Card title={`SLA · ${d.sla.policy}`}>
              <SlaMeter
                label={`Response (due ${formatDateTime(d.sla.response.dueAt)})`}
                percent={d.sla.response.percentUsed}
                status={d.sla.response.status}
              />
              <SlaMeter
                label={`Resolution (due ${formatDateTime(d.sla.resolution.dueAt)})`}
                percent={d.sla.resolution.percentUsed}
                status={d.sla.resolution.status}
              />
              {d.sla.resolution.completedAt === null &&
                d.sla.resolution.status !== "NOT_APPLICABLE" && (
                  <p className="small muted">
                    Time left to resolve: {minutes(d.sla.resolution.minutesRemaining)}
                  </p>
                )}
            </Card>
          )}
          <Actions
            incident={d}
            user={user}
            isReporter={isReporter}
            isAssignee={isAssignee}
            busy={busy}
            run={run}
          />
          {isStaff(user.role) && d.allowedActions.length > 0 && (
            <Triage incident={d} user={user} busy={busy} run={run} />
          )}
          {isStaff(user.role) && d.status !== "CLOSED" && d.status !== "CANCELLED" && (
            <Suggestions incidentId={d.id} />
          )}
          <Related items={related} />
        </div>
      </div>
    </>
  );
}

type Run = (action: () => Promise<IncidentDetail>, success: string) => Promise<boolean>;

function Details({ incident: d }: { incident: IncidentDetail }) {
  return (
    <Card title="Details">
      <p className="pre-wrap">{d.description}</p>
      <dl className="kv">
        <dt>Service</dt>
        <dd>
          {d.serviceName}{" "}
          <span className="muted small">(criticality {humanize(d.serviceCriticality)})</span>
        </dd>
        <dt>Impact / urgency</dt>
        <dd>
          {humanize(d.impact)} / {humanize(d.urgency)}
        </dd>
        <dt>People affected</dt>
        <dd>{d.affectedUsers}</dd>
        <dt>Technician</dt>
        <dd>
          {d.assignee ? (
            `${d.assignee.name} (${d.assignee.id})`
          ) : (
            <span className="muted">Not assigned yet</span>
          )}
        </dd>
        <dt>Acknowledged</dt>
        <dd>{formatDateTime(d.acknowledgedAt)}</dd>
        <dt>Resolved</dt>
        <dd>{formatDateTime(d.resolvedAt)}</dd>
        {d.reopenCount > 0 && (
          <>
            <dt>Reopened</dt>
            <dd>{d.reopenCount}×</dd>
          </>
        )}
      </dl>
    </Card>
  );
}

const NOTE_LABEL: Record<NoteType, string> = {
  INVESTIGATION: "Investigation note (internal)",
  INFO_REQUEST: "Question to reporter",
  INFO_RESPONSE: "Reporter's answer",
  COMMENT: "Comment",
};

function Conversation({
  incident: d,
  user,
  isAssignee,
  busy,
  run,
}: { incident: IncidentDetail; user: User; isAssignee: boolean; busy: boolean; run: Run }) {
  const [text, setText] = useState("");
  const [type, setType] = useState<NoteType>("COMMENT");
  const canInvestigate = isAssignee || isSupervisor(user.role);
  const open = !["CLOSED", "CANCELLED"].includes(d.status);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (await run(() => api.addNote(d.id, type, text.trim()), "Note added.")) {
      setText("");
    }
  }

  return (
    <Card title="Conversation & notes">
      {d.notes.length === 0 ? (
        <p className="muted small">No notes yet.</p>
      ) : (
        <ul className="list">
          {d.notes.map((n) => (
            <li
              key={`${n.createdAt}-${n.author.id}-${n.type}`}
              className={`note note-${n.type.toLowerCase()}`}
            >
              <div className="row space-between small muted">
                <span>
                  <strong>{n.author.name}</strong> · {NOTE_LABEL[n.type]}
                </span>
                <span>{formatDateTime(n.createdAt)}</span>
              </div>
              <p className="pre-wrap">{n.text}</p>
            </li>
          ))}
        </ul>
      )}
      {open && (
        <form className="form" onSubmit={submit}>
          <Field label="Add a note" htmlFor="note">
            <textarea
              id="note"
              value={text}
              onChange={(e) => setText(e.target.value)}
              required
              maxLength={4000}
            />
          </Field>
          <div className="row gap-s">
            {canInvestigate && (
              <select
                aria-label="Note type"
                value={type}
                onChange={(e) => setType(e.target.value as NoteType)}
                style={{ width: "auto" }}
              >
                <option value="COMMENT">Comment (visible to reporter)</option>
                <option value="INVESTIGATION">Investigation note (internal)</option>
              </select>
            )}
            <button type="submit" className="btn" disabled={busy || !text.trim()}>
              Add note
            </button>
          </div>
        </form>
      )}
    </Card>
  );
}

function Outcome({ incident: d }: { incident: IncidentDetail }) {
  return (
    <Card title="Outcome">
      {d.resolution && (
        <div>
          <h3>Resolution</h3>
          <p className="pre-wrap">{d.resolution.summary}</p>
          {d.resolution.rootCause && (
            <p className="small muted">Root cause: {d.resolution.rootCause}</p>
          )}
          <p className="small muted">
            By {d.resolution.resolvedBy.name}, {formatDateTime(d.resolution.resolvedAt)}
          </p>
        </div>
      )}
      {d.feedback && (
        <div>
          <h3>Feedback</h3>
          <p aria-label={`${d.feedback.rating} out of 5`}>
            {"★".repeat(d.feedback.rating)}
            {"☆".repeat(5 - d.feedback.rating)} {d.feedback.comment}
          </p>
        </div>
      )}
      {d.closure && (
        <div>
          <h3>{d.status === "CANCELLED" ? "Cancelled" : "Closed"}</h3>
          <p className="small muted">
            By {d.closure.closedBy.name}, {formatDateTime(d.closure.closedAt)}
            {d.closure.note ? ` - ${d.closure.note}` : ""}
          </p>
        </div>
      )}
    </Card>
  );
}

function FeedbackForm({ incidentId, busy, run }: { incidentId: string; busy: boolean; run: Run }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  return (
    <Card title="How did we do?">
      <form
        className="form"
        onSubmit={(e) => {
          e.preventDefault();
          run(
            () => api.feedback(incidentId, rating, comment.trim()),
            "Thank you for your feedback."
          );
        }}
      >
        <Field label="Rating" htmlFor="fbrating">
          <select id="fbrating" value={rating} onChange={(e) => setRating(Number(e.target.value))}>
            {[5, 4, 3, 2, 1].map((n) => (
              <option key={n} value={n}>
                {"★".repeat(n)}
                {"☆".repeat(5 - n)} ({n}/5)
              </option>
            ))}
          </select>
        </Field>
        <Field label="Comment (optional)" htmlFor="fbcomment">
          <input
            id="fbcomment"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            maxLength={1000}
          />
        </Field>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          Send feedback
        </button>
      </form>
    </Card>
  );
}

function History({ entries }: { entries: AuditEntry[] }) {
  return (
    <Card title="History (audit trail)">
      {entries.length === 0 ? (
        <p className="muted small">No history.</p>
      ) : (
        <ol className="timeline">
          {entries.map((e) => (
            <li key={e.seq}>
              <span className="mono">{formatDateTime(e.timestamp)}</span> - {e.description}
              <div className="muted small">
                {e.actorId === "SYSTEM" ? "Automation" : `${ROLE_LABEL[e.actorRole]} ${e.actorId}`}
                {e.previousStatus && e.newStatus && e.previousStatus !== e.newStatus
                  ? ` · ${humanize(e.previousStatus)} → ${humanize(e.newStatus)}`
                  : ""}
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

/** Lifecycle buttons: only those the state allows AND this user may perform. */
function Actions({
  incident: d,
  user,
  isReporter,
  isAssignee,
  busy,
  run,
}: {
  incident: IncidentDetail;
  user: User;
  isReporter: boolean;
  isAssignee: boolean;
  busy: boolean;
  run: Run;
}) {
  const [form, setForm] = useState<FormName>(null);
  const [text, setText] = useState("");
  const [rootCause, setRootCause] = useState("");
  const can = (a: IncidentAction) => d.allowedActions.includes(a);
  const admin = user.role === "ADMINISTRATOR";
  const supervisor = isSupervisor(user.role);
  const open = !["RESOLVED", "VERIFIED", "CLOSED", "CANCELLED"].includes(d.status);

  const buttons: ReactNode[] = [];
  const add = (key: string, label: string, onClick: () => void, primary = false) =>
    buttons.push(
      <button
        key={key}
        type="button"
        className={`btn ${primary ? "btn-primary" : ""}`}
        disabled={busy}
        onClick={onClick}
      >
        {label}
      </button>
    );
  const openForm = (f: FormName) => {
    setText("");
    setRootCause("");
    setForm(form === f ? null : f);
  };

  if (isAssignee && can("ACKNOWLEDGE")) {
    add(
      "ack",
      "Acknowledge",
      () => run(() => api.acknowledge(d.id), "Incident acknowledged."),
      true
    );
  }
  if (isAssignee && can("START_WORK")) {
    add(
      "start",
      "Start investigation",
      () => run(() => api.start(d.id), "Investigation started."),
      true
    );
  }
  if (isAssignee && can("REQUEST_INFO")) {
    add("ask", "Ask reporter", () => openForm("requestInfo"));
  }
  if (isAssignee && can("RESOLVE")) {
    add("resolve", "Resolve", () => openForm("resolve"), true);
  }
  if (isReporter && can("PROVIDE_INFO")) {
    add("answer", "Answer question", () => openForm("provideInfo"), true);
  }
  if ((isReporter || admin) && can("VERIFY")) {
    add(
      "verify",
      "Confirm it is fixed",
      () => run(() => api.verify(d.id), "Resolution confirmed."),
      true
    );
  }
  if ((isReporter || admin) && can("REOPEN")) {
    add("reopen", "Not fixed - reopen", () => openForm("reopen"));
  }
  if ((isReporter || admin) && can("CLOSE")) {
    add("close", "Close", () => openForm("close"));
  }
  if ((isReporter || supervisor) && can("CANCEL")) {
    add("cancel", "Cancel incident", () => openForm("cancel"));
  }
  if ((isReporter || supervisor) && open) {
    add("edit", "Edit details", () => openForm("edit"));
  }

  if (buttons.length === 0) {
    return null;
  }

  const textForm = (
    label: string,
    submitLabel: string,
    action: () => Promise<IncidentDetail>,
    success: string,
    required = true
  ) => (
    <form
      className="form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (await run(action, success)) {
          setForm(null);
        }
      }}
    >
      <Field label={label} htmlFor="actiontext">
        <textarea
          id="actiontext"
          value={text}
          onChange={(e) => setText(e.target.value)}
          required={required}
          maxLength={2000}
        />
      </Field>
      {form === "resolve" && (
        <Field label="Root cause (optional)" htmlFor="rootcause">
          <input
            id="rootcause"
            value={rootCause}
            onChange={(e) => setRootCause(e.target.value)}
            maxLength={2000}
          />
        </Field>
      )}
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {submitLabel}
      </button>
    </form>
  );

  return (
    <Card title="Actions">
      <div className="row gap-s">{buttons}</div>
      {form === "requestInfo" &&
        textForm(
          "Question for the reporter",
          "Send question",
          () => api.requestInfo(d.id, text.trim()),
          "Question sent to the reporter."
        )}
      {form === "provideInfo" &&
        textForm(
          "Your answer",
          "Send answer",
          () => api.provideInfo(d.id, text.trim()),
          "Answer sent to the technician."
        )}
      {form === "resolve" &&
        textForm(
          "What fixed it?",
          "Mark resolved",
          () => api.resolve(d.id, text.trim(), rootCause.trim()),
          "Incident resolved."
        )}
      {form === "reopen" &&
        textForm(
          "What is still wrong?",
          "Reopen",
          () => api.reopen(d.id, text.trim()),
          "Incident reopened."
        )}
      {form === "close" &&
        textForm(
          "Closing note (optional)",
          "Close incident",
          () => api.close(d.id, text.trim()),
          "Incident closed.",
          false
        )}
      {form === "cancel" &&
        textForm(
          "Why cancel?",
          "Cancel incident",
          () => api.cancel(d.id, text.trim()),
          "Incident cancelled."
        )}
      {form === "edit" && (
        <EditForm incident={d} busy={busy} run={run} onDone={() => setForm(null)} />
      )}
    </Card>
  );
}

function EditForm({
  incident: d,
  busy,
  run,
  onDone,
}: { incident: IncidentDetail; busy: boolean; run: Run; onDone: () => void }) {
  const [title, setTitle] = useState(d.title);
  const [description, setDescription] = useState(d.description);
  const [impact, setImpact] = useState<Level>(d.impact);
  const [urgency, setUrgency] = useState<Level>(d.urgency);
  const [affected, setAffected] = useState(d.affectedUsers);
  return (
    <form
      className="form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (
          await run(
            () =>
              api.update(d.id, { title, description, impact, urgency, affectedUsers: affected }),
            "Details updated (priority recalculated if impact or urgency changed)."
          )
        ) {
          onDone();
        }
      }}
    >
      <Field label="Title" htmlFor="etitle">
        <input
          id="etitle"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
          minLength={5}
          maxLength={120}
        />
      </Field>
      <Field label="Description" htmlFor="edesc">
        <textarea
          id="edesc"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
          minLength={10}
        />
      </Field>
      <div className="grid-3">
        <Field label="Impact" htmlFor="eimpact">
          <select id="eimpact" value={impact} onChange={(e) => setImpact(e.target.value as Level)}>
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                {humanize(l)}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Urgency" htmlFor="eurgency">
          <select
            id="eurgency"
            value={urgency}
            onChange={(e) => setUrgency(e.target.value as Level)}
          >
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                {humanize(l)}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Affected" htmlFor="eaffected">
          <input
            id="eaffected"
            type="number"
            min={1}
            value={affected}
            onChange={(e) => setAffected(Math.max(1, Number(e.target.value) || 1))}
          />
        </Field>
      </div>
      <button type="submit" className="btn btn-primary" disabled={busy}>
        Save changes
      </button>
    </form>
  );
}

/** Classification, priority override and assignment for support staff. */
function Triage({
  incident: d,
  user,
  busy,
  run,
}: { incident: IncidentDetail; user: User; busy: boolean; run: Run }) {
  const supervisor = isSupervisor(user.role);
  const [categories, setCategories] = useState<Category[]>([]);
  const [category, setCategory] = useState(d.category ?? "");
  const [priority, setPriority] = useState<Priority | "">(d.priority ?? "");
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [tech, setTech] = useState("");
  const canAssign =
    supervisor && d.allowedActions.includes("ASSIGN") && d.category !== null && d.priority !== null;

  useEffect(() => {
    api
      .categories()
      .then((c) => setCategories(c.filter((x) => x.active)))
      .catch(() => {});
  }, []);
  useEffect(() => {
    if (canAssign) {
      api
        .recommendations(d.id)
        .then(setCandidates)
        .catch(() => setCandidates([]));
    }
  }, [canAssign, d.id]);

  return (
    <Card title="Triage">
      {d.allowedActions.includes("CLASSIFY") && (
        <div className="row gap-s">
          <select
            aria-label="Category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            style={{ flex: 1 }}
          >
            <option value="">Choose category…</option>
            {categories.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="btn"
            disabled={busy || !category}
            onClick={() =>
              run(
                () => api.classify(d.id, category),
                "Classified - priority and assignment updated."
              )
            }
          >
            Classify
          </button>
        </div>
      )}
      {d.priority !== null &&
        !["RESOLVED", "VERIFIED", "CLOSED", "CANCELLED"].includes(d.status) && (
          <div className="row gap-s">
            <select
              aria-label="Priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value as Priority)}
              style={{ flex: 1 }}
            >
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn"
              disabled={busy || !priority || priority === d.priority}
              onClick={() =>
                priority &&
                run(() => api.setPriority(d.id, priority), `Priority set to ${priority}.`)
              }
            >
              Override priority
            </button>
          </div>
        )}
      {canAssign && (
        <div className="col gap-s">
          <h3>Assign</h3>
          {candidates.length > 0 ? (
            <ul className="list">
              {candidates.slice(0, 4).map((c, idx) => (
                <li key={c.technician.id} className="list-item small">
                  <div className="row space-between">
                    <strong>
                      {idx === 0 ? "★ " : ""}
                      {c.technician.name}
                    </strong>
                    <button
                      type="button"
                      className="btn btn-small"
                      disabled={busy || d.assignee?.id === c.technician.id}
                      onClick={() =>
                        run(
                          () => api.assign(d.id, c.technician.id),
                          `Assigned to ${c.technician.name}.`
                        )
                      }
                    >
                      Assign
                    </button>
                  </div>
                  <div className="muted">{c.reason}</div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted small">No technician is currently free for this incident.</p>
          )}
          <div className="row gap-s">
            <input
              aria-label="Technician id"
              placeholder="Technician id, e.g. T203"
              value={tech}
              onChange={(e) => setTech(e.target.value)}
              style={{ flex: 1 }}
            />
            <button
              type="button"
              className="btn"
              disabled={busy || !tech.trim()}
              onClick={() =>
                run(() => api.assign(d.id, tech.trim()), `Assigned to ${tech.trim()}.`)
              }
            >
              Assign
            </button>
          </div>
          <button
            type="button"
            className="btn"
            disabled={busy}
            onClick={() => run(() => api.assign(d.id, null), "Assigned by the active strategy.")}
          >
            Auto-assign (active strategy)
          </button>
        </div>
      )}
    </Card>
  );
}

function Suggestions({ incidentId }: { incidentId: string }) {
  const [items, setItems] = useState<ResolutionSuggestion[] | null>(null);
  useEffect(() => {
    api
      .resolutionSuggestions(incidentId)
      .then(setItems)
      .catch(() => setItems([]));
  }, [incidentId]);
  if (!items || items.length === 0) {
    return null;
  }
  return (
    <Card title="Suggested fixes">
      <ul className="list">
        {items.map((s) => (
          <li key={s.sourceIncidentId} className="list-item small">
            <strong>{s.summary}</strong>
            {s.rootCause && <div className="muted">Root cause: {s.rootCause}</div>}
            <div className="muted">
              From <Link href={`/incidents/${s.sourceIncidentId}`}>{s.sourceIncidentId}</Link> “
              {s.sourceTitle}” · {s.scorePercent}% similar
              {s.rating ? ` · rated ${s.rating}★` : ""}
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Related({ items }: { items: RelatedIncident[] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <Card title="Possibly related">
      <ul className="list">
        {items.map((r) => (
          <li key={r.id} className="list-item small">
            <div className="row space-between">
              <Link href={`/incidents/${r.id}`} className="strong">
                {r.id}
              </Link>
              <span
                className={`badge ${r.verdict === "LIKELY_DUPLICATE" ? "sla-breached" : "sla-at_risk"}`}
              >
                {r.verdict === "LIKELY_DUPLICATE" ? "Likely duplicate" : "Related"} {r.scorePercent}
                %
              </span>
            </div>
            <div>{r.title}</div>
            <div className="muted">{r.reasons.join("; ")}</div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
