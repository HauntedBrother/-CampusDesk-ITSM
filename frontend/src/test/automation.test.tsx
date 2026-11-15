import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AutomationPage } from '@/incidents/AutomationPage';
import { SlaPanel, duration, slaOutcome } from '@/incidents/SlaPanel';
import type { Incident, Sla } from '@/incidents/types';

const { auth } = vi.hoisted(() => ({ auth: { user: { id: 'admin', role: 'ADMIN' }, request: vi.fn() } }));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
const category = { id: 'category-1', name: 'Network', description: '', active: true };
const service = { id: 'service-1', name: 'Campus Wi-Fi', description: '', businessCriticality: 3, active: true };
const policy = { id: 'policy-1', serviceId: null, priority: 'P1', responseSeconds: 300, resolutionSeconds: 1800, warningPercent: 80, active: true };
function response(path: string) {
  if (path.endsWith('/settings')) return { strategy: 'SKILL_BASED', automaticAssignment: true };
  if (path.endsWith('/policies')) return [policy];
  if (path.endsWith('/technicians')) return [{ id: 'tech-1', displayName: 'Engineer', capacity: 5, available: true, workload: 1, skills: [{ categoryId: category.id, proficiency: 3 }] }];
  if (path.startsWith('/catalog/services')) return [service];
  if (path.startsWith('/catalog/categories')) return [category];
  if (path.endsWith('/history')) return [];
}
beforeEach(() => { auth.request.mockReset(); auth.request.mockImplementation(async (path: string) => response(path)); });
describe('Automation configuration', () => {
  it('loads persisted policy values and technician workload', async () => {
    render(<MemoryRouter><AutomationPage/></MemoryRouter>);
    expect(await screen.findByLabelText('Response target (seconds)')).toHaveValue(300);
    expect(screen.getByLabelText('Resolution target (seconds)')).toHaveValue(1800);
    expect(screen.getByText('1 / 5 active')).toBeInTheDocument();
    expect(screen.getByLabelText('Skill: Network')).toHaveValue('3');
  });
  it('persists an interchangeable assignment strategy and automatic toggle', async () => {
    render(<MemoryRouter><AutomationPage/></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText('Strategy'), { target: { value: 'LOAD_BASED' } });
    fireEvent.click(screen.getByLabelText('Automatically assign new/updated unassigned incidents'));
    fireEvent.click(screen.getByText('Save strategy'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/admin/automation/settings', { method: 'PUT', body: JSON.stringify({ strategy: 'LOAD_BASED', automaticAssignment: false }) }));
  });
  it('submits SLA targets and warning thresholds to the admin API', async () => {
    render(<MemoryRouter><AutomationPage/></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText('Response target (seconds)'), { target: { value: '600' } });
    fireEvent.change(screen.getByLabelText('Warn at elapsed target (%)'), { target: { value: '75' } });
    fireEvent.click(screen.getByText('Save SLA policy'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/admin/automation/policies', { method: 'PUT', body: JSON.stringify({ serviceId: null, priority: 'P1', responseSeconds: 600, resolutionSeconds: 1800, warningPercent: 75, active: true }) }));
  });
  it('saves actual category proficiency and configurable capacity', async () => {
    render(<MemoryRouter><AutomationPage/></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText('Capacity'), { target: { value: '3' } });
    fireEvent.change(screen.getByLabelText('Skill: Network'), { target: { value: '2' } });
    fireEvent.click(screen.getByText('Save technician'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/admin/automation/technicians/tech-1', { method: 'PUT', body: JSON.stringify({ capacity: 3, available: true, skills: [{ categoryId: category.id, proficiency: 2 }] }) }));
  });
  it('does not replace failed backend reads with invented configuration', async () => {
    auth.request.mockRejectedValue(new Error('Forbidden'));
    render(<MemoryRouter><AutomationPage/></MemoryRouter>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Forbidden');
    expect(screen.queryByText('Save strategy')).not.toBeInTheDocument();
  });
  it('surfaces backend capacity errors instead of claiming success', async () => {
    auth.request.mockImplementation(async (path: string, options?: RequestInit) => { if (options?.method === 'PUT') throw new Error('Capacity cannot be reduced below the current active workload'); return response(path); });
    render(<MemoryRouter><AutomationPage/></MemoryRouter>);
    fireEvent.click(await screen.findByText('Save technician'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Capacity cannot be reduced');
    expect(screen.queryByText('Configuration saved and audited.')).not.toBeInTheDocument();
  });
});

const start = Date.parse('2026-11-15T12:00:00Z');
const at = (seconds: number) => new Date(start + seconds * 1000).toISOString();
const sla: Sla = { policyId: 'p1', responseSeconds: 300, resolutionSeconds: 1800, warningPercent: 80, responseDueAt: at(300), resolutionDueAt: at(1800), firstAcknowledgedAt: null, firstResponseAt: null, responseBreachedAt: null, resolutionBreachedAt: null, responseStatus: 'TRACKING', resolutionStatus: 'TRACKING', evaluatedAt: at(60), issue: null, serverTime: at(60) };
const incident: Incident = { id: 1, reference: 'INC-000001', version: 0, title: 'Network down', description: 'No connectivity', reporter: { id: 'e1', displayName: 'Employee' }, service, category, impact: 3, urgency: 2, priority: 'P1', status: 'ASSIGNED', technician: { id: 't1', displayName: 'Engineer' }, resolution: null, feedback: null, feedbackRating: null, createdAt: at(0), updatedAt: at(0), acknowledgedAt: null, resolvedAt: null, verifiedAt: null, closedAt: null, priorityScore: 18, priorityCriticality: 3, assignmentIssue: null, sla };
describe('SLA presentation', () => {
  it('formats calendar durations without pausing nights or weekends', () => { expect(duration(90061000)).toBe('1d 1h 1m 1s'); });
  it('distinguishes approaching, exact deadline and breach', () => {
    expect(slaOutcome(at(300), null, null, 300, 80, start + 239000)).toBe('TRACKING');
    expect(slaOutcome(at(300), null, null, 300, 80, start + 240000)).toBe('APPROACHING');
    expect(slaOutcome(at(300), null, null, 300, 80, start + 300000)).toBe('APPROACHING');
    expect(slaOutcome(at(300), null, null, 300, 80, start + 300001)).toBe('BREACHED');
  });
  it('keeps prior breach indicators after a deadline extension', () => { expect(slaOutcome(at(86400), at(100), at(60), 86400, 80, start + 200000)).toBe('BREACHED'); });
  it('does not show a fabricated SLA without configured deadlines', () => { expect(slaOutcome(null, null, null, null, null, start)).toBe('UNCONFIGURED'); });
  it('stops the response countdown after actual acknowledgement', () => { expect(slaOutcome(at(300), at(200), null, 300, 80, start + 900000)).toBe('MET'); });
  it('ticks a server-anchored countdown and cleans up its timer', () => {
    vi.useFakeTimers(); vi.setSystemTime(start + 60000);
    const view = render(<SlaPanel incident={incident}/>);
    try { expect(screen.getByText('Due in 4m 0s')).toBeInTheDocument(); act(() => { vi.advanceTimersByTime(1000); }); expect(screen.getByText('Due in 3m 59s')).toBeInTheDocument(); }
    finally { view.unmount(); vi.useRealTimers(); }
  });
});
