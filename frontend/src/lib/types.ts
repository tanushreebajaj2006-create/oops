/**
 * TypeScript mirrors of the backend's JSON views (com.servicedesk.api.dto.Views).
 * Keep in sync with the Java records.
 */

export type Role = "EMPLOYEE" | "TECHNICIAN" | "TEAM_LEAD" | "ADMINISTRATOR" | "SYSTEM";
export type Level = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type Priority = "P1" | "P2" | "P3" | "P4";
export type IncidentStatus =
  | "NEW"
  | "CLASSIFIED"
  | "ASSIGNED"
  | "ACKNOWLEDGED"
  | "IN_PROGRESS"
  | "AWAITING_INFO"
  | "RESOLVED"
  | "VERIFIED"
  | "CLOSED"
  | "CANCELLED";
export type IncidentAction =
  | "CLASSIFY"
  | "ASSIGN"
  | "ACKNOWLEDGE"
  | "START_WORK"
  | "REQUEST_INFO"
  | "PROVIDE_INFO"
  | "RESOLVE"
  | "VERIFY"
  | "REOPEN"
  | "CLOSE"
  | "CANCEL";
export type SlaStatus = "MET" | "NOT_APPLICABLE" | "ON_TRACK" | "AT_RISK" | "BREACHED";
export type NoteType = "INVESTIGATION" | "INFO_REQUEST" | "INFO_RESPONSE" | "COMMENT";

export const OPEN_STATUSES: IncidentStatus[] = [
  "NEW",
  "CLASSIFIED",
  "ASSIGNED",
  "ACKNOWLEDGED",
  "IN_PROGRESS",
  "AWAITING_INFO",
];

export interface UserRef {
  id: string;
  name: string;
  role: Role;
}

export interface User {
  id: string;
  role: Role;
  fullName: string;
  email: string;
  phone: string | null;
  active: boolean;
  department: string | null;
  skills: Record<string, number> | null;
  experienceYears: number | null;
  maxWorkload: number | null;
  activeWorkload: number | null;
  available: boolean | null;
}

export interface Session {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface SlaMeasure {
  dueAt: string;
  completedAt: string | null;
  status: SlaStatus;
  percentUsed: number;
  minutesRemaining: number;
}

export interface Sla {
  policy: string;
  priority: Priority;
  overall: SlaStatus;
  response: SlaMeasure;
  resolution: SlaMeasure;
}

export interface IncidentSummary {
  id: string;
  title: string;
  status: IncidentStatus;
  priority: Priority | null;
  category: string | null;
  serviceCode: string;
  serviceName: string;
  reporter: UserRef;
  assignee: UserRef | null;
  createdAt: string;
  updatedAt: string;
  slaStatus: SlaStatus | null;
  resolutionDueAt: string | null;
  escalationLevel: number;
}

export interface Note {
  author: UserRef;
  type: NoteType;
  text: string;
  createdAt: string;
}

export interface IncidentDetail {
  id: string;
  title: string;
  description: string;
  status: IncidentStatus;
  priority: Priority | null;
  category: string | null;
  serviceCode: string;
  serviceName: string;
  serviceCriticality: Level;
  impact: Level;
  urgency: Level;
  affectedUsers: number;
  reporter: UserRef;
  assignee: UserRef | null;
  createdAt: string;
  updatedAt: string;
  acknowledgedAt: string | null;
  resolvedAt: string | null;
  reopenCount: number;
  escalationLevel: number;
  sla: Sla | null;
  notes: Note[];
  resolution: {
    summary: string;
    rootCause: string | null;
    resolvedBy: UserRef;
    resolvedAt: string;
  } | null;
  feedback: { rating: number; comment: string | null; givenAt: string } | null;
  closure: { closedBy: UserRef; closedAt: string; note: string | null } | null;
  allowedActions: IncidentAction[];
}

export interface Page<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

export interface AuditEntry {
  seq: number;
  timestamp: string;
  actorId: string;
  actorRole: Role;
  action: string;
  incidentId: string | null;
  previousStatus: IncidentStatus | null;
  newStatus: IncidentStatus | null;
  description: string;
}

export interface NotificationItem {
  id: number;
  subject: string;
  body: string;
  incidentId: string;
  eventType: string;
  urgent: boolean;
  createdAt: string;
  read: boolean;
}

export interface Candidate {
  technician: UserRef;
  score: number;
  reason: string;
  activeWorkload: number;
  maxWorkload: number;
}

export interface Service {
  code: string;
  name: string;
  criticality: Level;
  active: boolean;
}

export interface Category {
  code: string;
  name: string;
  active: boolean;
}

export interface SlaPolicy {
  name: string;
  serviceCode: string | null;
  warningThreshold: number;
  targets: Partial<Record<Priority, { responseMinutes: number; resolutionMinutes: number }>>;
}

export interface StrategyInfo {
  name: string;
  description: string;
}

export interface Strategies {
  assignment: StrategyInfo;
  assignmentOptions: StrategyInfo[];
  priority: StrategyInfo;
  priorityOptions: StrategyInfo[];
}

export interface Overview {
  total: number;
  open: number;
  criticalOpen: number;
  resolved: number;
  cancelled: number;
  slaViolations: number;
  atRiskNow: number;
  slaCompliancePercent: number | null;
  averageResolutionMinutes: number | null;
  averageFeedbackRating: number | null;
  byPriority: Record<Priority, number>;
  byStatus: Record<IncidentStatus, number>;
}

export interface TechnicianLoad {
  technician: UserRef;
  activeIncidents: number;
  maxWorkload: number;
  utilisationPercent: number;
  available: boolean;
  resolvedTotal: number;
}

export interface Breakdown {
  code: string;
  name: string;
  total: number;
  open: number;
  critical: number;
}

export interface SlaAlert {
  type: string;
  incidentId: string;
  kind: string;
  priority: Priority;
  dueAt: string;
  detectedAt: string;
  assigneeId: string | null;
  overdueMinutes: number;
}

export interface SlaReport {
  recentViolations: SlaAlert[];
  atRisk: { incidentId: string; priority: Priority; overall: SlaStatus; sla: Sla }[];
  breachedOpen: { incidentId: string; priority: Priority; overall: SlaStatus; sla: Sla }[];
}

export interface MyWorkload {
  technician: UserRef;
  activeIncidents: number;
  maxWorkload: number;
  available: boolean;
  incidents: IncidentSummary[];
}

export interface CategorySuggestion {
  code: string;
  name: string;
  confidencePercent: number;
  matchedTerms: string[];
  confident: boolean;
}

export interface RelatedIncident {
  id: string;
  title: string;
  status: IncidentStatus;
  priority: Priority | null;
  serviceCode: string;
  createdAt: string;
  scorePercent: number;
  verdict: "LIKELY_DUPLICATE" | "RELATED";
  reasons: string[];
}

export interface ResolutionSuggestion {
  sourceIncidentId: string;
  sourceTitle: string;
  summary: string;
  rootCause: string | null;
  rating: number | null;
  resolvedAt: string | null;
  scorePercent: number;
}

export interface SlaRisk {
  incident: IncidentSummary;
  level: "LOW" | "MEDIUM" | "HIGH";
  projectedPercentOfAllowance: number;
  projectedResolution: string;
  dueAt: string;
  reasons: string[];
}

export interface Cluster {
  pattern: "SERVICE_OUTAGE" | "SHARED_COMPONENT";
  key: string;
  summary: string;
  highestPriority: Priority | null;
  count: number;
  firstReportedAt: string;
  lastReportedAt: string;
  commonTerms: string[];
  incidents: IncidentSummary[];
}

export interface SlaCheckResult {
  incidentsChecked: number;
  warnings: number;
  violations: number;
  escalations: number;
  skipped: number;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
}
