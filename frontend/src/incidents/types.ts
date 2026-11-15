export const statuses = ['NEW', 'CLASSIFIED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'VERIFIED', 'CLOSED'] as const;
export type IncidentStatus = typeof statuses[number];
export interface Service { id: string; name: string; description: string; businessCriticality: number; active: boolean }
export interface Category { id: string; name: string; description: string; active: boolean }
export interface Person { id: string; displayName: string }
export interface Technician extends Person { capacity: number; available: boolean; workload: number; skills: { categoryId: string; proficiency: number }[] }
export type Priority = 'P1' | 'P2' | 'P3' | 'P4';
export type SlaOutcome = 'UNCONFIGURED' | 'TRACKING' | 'APPROACHING' | 'MET' | 'BREACHED';
export interface Sla {
  policyId: string | null; responseSeconds: number | null; resolutionSeconds: number | null; warningPercent: number | null;
  responseDueAt: string | null; resolutionDueAt: string | null; firstAcknowledgedAt: string | null; firstResponseAt: string | null;
  responseBreachedAt: string | null; resolutionBreachedAt: string | null; responseStatus: SlaOutcome; resolutionStatus: SlaOutcome;
  evaluatedAt: string | null; issue: string | null; serverTime: string;
}
export interface Incident {
  id: number; reference: string; version: number; title: string; description: string; reporter: Person;
  service: Service; category: Category; impact: number; urgency: number; priority: 'P1' | 'P2' | 'P3' | 'P4' | null;
  status: IncidentStatus; technician: Person | null; resolution: string | null; feedback: string | null; feedbackRating: number | null;
  createdAt: string; updatedAt: string; acknowledgedAt: string | null; resolvedAt: string | null; verifiedAt: string | null; closedAt: string | null;
  priorityScore: number | null; priorityCriticality: number | null; assignmentIssue: string | null; sla: Sla | null;
}
export interface IncidentComment { id: number; author: Person; body: string; internal: boolean; createdAt: string }
export interface History { incidentId?: number; oldValues?: Record<string, unknown>; newValues?: Record<string, unknown>; id: number; actor: Person; action: string; previousStatus: IncidentStatus | null; newStatus: IncidentStatus; description: string; internal: boolean; createdAt: string }
export interface Detail { incident: Incident; comments: IncidentComment[]; history: History[]; actions: string[] }
export interface IncidentPage { content: Incident[]; totalElements: number; totalPages: number; page: number; size: number }
export const label = (value: string) => value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
export const dateTime = (value: string) => new Date(value).toLocaleString();
