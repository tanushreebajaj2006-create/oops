"use client";

import { Card, ErrorBanner, Field, PageHeader, StatusBadge } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { LEVELS, humanize } from "@/lib/format";
import type { Category, CategorySuggestion, Level, RelatedIncident, Service } from "@/lib/types";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";

const LEVEL_HELP: Record<"impact" | "urgency", Record<Level, string>> = {
  impact: {
    LOW: "Only me, minor inconvenience",
    MEDIUM: "Several people or one team",
    HIGH: "A department or an important process",
    CRITICAL: "The whole organisation or customers",
  },
  urgency: {
    LOW: "Can wait a few days",
    MEDIUM: "Should be fixed today",
    HIGH: "Work is blocked now",
    CRITICAL: "Business is losing money right now",
  },
};

/** Report an incident, with live category suggestions and duplicate warnings. */
export default function ReportPage() {
  const router = useRouter();
  const [services, setServices] = useState<Service[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [serviceCode, setServiceCode] = useState("");
  const [impact, setImpact] = useState<Level>("MEDIUM");
  const [urgency, setUrgency] = useState<Level>("MEDIUM");
  const [affectedUsers, setAffectedUsers] = useState(1);
  const [categoryCode, setCategoryCode] = useState("");
  const [suggestions, setSuggestions] = useState<CategorySuggestion[]>([]);
  const [duplicates, setDuplicates] = useState<RelatedIncident[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.services(), api.categories()])
      .then(([s, c]) => {
        setServices(s.filter((x) => x.active));
        setCategories(c.filter((x) => x.active));
      })
      .catch((e) => setError(errorMessage(e)));
  }, []);

  // live intelligence: wait until the user pauses typing, then ask the server
  useEffect(() => {
    if (title.trim().length < 5) {
      setSuggestions([]);
      setDuplicates([]);
      return;
    }
    const timer = window.setTimeout(() => {
      api
        .suggestCategories(title, description)
        .then(setSuggestions)
        .catch(() => setSuggestions([]));
      if (serviceCode) {
        api
          .duplicateCheck(title, description, serviceCode)
          .then(setDuplicates)
          .catch(() => setDuplicates([]));
      }
    }, 600);
    return () => window.clearTimeout(timer);
  }, [title, description, serviceCode]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await api.report({
        title: title.trim(),
        description: description.trim(),
        serviceCode,
        impact,
        urgency,
        affectedUsers,
        categoryCode: categoryCode || null,
      });
      router.push(`/incidents/${created.id}?created=1`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  const top = suggestions[0];
  return (
    <>
      <PageHeader
        title="Report an incident"
        subtitle="Describe what is wrong. The system classifies, prioritises and assigns it automatically."
      />
      <div className="layout-main-side">
        <Card>
          <ErrorBanner message={error} onClose={() => setError(null)} />
          <form className="form" onSubmit={submit}>
            <Field
              label="Title"
              htmlFor="title"
              hint="One line, e.g. “Payment application unavailable”"
            >
              <input
                id="title"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
                minLength={5}
                maxLength={120}
              />
            </Field>
            <Field
              label="What happened?"
              htmlFor="description"
              hint="When did it start, what do you see, who is affected?"
            >
              <textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                required
                minLength={10}
                maxLength={4000}
              />
            </Field>
            <div className="grid-2">
              <Field label="Affected service" htmlFor="service">
                <select
                  id="service"
                  value={serviceCode}
                  onChange={(e) => setServiceCode(e.target.value)}
                  required
                >
                  <option value="">Choose a service…</option>
                  {services.map((s) => (
                    <option key={s.code} value={s.code}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field
                label="Category (optional)"
                htmlFor="category"
                hint="Leave empty to let the system decide"
              >
                <select
                  id="category"
                  value={categoryCode}
                  onChange={(e) => setCategoryCode(e.target.value)}
                >
                  <option value="">Let the system decide</option>
                  {categories.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Impact" htmlFor="impact" hint={LEVEL_HELP.impact[impact]}>
                <select
                  id="impact"
                  value={impact}
                  onChange={(e) => setImpact(e.target.value as Level)}
                >
                  {LEVELS.map((l) => (
                    <option key={l} value={l}>
                      {humanize(l)}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Urgency" htmlFor="urgency" hint={LEVEL_HELP.urgency[urgency]}>
                <select
                  id="urgency"
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
              <Field label="People affected" htmlFor="affected">
                <input
                  id="affected"
                  type="number"
                  min={1}
                  max={1000000}
                  value={affectedUsers}
                  onChange={(e) => setAffectedUsers(Math.max(1, Number(e.target.value) || 1))}
                />
              </Field>
            </div>
            <div className="row gap-s">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {busy ? "Submitting…" : "Submit incident"}
              </button>
              <Link href="/incidents" className="btn btn-ghost">
                Cancel
              </Link>
            </div>
          </form>
        </Card>

        <div className="col gap-l">
          <Card title="Suggested category">
            {top ? (
              <div className="col gap-s">
                {suggestions.map((s) => (
                  <div key={s.code} className="row space-between">
                    <span>
                      <strong>{s.name}</strong>{" "}
                      {s.matchedTerms.length > 0 && (
                        <span className="muted small">({s.matchedTerms.join(", ")})</span>
                      )}
                    </span>
                    <span className="row gap-s">
                      <span className="muted small">{s.confidencePercent}%</span>
                      <button
                        type="button"
                        className="btn btn-small"
                        onClick={() => setCategoryCode(s.code)}
                      >
                        Use
                      </button>
                    </span>
                  </div>
                ))}
                <p className="muted small">
                  {top.confident
                    ? `If you leave the category empty, it will be classified as ${top.name} automatically.`
                    : "Not confident enough to decide alone - a team lead will classify it."}
                </p>
              </div>
            ) : (
              <p className="muted small">
                Start typing a title and description to see suggestions.
              </p>
            )}
          </Card>
          <Card title="Already reported?">
            {duplicates.length === 0 ? (
              <p className="muted small">
                {serviceCode
                  ? "No similar open incidents found."
                  : "Choose the affected service to check for duplicates."}
              </p>
            ) : (
              <div className="alert alert-warn">
                <strong>
                  Similar incidents are already open - you may not need to report this again.
                </strong>
                <ul className="list">
                  {duplicates.map((d) => (
                    <li key={d.id} className="list-item">
                      <div className="row space-between">
                        <span className="strong">
                          {d.id} · {d.title}
                        </span>
                        <StatusBadge status={d.status} />
                      </div>
                      <div className="small">
                        {d.verdict === "LIKELY_DUPLICATE" ? "Likely duplicate" : "Related"} (
                        {d.scorePercent}%): {d.reasons.join("; ")}
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
