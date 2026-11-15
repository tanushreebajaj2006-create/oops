import type { IncidentStatus, Level, Priority, Role, SlaStatus } from "./types";

const dateTime = new Intl.DateTimeFormat(undefined, {
  day: "2-digit",
  month: "short",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : "—";
}

/** "5 min ago", "in 2 h", "3 days ago". */
export function relative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) {
    return "—";
  }
  const diffMin = Math.round((new Date(iso).getTime() - now) / 60000);
  const abs = Math.abs(diffMin);
  let text: string;
  if (abs < 1) {
    return "just now";
  }
  if (abs < 90) {
    text = `${abs} min`;
  } else if (abs < 48 * 60) {
    text = `${Math.round(abs / 60)} h`;
  } else {
    text = `${Math.round(abs / 1440)} days`;
  }
  return diffMin < 0 ? `${text} ago` : `in ${text}`;
}

export function minutes(m: number | null | undefined): string {
  if (m === null || m === undefined) {
    return "—";
  }
  const abs = Math.abs(m);
  const text =
    abs < 90
      ? `${abs} min`
      : abs < 48 * 60
        ? `${(abs / 60).toFixed(1)} h`
        : `${(abs / 1440).toFixed(1)} days`;
  return m < 0 ? `${text} overdue` : text;
}

export function humanize(code: string | null | undefined): string {
  if (!code) {
    return "—";
  }
  const words = code.toLowerCase().replaceAll("_", " ");
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export const ROLE_LABEL: Record<Role, string> = {
  EMPLOYEE: "Employee",
  TECHNICIAN: "Technician",
  TEAM_LEAD: "Team lead",
  ADMINISTRATOR: "Administrator",
  SYSTEM: "System",
};

export const PRIORITY_LABEL: Record<Priority, string> = {
  P1: "P1 · Critical",
  P2: "P2 · High",
  P3: "P3 · Medium",
  P4: "P4 · Low",
};

export const LEVELS: Level[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
export const PRIORITIES: Priority[] = ["P1", "P2", "P3", "P4"];
export const ALL_STATUSES: IncidentStatus[] = [
  "NEW",
  "CLASSIFIED",
  "ASSIGNED",
  "ACKNOWLEDGED",
  "IN_PROGRESS",
  "AWAITING_INFO",
  "RESOLVED",
  "VERIFIED",
  "CLOSED",
  "CANCELLED",
];
export const SLA_STATUSES: SlaStatus[] = ["ON_TRACK", "AT_RISK", "BREACHED", "MET"];

export function isStaff(role: Role | undefined): boolean {
  return role === "TECHNICIAN" || role === "TEAM_LEAD" || role === "ADMINISTRATOR";
}

export function isSupervisor(role: Role | undefined): boolean {
  return role === "TEAM_LEAD" || role === "ADMINISTRATOR";
}
