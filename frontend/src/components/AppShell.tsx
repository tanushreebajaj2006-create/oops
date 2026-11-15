"use client";

import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { ROLE_LABEL } from "@/lib/format";
import type { Role } from "@/lib/types";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";
import { Spinner } from "./ui";

interface NavItem {
  href: string;
  label: string;
  roles: Role[];
}

/** The navigation differs per role, as the brief asks ("difference in GUI for different users"). */
const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", roles: ["TEAM_LEAD", "ADMINISTRATOR"] },
  { href: "/queue", label: "My queue", roles: ["TECHNICIAN", "TEAM_LEAD"] },
  { href: "/incidents", label: "Incidents", roles: ["TECHNICIAN", "TEAM_LEAD", "ADMINISTRATOR"] },
  { href: "/incidents", label: "My incidents", roles: ["EMPLOYEE"] },
  {
    href: "/report",
    label: "Report incident",
    roles: ["EMPLOYEE", "TECHNICIAN", "TEAM_LEAD", "ADMINISTRATOR"],
  },
  { href: "/admin", label: "Administration", roles: ["ADMINISTRATOR"] },
];

const PUBLIC_PATHS = ["/login"];

export function AppShell({ children }: { children: ReactNode }) {
  const { user, loading, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const isPublic = PUBLIC_PATHS.includes(pathname);
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    if (!loading && !user && !isPublic) {
      router.replace("/login");
    }
  }, [loading, user, isPublic, router]);

  // notification bell: refresh the unread count every 30 s and on navigation
  // biome-ignore lint/correctness/useExhaustiveDependencies: pathname is a deliberate refresh trigger
  useEffect(() => {
    if (!user) {
      return;
    }
    let alive = true;
    const load = () =>
      api
        .unreadCount()
        .then((r) => alive && setUnread(r.count))
        .catch(() => {});
    load();
    const timer = window.setInterval(load, 30_000);
    return () => {
      alive = false;
      window.clearInterval(timer);
    };
  }, [user, pathname]);

  if (isPublic) {
    return <>{children}</>;
  }
  if (loading || !user) {
    return (
      <div className="center-page">
        <Spinner />
      </div>
    );
  }

  const items = NAV.filter((n) => n.roles.includes(user.role));
  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-inner">
          <Link href="/" className="brand">
            ServiceDesk
            <span className="brand-sub">Incident Resolution</span>
          </Link>
          <nav className="nav" aria-label="Main">
            {items.map((n) => (
              <Link
                key={`${n.href}-${n.label}`}
                href={n.href}
                className={`nav-link ${pathname === n.href || pathname.startsWith(`${n.href}/`) ? "active" : ""}`}
              >
                {n.label}
              </Link>
            ))}
          </nav>
          <div className="row gap-m">
            <Link
              href="/notifications"
              className={`bell ${pathname === "/notifications" ? "active" : ""}`}
              aria-label={`Notifications, ${unread} unread`}
            >
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-6V11a7 7 0 0 0-5.5-6.84V3a1.5 1.5 0 0 0-3 0v1.16A7 7 0 0 0 5 11v5l-2 2v1h18v-1Z"
                />
              </svg>
              {unread > 0 && <span className="bell-count">{unread > 99 ? "99+" : unread}</span>}
            </Link>
            <div className="user-chip">
              <span className="user-name">{user.fullName}</span>
              <span className="user-role">
                {ROLE_LABEL[user.role]} · {user.id}
              </span>
            </div>
            <button type="button" className="btn btn-ghost" onClick={logout}>
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="content">{children}</main>
      <footer className="footer">
        ServiceDesk · Intelligent IT Service Management · OOP course project
      </footer>
    </div>
  );
}
