"use client";

import { Card, ErrorBanner, Field, PageHeader, Spinner, SuccessBanner } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { LEVELS, PRIORITIES, ROLE_LABEL, formatDateTime, humanize } from "@/lib/format";
import type {
  AuditEntry,
  Category,
  Level,
  Priority,
  Role,
  Service,
  SlaPolicy,
  Strategies,
  User,
} from "@/lib/types";
import Link from "next/link";
import { type FormEvent, useCallback, useEffect, useState } from "react";

type Tab = "users" | "catalog" | "sla" | "strategies" | "audit";
const TABS: { key: Tab; label: string }[] = [
  { key: "users", label: "Users & technicians" },
  { key: "catalog", label: "Services & categories" },
  { key: "sla", label: "SLA policies" },
  { key: "strategies", label: "Strategies" },
  { key: "audit", label: "Audit log" },
];

/** Administrator / IT manager console. */
export default function AdminPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>("users");
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (user?.role !== "ADMINISTRATOR") {
    return <ErrorBanner message="Only administrators can open this page." />;
  }

  async function runSlaCheck() {
    setError(null);
    try {
      const r = await api.runSlaCheck();
      setNotice(
        `SLA check: ${r.incidentsChecked} open incidents checked, ${r.warnings} warnings, ${r.violations} violations, ${r.escalations} escalations.`
      );
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const feedback = { ok: setNotice, fail: (e: unknown) => setError(errorMessage(e)) };
  return (
    <>
      <PageHeader
        title="Administration"
        subtitle="Configure the service desk. Every change is recorded in the audit log."
        actions={
          <button type="button" className="btn" onClick={runSlaCheck}>
            Run SLA check now
          </button>
        }
      />
      <SuccessBanner message={notice} />
      <ErrorBanner message={error} onClose={() => setError(null)} />
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            className={`tab ${tab === t.key ? "active" : ""}`}
            onClick={() => {
              setTab(t.key);
              setNotice(null);
              setError(null);
            }}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === "users" && <UsersTab {...feedback} />}
      {tab === "catalog" && <CatalogTab {...feedback} />}
      {tab === "sla" && <SlaTab {...feedback} />}
      {tab === "strategies" && <StrategiesTab {...feedback} />}
      {tab === "audit" && <AuditTab fail={feedback.fail} />}
    </>
  );
}

interface Feedback {
  ok: (message: string) => void;
  fail: (e: unknown) => void;
}

// ---------------------------------------------------------------------------------------

function parseSkills(text: string): Record<string, number> {
  const skills: Record<string, number> = {};
  for (const part of text.split(",")) {
    const [code, level] = part.split(":").map((s) => s.trim());
    if (code && level) {
      skills[code.toUpperCase()] = Number(level);
    }
  }
  return skills;
}

function UsersTab({ ok, fail }: Feedback) {
  const [users, setUsers] = useState<User[] | null>(null);
  const [role, setRole] = useState<Role>("TECHNICIAN");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [department, setDepartment] = useState("");
  const [phone, setPhone] = useState("");
  const [skills, setSkills] = useState("NETWORK:3");
  const [experience, setExperience] = useState(1);
  const [maxWorkload, setMaxWorkload] = useState(5);

  const load = useCallback(() => {
    api.users().then(setUsers).catch(fail);
  }, [fail]);
  useEffect(() => {
    load();
  }, [load]);

  const tech = role === "TECHNICIAN" || role === "TEAM_LEAD";
  async function create(e: FormEvent) {
    e.preventDefault();
    try {
      const u = await api.createUser({
        role,
        fullName,
        email,
        password,
        department: role === "EMPLOYEE" ? department : null,
        phone: phone || null,
        skills: tech ? parseSkills(skills) : undefined,
        experienceYears: tech ? experience : undefined,
        maxWorkload: tech ? maxWorkload : undefined,
      });
      ok(`Created ${ROLE_LABEL[u.role]} ${u.id}.`);
      setFullName("");
      setEmail("");
      setPassword("");
      load();
    } catch (err) {
      fail(err);
    }
  }

  async function update(u: User, body: Parameters<typeof api.updateUser>[1], message: string) {
    try {
      await api.updateUser(u.id, body);
      ok(message);
      load();
    } catch (err) {
      fail(err);
    }
  }

  return (
    <div className="col gap-l">
      <Card title="Users">
        {!users ? (
          <Spinner />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th>Skills</th>
                  <th>Workload</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.fullName}</strong> <span className="muted small">{u.id}</span>
                      <div className="muted small">{u.email}</div>
                    </td>
                    <td>{ROLE_LABEL[u.role]}</td>
                    <td className="small">
                      {u.skills
                        ? Object.entries(u.skills).map(([k, v]) => (
                            <span key={k} className="tag">
                              {k} {v}/5
                            </span>
                          ))
                        : (u.department ?? "—")}
                    </td>
                    <td className="small">
                      {u.maxWorkload !== null ? (
                        <span className="row gap-s">
                          {u.activeWorkload} /
                          <select
                            aria-label={`Max workload for ${u.fullName}`}
                            value={u.maxWorkload}
                            style={{ width: "auto" }}
                            onChange={(e) =>
                              update(
                                u,
                                { maxWorkload: Number(e.target.value) },
                                `Max workload of ${u.id} set to ${e.target.value}.`
                              )
                            }
                          >
                            {[1, 2, 3, 4, 5, 6, 8, 10].map((n) => (
                              <option key={n} value={n}>
                                {n}
                              </option>
                            ))}
                          </select>
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="small">
                      <span className={`badge ${u.active ? "sla-met" : "sla-breached"}`}>
                        {u.active ? "Active" : "Deactivated"}
                      </span>
                      {u.available === false && <span className="badge sla-at_risk">Away</span>}
                    </td>
                    <td>
                      <div className="row gap-s">
                        <button
                          type="button"
                          className="btn btn-small"
                          onClick={() =>
                            update(
                              u,
                              { active: !u.active },
                              `${u.id} ${u.active ? "deactivated" : "activated"}.`
                            )
                          }
                        >
                          {u.active ? "Deactivate" : "Activate"}
                        </button>
                        {u.available !== null && (
                          <button
                            type="button"
                            className="btn btn-small"
                            onClick={() =>
                              update(
                                u,
                                { available: !u.available },
                                `${u.id} marked ${u.available ? "away" : "available"}.`
                              )
                            }
                          >
                            {u.available ? "Mark away" : "Mark available"}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <Card title="Add a user">
        <form className="form" onSubmit={create}>
          <div className="grid-3">
            <Field label="Role" htmlFor="nrole">
              <select id="nrole" value={role} onChange={(e) => setRole(e.target.value as Role)}>
                {(["EMPLOYEE", "TECHNICIAN", "TEAM_LEAD", "ADMINISTRATOR"] as Role[]).map((r) => (
                  <option key={r} value={r}>
                    {ROLE_LABEL[r]}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Full name" htmlFor="nname">
              <input
                id="nname"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
              />
            </Field>
            <Field label="E-mail" htmlFor="nemail">
              <input
                id="nemail"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </Field>
            <Field
              label="Initial password"
              htmlFor="npass"
              hint="8+ characters with a letter and a digit"
            >
              <input
                id="npass"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
            </Field>
            <Field label="Phone (for SMS)" htmlFor="nphone">
              <input
                id="nphone"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="+919800000000"
              />
            </Field>
            {role === "EMPLOYEE" && (
              <Field label="Department" htmlFor="ndept">
                <input
                  id="ndept"
                  value={department}
                  onChange={(e) => setDepartment(e.target.value)}
                />
              </Field>
            )}
            {tech && (
              <>
                <Field
                  label="Skills"
                  htmlFor="nskills"
                  hint="CATEGORY:level, e.g. NETWORK:4, SECURITY:2"
                >
                  <input id="nskills" value={skills} onChange={(e) => setSkills(e.target.value)} />
                </Field>
                <Field label="Years of experience" htmlFor="nexp">
                  <input
                    id="nexp"
                    type="number"
                    min={0}
                    max={60}
                    value={experience}
                    onChange={(e) => setExperience(Number(e.target.value))}
                  />
                </Field>
                <Field label="Max open incidents" htmlFor="nmax">
                  <input
                    id="nmax"
                    type="number"
                    min={1}
                    max={50}
                    value={maxWorkload}
                    onChange={(e) => setMaxWorkload(Number(e.target.value))}
                  />
                </Field>
              </>
            )}
          </div>
          <div>
            <button type="submit" className="btn btn-primary">
              Create user
            </button>
          </div>
        </form>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------------------

function CatalogTab({ ok, fail }: Feedback) {
  const [services, setServices] = useState<Service[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [sCode, setSCode] = useState("");
  const [sName, setSName] = useState("");
  const [sCrit, setSCrit] = useState<Level>("MEDIUM");
  const [cCode, setCCode] = useState("");
  const [cName, setCName] = useState("");

  const load = useCallback(() => {
    api.services().then(setServices).catch(fail);
    api.categories().then(setCategories).catch(fail);
  }, [fail]);
  useEffect(() => {
    load();
  }, [load]);

  async function saveService(
    code: string,
    name: string,
    criticality: Level,
    active: boolean,
    message: string
  ) {
    try {
      await api.saveService(code, name, criticality, active);
      ok(message);
      load();
    } catch (e) {
      fail(e);
    }
  }
  async function saveCategory(code: string, name: string, active: boolean, message: string) {
    try {
      await api.saveCategory(code, name, active);
      ok(message);
      load();
    } catch (e) {
      fail(e);
    }
  }

  return (
    <div className="grid-2">
      <Card title="IT services">
        <table>
          <thead>
            <tr>
              <th>Service</th>
              <th>Criticality</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {services.map((s) => (
              <tr key={s.code}>
                <td>
                  {s.name} <span className="muted small">{s.code}</span>
                  {!s.active && <span className="badge muted-badge">retired</span>}
                </td>
                <td>
                  <select
                    aria-label={`Criticality of ${s.name}`}
                    value={s.criticality}
                    onChange={(e) =>
                      saveService(
                        s.code,
                        s.name,
                        e.target.value as Level,
                        s.active,
                        `${s.code} criticality updated.`
                      )
                    }
                  >
                    {LEVELS.map((l) => (
                      <option key={l} value={l}>
                        {humanize(l)}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <button
                    type="button"
                    className="btn btn-small"
                    onClick={() =>
                      saveService(
                        s.code,
                        s.name,
                        s.criticality,
                        !s.active,
                        `${s.code} ${s.active ? "retired" : "re-activated"}.`
                      )
                    }
                  >
                    {s.active ? "Retire" : "Restore"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <form
          className="row gap-s"
          onSubmit={(e) => {
            e.preventDefault();
            saveService(
              sCode.trim().toUpperCase(),
              sName.trim(),
              sCrit,
              true,
              `Service ${sCode.toUpperCase()} added.`
            );
            setSCode("");
            setSName("");
          }}
        >
          <input
            aria-label="New service code"
            placeholder="CODE"
            value={sCode}
            onChange={(e) => setSCode(e.target.value)}
            required
            style={{ width: "8rem" }}
          />
          <input
            aria-label="New service name"
            placeholder="Service name"
            value={sName}
            onChange={(e) => setSName(e.target.value)}
            required
            style={{ flex: 1 }}
          />
          <select
            aria-label="New service criticality"
            value={sCrit}
            onChange={(e) => setSCrit(e.target.value as Level)}
            style={{ width: "auto" }}
          >
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                {humanize(l)}
              </option>
            ))}
          </select>
          <button type="submit" className="btn">
            Add
          </button>
        </form>
      </Card>
      <Card title="Incident categories">
        <p className="muted small">
          New categories work immediately - no code change. The auto-categoriser learns them from
          resolved incidents.
        </p>
        <table>
          <tbody>
            {categories.map((c) => (
              <tr key={c.code}>
                <td>
                  {c.name} <span className="muted small">{c.code}</span>
                  {!c.active && <span className="badge muted-badge">retired</span>}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn btn-small"
                    onClick={() =>
                      saveCategory(
                        c.code,
                        c.name,
                        !c.active,
                        `${c.code} ${c.active ? "retired" : "re-activated"}.`
                      )
                    }
                  >
                    {c.active ? "Retire" : "Restore"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <form
          className="row gap-s"
          onSubmit={(e) => {
            e.preventDefault();
            saveCategory(
              cCode.trim().toUpperCase(),
              cName.trim(),
              true,
              `Category ${cCode.toUpperCase()} added.`
            );
            setCCode("");
            setCName("");
          }}
        >
          <input
            aria-label="New category code"
            placeholder="CODE"
            value={cCode}
            onChange={(e) => setCCode(e.target.value)}
            required
            style={{ width: "8rem" }}
          />
          <input
            aria-label="New category name"
            placeholder="Category name"
            value={cName}
            onChange={(e) => setCName(e.target.value)}
            required
            style={{ flex: 1 }}
          />
          <button type="submit" className="btn">
            Add
          </button>
        </form>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------------------

type TargetForm = Record<Priority, { response: string; resolution: string }>;

function SlaTab({ ok, fail }: Feedback) {
  const [policies, setPolicies] = useState<SlaPolicy[] | null>(null);
  const [services, setServices] = useState<Service[]>([]);
  const [name, setName] = useState("");
  const [serviceCode, setServiceCode] = useState("");
  const [threshold, setThreshold] = useState(0.75);
  const [targets, setTargets] = useState<TargetForm>({
    P1: { response: "5", resolution: "30" },
    P2: { response: "15", resolution: "120" },
    P3: { response: "60", resolution: "480" },
    P4: { response: "240", resolution: "1440" },
  });

  const load = useCallback(() => {
    api.slaPolicies().then(setPolicies).catch(fail);
    api.services().then(setServices).catch(fail);
  }, [fail]);
  useEffect(() => {
    load();
  }, [load]);

  function edit(p: SlaPolicy) {
    setName(p.name);
    setServiceCode(p.serviceCode ?? "");
    setThreshold(p.warningThreshold);
    const next: TargetForm = { ...targets };
    for (const pr of PRIORITIES) {
      const t = p.targets[pr];
      next[pr] = {
        response: t ? String(t.responseMinutes) : "",
        resolution: t ? String(t.resolutionMinutes) : "",
      };
    }
    setTargets(next);
  }

  async function save(e: FormEvent) {
    e.preventDefault();
    const body: Partial<Record<Priority, { responseMinutes: number; resolutionMinutes: number }>> =
      {};
    for (const p of PRIORITIES) {
      const t = targets[p];
      if (t.response && t.resolution) {
        body[p] = { responseMinutes: Number(t.response), resolutionMinutes: Number(t.resolution) };
      }
    }
    try {
      await api.saveSlaPolicy({
        name,
        serviceCode: serviceCode || null,
        warningThreshold: threshold,
        targets: body,
      });
      ok(`SLA policy "${name}" saved.`);
      load();
    } catch (err) {
      fail(err);
    }
  }

  async function remove(code: string) {
    try {
      await api.deleteSlaPolicy(code);
      ok(`Policy for ${code} removed; the default policy now applies.`);
      load();
    } catch (err) {
      fail(err);
    }
  }

  return (
    <div className="grid-2">
      <Card title="Policies">
        {!policies ? (
          <Spinner />
        ) : (
          <ul className="list">
            {policies.map((p) => (
              <li key={p.serviceCode ?? "default"} className="list-item">
                <div className="row space-between">
                  <strong>
                    {p.name}{" "}
                    <span className="muted small">
                      {p.serviceCode ? `service ${p.serviceCode}` : "default for all services"}
                    </span>
                  </strong>
                  <span className="row gap-s">
                    <button type="button" className="btn btn-small" onClick={() => edit(p)}>
                      Edit
                    </button>
                    {p.serviceCode && (
                      <button
                        type="button"
                        className="btn btn-small btn-danger"
                        onClick={() => p.serviceCode && remove(p.serviceCode)}
                      >
                        Remove
                      </button>
                    )}
                  </span>
                </div>
                <table>
                  <tbody>
                    {PRIORITIES.filter((pr) => p.targets[pr]).map((pr) => (
                      <tr key={pr}>
                        <td>{pr}</td>
                        <td className="small">respond {p.targets[pr]?.responseMinutes} min</td>
                        <td className="small">resolve {p.targets[pr]?.resolutionMinutes} min</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="muted small">
                  Warning at {Math.round(p.warningThreshold * 100)}% of the allowed time
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Create or update a policy">
        <form className="form" onSubmit={save}>
          <Field label="Policy name" htmlFor="pname">
            <input id="pname" value={name} onChange={(e) => setName(e.target.value)} required />
          </Field>
          <Field
            label="Applies to"
            htmlFor="pservice"
            hint="A service policy overrides the default for that service"
          >
            <select
              id="pservice"
              value={serviceCode}
              onChange={(e) => setServiceCode(e.target.value)}
            >
              <option value="">All services (default policy)</option>
              {services.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label={`Warning threshold: ${Math.round(threshold * 100)}%`} htmlFor="pthreshold">
            <input
              id="pthreshold"
              type="range"
              min={0.5}
              max={0.95}
              step={0.05}
              value={threshold}
              onChange={(e) => setThreshold(Number(e.target.value))}
            />
          </Field>
          <table>
            <thead>
              <tr>
                <th>Priority</th>
                <th>Respond within (min)</th>
                <th>Resolve within (min)</th>
              </tr>
            </thead>
            <tbody>
              {PRIORITIES.map((p) => (
                <tr key={p}>
                  <td>{p}</td>
                  <td>
                    <input
                      aria-label={`${p} response minutes`}
                      type="number"
                      min={1}
                      value={targets[p].response}
                      onChange={(e) =>
                        setTargets({ ...targets, [p]: { ...targets[p], response: e.target.value } })
                      }
                    />
                  </td>
                  <td>
                    <input
                      aria-label={`${p} resolution minutes`}
                      type="number"
                      min={1}
                      value={targets[p].resolution}
                      onChange={(e) =>
                        setTargets({
                          ...targets,
                          [p]: { ...targets[p], resolution: e.target.value },
                        })
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted small">
            Leave a row empty to fall back to the default policy for that priority.
          </p>
          <button type="submit" className="btn btn-primary">
            Save policy
          </button>
        </form>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------------------

function StrategiesTab({ ok, fail }: Feedback) {
  const [s, setS] = useState<Strategies | null>(null);
  useEffect(() => {
    api.strategies().then(setS).catch(fail);
  }, [fail]);

  async function change(assignment: string | null, priority: string | null) {
    try {
      setS(await api.changeStrategies(assignment, priority));
      ok("Strategy changed. New incidents use it immediately; the choice survives restarts.");
    } catch (e) {
      fail(e);
    }
  }

  if (!s) {
    return <Spinner />;
  }
  return (
    <div className="grid-2">
      <Card title="Technician assignment strategy">
        <ul className="list">
          {s.assignmentOptions.map((o) => (
            <li
              key={o.name}
              className={`list-item ${o.name === s.assignment.name ? "unread" : ""}`}
            >
              <div className="row space-between">
                <strong>{humanize(o.name)}</strong>
                {o.name === s.assignment.name ? (
                  <span className="badge sla-met">Active</span>
                ) : (
                  <button
                    type="button"
                    className="btn btn-small"
                    onClick={() => change(o.name, null)}
                  >
                    Use this
                  </button>
                )}
              </div>
              <div className="muted small">{o.description}</div>
            </li>
          ))}
        </ul>
      </Card>
      <Card title="Priority calculation strategy">
        <ul className="list">
          {s.priorityOptions.map((o) => (
            <li key={o.name} className={`list-item ${o.name === s.priority.name ? "unread" : ""}`}>
              <div className="row space-between">
                <strong>{humanize(o.name)}</strong>
                {o.name === s.priority.name ? (
                  <span className="badge sla-met">Active</span>
                ) : (
                  <button
                    type="button"
                    className="btn btn-small"
                    onClick={() => change(null, o.name)}
                  >
                    Use this
                  </button>
                )}
              </div>
              <div className="muted small">{o.description}</div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------------------

function AuditTab({ fail }: { fail: (e: unknown) => void }) {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [incident, setIncident] = useState("");
  const [actor, setActor] = useState("");
  const [action, setAction] = useState("");

  const load = useCallback(
    (f: { incident?: string; actor?: string; action?: string }) => {
      setEntries(null);
      api
        .audit(f)
        .then((list) => setEntries(list.slice().reverse()))
        .catch(fail);
    },
    [fail]
  );
  useEffect(() => {
    load({});
  }, [load]);

  return (
    <Card title="Audit log">
      <form
        className="filters"
        onSubmit={(e) => {
          e.preventDefault();
          load({
            incident: incident || undefined,
            actor: actor || undefined,
            action: action || undefined,
          });
        }}
      >
        <Field label="Incident" htmlFor="aincident">
          <input
            id="aincident"
            value={incident}
            onChange={(e) => setIncident(e.target.value)}
            placeholder="INC1001"
          />
        </Field>
        <Field label="User" htmlFor="aactor">
          <input
            id="aactor"
            value={actor}
            onChange={(e) => setActor(e.target.value)}
            placeholder="T201 or SYSTEM"
          />
        </Field>
        <Field label="Action" htmlFor="aaction">
          <input
            id="aaction"
            value={action}
            onChange={(e) => setAction(e.target.value)}
            placeholder="ASSIGNED, ESCALATED…"
          />
        </Field>
        <button type="submit" className="btn btn-primary">
          Filter
        </button>
      </form>
      {!entries ? (
        <Spinner />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>When</th>
                <th>Who</th>
                <th>Action</th>
                <th>Incident</th>
                <th>State</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {entries.slice(0, 300).map((e) => (
                <tr key={e.seq}>
                  <td className="small mono">{formatDateTime(e.timestamp)}</td>
                  <td className="small">
                    {e.actorId}
                    <div className="muted">{ROLE_LABEL[e.actorRole]}</div>
                  </td>
                  <td className="small">{e.action}</td>
                  <td className="small">
                    {e.incidentId ? (
                      <Link href={`/incidents/${e.incidentId}`}>{e.incidentId}</Link>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="small">
                    {e.previousStatus && e.newStatus && e.previousStatus !== e.newStatus
                      ? `${humanize(e.previousStatus)} → ${humanize(e.newStatus)}`
                      : e.newStatus
                        ? humanize(e.newStatus)
                        : ""}
                  </td>
                  <td className="small">{e.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
