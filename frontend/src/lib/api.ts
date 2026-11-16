import type {
  ApiErrorBody,
  AuditEntry,
  Breakdown,
  Candidate,
  Category,
  CategorySuggestion,
  Cluster,
  IncidentDetail,
  IncidentSummary,
  Level,
  MyWorkload,
  NoteType,
  NotificationItem,
  Overview,
  Page,
  Priority,
  RelatedIncident,
  ResolutionSuggestion,
  Role,
  Service,
  Session,
  SlaCheckResult,
  SlaPolicy,
  SlaReport,
  SlaRisk,
  Strategies,
  TechnicianLoad,
  User,
} from "./types";

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

const TOKEN_KEY = "servicedesk.token";

/** An error answered by the backend, carrying its stable error code. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) {
    window.localStorage.setItem(TOKEN_KEY, token);
  } else {
    window.localStorage.removeItem(TOKEN_KEY);
  }
}

/** Called when the server says the token is no longer valid (expired, account deactivated). */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  const token = getToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw new ApiError(
      0,
      "NETWORK_ERROR",
      "Cannot reach the ServiceDesk server. Check your connection."
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : undefined;
  if (!response.ok) {
    const err = (data ?? {}) as Partial<ApiErrorBody>;
    if (response.status === 401 && token) {
      onUnauthorized();
    }
    throw new ApiError(
      response.status,
      err.code ?? "HTTP_ERROR",
      err.message ?? `Request failed (${response.status})`
    );
  }
  return data as T;
}

function query(params: Record<string, string | number | string[] | undefined | null>): string {
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") {
      continue;
    }
    if (Array.isArray(value)) {
      if (value.length > 0) {
        q.set(key, value.join(","));
      }
    } else {
      q.set(key, String(value));
    }
  }
  const s = q.toString();
  return s ? `?${s}` : "";
}

export interface IncidentFilters {
  id?: string;
  status?: string[];
  priority?: string[];
  category?: string;
  service?: string;
  technician?: string;
  reporter?: string;
  from?: string;
  to?: string;
  q?: string;
  sla?: string;
  limit?: number;
  offset?: number;
}

export interface ReportIncidentBody {
  title: string;
  description: string;
  serviceCode: string;
  impact: Level;
  urgency: Level;
  affectedUsers: number;
  categoryCode?: string | null;
}

export interface UpdateIncidentBody {
  title: string;
  description: string;
  impact: Level;
  urgency: Level;
  affectedUsers: number;
}

export interface CreateUserBody {
  role: Role;
  fullName: string;
  email: string;
  password: string;
  department?: string | null;
  phone?: string | null;
  skills?: Record<string, number>;
  experienceYears?: number;
  maxWorkload?: number;
}

export type UpdateUserBody = Partial<{
  fullName: string;
  phone: string;
  active: boolean;
  department: string;
  skills: Record<string, number>;
  experienceYears: number;
  maxWorkload: number;
  available: boolean;
}>;

export interface SaveSlaPolicyBody {
  name: string;
  serviceCode: string | null;
  warningThreshold: number;
  targets: Partial<Record<Priority, { responseMinutes: number; resolutionMinutes: number }>>;
}

const id = (incidentId: string) => `/incidents/${encodeURIComponent(incidentId)}`;

/** Every backend endpoint the UI uses, typed. */
export const api = {
  // auth
  login: (email: string, password: string) =>
    request<Session>("POST", "/auth/login", { email, password }),
  register: (fullName: string, email: string, password: string, department: string) =>
    request<Session>("POST", "/auth/register", { fullName, email, password, department }),
  me: () => request<User>("GET", "/auth/me"),

  // incidents
  searchIncidents: (f: IncidentFilters) =>
    request<Page<IncidentSummary>>("GET", `/incidents${query({ ...f })}`),
  getIncident: (incidentId: string) => request<IncidentDetail>("GET", id(incidentId)),
  history: (incidentId: string) => request<AuditEntry[]>("GET", `${id(incidentId)}/history`),
  recommendations: (incidentId: string) =>
    request<Candidate[]>("GET", `${id(incidentId)}/recommendations`),
  related: (incidentId: string) => request<RelatedIncident[]>("GET", `${id(incidentId)}/related`),
  resolutionSuggestions: (incidentId: string) =>
    request<ResolutionSuggestion[]>("GET", `${id(incidentId)}/resolution-suggestions`),
  report: (body: ReportIncidentBody) => request<IncidentDetail>("POST", "/incidents", body),
  update: (incidentId: string, body: UpdateIncidentBody) =>
    request<IncidentDetail>("PATCH", id(incidentId), body),
  classify: (incidentId: string, categoryCode: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/classify`, { categoryCode }),
  setPriority: (incidentId: string, priority: Priority) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/priority`, { priority }),
  assign: (incidentId: string, technicianId: string | null) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/assign`, { technicianId }),
  acknowledge: (incidentId: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/acknowledge`),
  start: (incidentId: string) => request<IncidentDetail>("POST", `${id(incidentId)}/start`),
  requestInfo: (incidentId: string, text: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/request-info`, { text }),
  provideInfo: (incidentId: string, text: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/provide-info`, { text }),
  addNote: (incidentId: string, type: NoteType, text: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/notes`, { type, text }),
  resolve: (incidentId: string, summary: string, rootCause: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/resolve`, { summary, rootCause }),
  verify: (incidentId: string) => request<IncidentDetail>("POST", `${id(incidentId)}/verify`),
  reopen: (incidentId: string, text: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/reopen`, { text }),
  close: (incidentId: string, note: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/close`, { note }),
  cancel: (incidentId: string, text: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/cancel`, { text }),
  feedback: (incidentId: string, rating: number, comment: string) =>
    request<IncidentDetail>("POST", `${id(incidentId)}/feedback`, { rating, comment }),

  // intelligence
  suggestCategories: (title: string, description: string) =>
    request<CategorySuggestion[]>("POST", "/intelligence/category-suggestions", {
      title,
      description,
    }),
  duplicateCheck: (title: string, description: string, serviceCode: string) =>
    request<RelatedIncident[]>("POST", "/intelligence/duplicate-check", {
      title,
      description,
      serviceCode,
    }),

  // reference data
  services: () => request<Service[]>("GET", "/services"),
  categories: () => request<Category[]>("GET", "/categories"),

  // technicians
  technicians: () => request<User[]>("GET", "/technicians"),
  myWorkload: () => request<MyWorkload>("GET", "/technicians/me/workload"),
  setAvailability: (available: boolean) =>
    request<User>("PUT", "/technicians/me/availability", { available }),

  // notifications
  notifications: () => request<NotificationItem[]>("GET", "/notifications"),
  unreadCount: () => request<{ count: number }>("GET", "/notifications/unread-count"),
  markRead: (notificationId: number) =>
    request<void>("POST", `/notifications/${notificationId}/read`),

  // reports
  overview: () => request<Overview>("GET", "/reports/overview"),
  technicianReport: () => request<TechnicianLoad[]>("GET", "/reports/technicians"),
  serviceReport: () => request<Breakdown[]>("GET", "/reports/services"),
  categoryReport: () => request<Breakdown[]>("GET", "/reports/categories"),
  slaReport: () => request<SlaReport>("GET", "/reports/sla"),
  slaRisk: () => request<SlaRisk[]>("GET", "/reports/sla-risk"),
  correlations: () => request<Cluster[]>("GET", "/reports/correlations"),

  // admin
  users: () => request<User[]>("GET", "/admin/users"),
  createUser: (body: CreateUserBody) => request<User>("POST", "/admin/users", body),
  updateUser: (userId: string, body: UpdateUserBody) =>
    request<User>("PATCH", `/admin/users/${encodeURIComponent(userId)}`, body),
  saveService: (code: string, name: string, criticality: Level, active: boolean) =>
    request<Service>("PUT", `/admin/services/${encodeURIComponent(code)}`, {
      name,
      criticality,
      active,
    }),
  saveCategory: (code: string, name: string, active: boolean) =>
    request<Category>("PUT", `/admin/categories/${encodeURIComponent(code)}`, { name, active }),
  slaPolicies: () => request<SlaPolicy[]>("GET", "/admin/sla-policies"),
  saveSlaPolicy: (body: SaveSlaPolicyBody) =>
    request<SlaPolicy>("PUT", "/admin/sla-policies", body),
  deleteSlaPolicy: (serviceCode: string) =>
    request<void>("DELETE", `/admin/sla-policies/${encodeURIComponent(serviceCode)}`),
  strategies: () => request<Strategies>("GET", "/admin/strategies"),
  changeStrategies: (assignment: string | null, priority: string | null) =>
    request<Strategies>("PUT", "/admin/strategies", { assignment, priority }),
  audit: (f: { incident?: string; actor?: string; action?: string; from?: string; to?: string }) =>
    request<AuditEntry[]>("GET", `/admin/audit${query(f)}`),
  runSlaCheck: () => request<SlaCheckResult>("POST", "/admin/sla-check"),
};

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) {
    return e.message;
  }
  return e instanceof Error ? e.message : "Something went wrong";
}
