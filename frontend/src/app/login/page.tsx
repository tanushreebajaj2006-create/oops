"use client";

import { ErrorBanner, Field } from "@/components/ui";
import { errorMessage } from "@/lib/api";
import { homeFor, useAuth } from "@/lib/auth";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";

/** Sign in, or register as a new employee. */
export default function LoginPage() {
  const { user, login, register } = useAuth();
  const router = useRouter();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [department, setDepartment] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      router.replace(homeFor(user));
    }
  }, [user, router]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const u =
        mode === "login"
          ? await login(email.trim(), password)
          : await register(fullName.trim(), email.trim(), password, department.trim());
      router.replace(homeFor(u));
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <div className="card login-card">
        <div>
          <h1>ServiceDesk</h1>
          <p className="muted">Intelligent IT Service Management &amp; Incident Resolution</p>
        </div>
        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === "login"}
            className={`tab ${mode === "login" ? "active" : ""}`}
            onClick={() => setMode("login")}
          >
            Sign in
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === "register"}
            className={`tab ${mode === "register" ? "active" : ""}`}
            onClick={() => setMode("register")}
          >
            Register
          </button>
        </div>
        <ErrorBanner message={error} />
        <form className="form" onSubmit={submit}>
          {mode === "register" && (
            <>
              <Field label="Full name" htmlFor="fullName">
                <input
                  id="fullName"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                  maxLength={100}
                />
              </Field>
              <Field label="Department" htmlFor="department">
                <input
                  id="department"
                  value={department}
                  onChange={(e) => setDepartment(e.target.value)}
                  maxLength={80}
                />
              </Field>
            </>
          )}
          <Field label="E-mail" htmlFor="email">
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </Field>
          <Field
            label="Password"
            htmlFor="password"
            hint={
              mode === "register" ? "At least 8 characters, with a letter and a digit." : undefined
            }
          >
            <input
              id="password"
              type="password"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={mode === "register" ? 8 : undefined}
            />
          </Field>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}
          </button>
        </form>
        {mode === "register" && (
          <p className="muted small">
            Self-registration creates an employee account. Technician and administrator accounts are
            created by an administrator.
          </p>
        )}
      </div>
    </div>
  );
}
