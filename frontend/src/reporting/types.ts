export interface Bucket { key: string; label: string; count: number }
export interface Report {
  observedAt: string;
  summary: { total: number; open: number; resolved: number; critical: number; slaViolations: number; responseViolations: number; resolutionViolations: number; completedWithTargets: number; onTimeCompleted: number; compliancePercent: number | null; averageResolutionSeconds: number | null; averageResponseSeconds: number | null; averageFeedback: number | null };
  statuses: Bucket[]; priorities: Bucket[]; categories: Bucket[]; sla: Bucket[]; serviceHealth: Bucket[];
  trends: { month: string; created: number; resolved: number }[];
}
export interface ReportPage<T> { content: T[]; totalElements: number; page: number; totalPages: number }
export interface Workload { id: string; displayName: string; enabled: boolean; available: boolean; capacity: number; active: number; assignments: number; resolved: number; averageResolutionSeconds: number | null; breached: number }
export interface ServiceHealth { id: string; name: string; total: number; open: number; critical: number; breached: number; health: string }
export const duration = (seconds: number | null) => seconds === null ? 'No completed data' : seconds < 60 ? `${seconds.toFixed(0)} sec` : seconds < 3600 ? `${(seconds / 60).toFixed(1)} min` : `${(seconds / 3600).toFixed(1)} hr`;
