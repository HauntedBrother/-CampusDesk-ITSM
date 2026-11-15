import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { IncidentList } from '@/incidents/IncidentList';
import { IncidentForm } from '@/incidents/IncidentForm';
import { IncidentDetail } from '@/incidents/IncidentDetail';
import { CatalogPage } from '@/incidents/CatalogPage';
import type { Detail, Incident } from '@/incidents/types';

const { auth } = vi.hoisted(() => ({ auth: { user: { id: 'employee-id', displayName: 'Student', role: 'EMPLOYEE' }, request: vi.fn() } }));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
const service = { id: '00000000-0000-0000-0000-000000000001', name: 'Campus Wi-Fi', description: 'Network access', businessCriticality: 3, active: true };
const category = { id: '00000000-0000-0000-0000-000000000002', name: 'Network', description: 'Connectivity', active: true };
const incident: Incident = { id: 12, reference: 'INC-000012', version: 0, title: 'Library Wi-Fi unavailable', description: 'Cannot connect in library', reporter: { id: 'employee-id', displayName: 'Student' }, service, category, impact: 2, urgency: 3, priority: null, priorityScore: null, priorityCriticality: null, assignmentIssue: null, sla: null, status: 'NEW', technician: null, resolution: null, feedback: null, feedbackRating: null, createdAt: '2026-11-15T10:00:00Z', updatedAt: '2026-11-15T10:00:00Z', acknowledgedAt: null, resolvedAt: null, verifiedAt: null, closedAt: null };
function detail(value: Partial<Incident> = {}, actions = ['COMMENT', 'UPDATE']): Detail { return { incident: { ...incident, ...value }, comments: [], history: [], actions }; }
function catalogs(path: string) { if (path.startsWith('/catalog/services')) return [service]; if (path.startsWith('/catalog/categories')) return [category]; if (path.startsWith('/catalog/technicians')) return []; return undefined; }
beforeEach(() => { auth.user.role = 'EMPLOYEE'; auth.request.mockReset(); });

describe('Incident list', () => {
  it('renders API incidents and unset priority without fabricated metrics', async () => {
    auth.request.mockImplementation(async (path: string) => catalogs(path) ?? { content: [incident], totalElements: 1, totalPages: 1, page: 0, size: 20 });
    render(<MemoryRouter><IncidentList/></MemoryRouter>);
    expect(await screen.findByText('Library Wi-Fi unavailable')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'INC-000012' })).toHaveAttribute('href', '/incidents/12');
    expect(screen.getAllByText('Not calculated').length).toBeGreaterThan(0);
  });
  it('submits all seven supported filter criteria to the backend', async () => {
    auth.request.mockImplementation(async (path: string) => catalogs(path) ?? { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
    render(<MemoryRouter><IncidentList/></MemoryRouter>);
    await screen.findByText('No incidents found');
    fireEvent.change(screen.getByLabelText('Incident ID'), { target: { value: 'INC-000012' } });
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'ASSIGNED' } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: category.id } });
    fireEvent.change(screen.getByLabelText('Service'), { target: { value: service.id } });
    fireEvent.change(screen.getByLabelText('Priority'), { target: { value: 'UNSET' } });
    fireEvent.change(screen.getByLabelText('Technician ID'), { target: { value: '00000000-0000-0000-0000-000000000003' } });
    fireEvent.change(screen.getByLabelText('Created from (UTC)'), { target: { value: '2026-11-01' } });
    fireEvent.change(screen.getByLabelText('Created through (UTC)'), { target: { value: '2026-11-15' } });
    fireEvent.click(screen.getByText('Apply filters'));
    await waitFor(() => expect(auth.request.mock.calls.some(([path]) => String(path).includes('status=ASSIGNED') && String(path).includes('categoryId='))).toBe(true));
    const path = auth.request.mock.calls.map(([p]) => String(p)).find(p => p.includes('status=ASSIGNED'))!;
    const params = new URLSearchParams(path.split('?')[1]);
    expect(Object.fromEntries(params)).toMatchObject({ id: 'INC-000012', status: 'ASSIGNED', categoryId: category.id, serviceId: service.id, priority: 'UNSET', createdFrom: '2026-11-01', createdTo: '2026-11-15', technicianId: '00000000-0000-0000-0000-000000000003' });
  });
  it('shows backend failures without pretending that the result is empty', async () => {
    auth.request.mockImplementation(async (path: string) => { const result = catalogs(path); if (result) return result; throw new Error('Incident access denied'); });
    render(<MemoryRouter><IncidentList/></MemoryRouter>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Incident access denied');
    expect(screen.queryByText('No incidents found')).not.toBeInTheDocument();
  });
});
describe('Incident creation', () => {
  it('sends validated fields and numeric scores then navigates to saved detail', async () => {
    auth.request.mockImplementation(async (path: string) => catalogs(path) ?? detail());
    render(<MemoryRouter initialEntries={['/incidents/new']}><Routes><Route path="/incidents/new" element={<IncidentForm/>}/><Route path="/incidents/:id" element={<p>Saved incident detail</p>}/></Routes></MemoryRouter>);
    await screen.findByLabelText('Short title');
    fireEvent.change(screen.getByLabelText('Short title'), { target: { value: 'Wi-Fi down' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Cannot connect' } });
    fireEvent.change(screen.getByLabelText('Affected service'), { target: { value: service.id } });
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: category.id } });
    fireEvent.change(screen.getByLabelText('Impact'), { target: { value: '3' } });
    fireEvent.click(screen.getByText('Submit incident'));
    expect(await screen.findByText('Saved incident detail')).toBeInTheDocument();
    const call = auth.request.mock.calls.find(([path, options]) => path === '/incidents' && options?.method === 'POST')!;
    expect(JSON.parse(call[1].body)).toMatchObject({ title: 'Wi-Fi down', impact: 3, urgency: 1, serviceId: service.id, categoryId: category.id });
  });
  it('explains missing catalog setup and prevents submission', async () => {
    auth.request.mockResolvedValue([]);
    render(<MemoryRouter><IncidentForm/></MemoryRouter>);
    expect(await screen.findByText(/administrator must configure/)).toBeInTheDocument();
    expect(screen.getByText('Submit incident')).toBeDisabled();
  });
});
describe('Incident detail', () => {
  it('uses the acknowledge command and refreshes available actions from its response', async () => {
    auth.user.role = 'TECHNICIAN';
    auth.request.mockImplementation(async (path: string) => path.endsWith('/acknowledge') ? detail({ status: 'ACKNOWLEDGED' }, ['START', 'COMMENT']) : detail({ status: 'ASSIGNED' }, ['ACKNOWLEDGE', 'COMMENT']));
    render(<MemoryRouter initialEntries={['/incidents/12']}><Routes><Route path="/incidents/:id" element={<IncidentDetail/>}/></Routes></MemoryRouter>);
    fireEvent.click(await screen.findByText('Acknowledge incident'));
    expect(await screen.findByText('Start investigation')).toBeInTheDocument();
    expect(auth.request).toHaveBeenCalledWith('/incidents/12/acknowledge', { method: 'POST' });
  });
  it('posts an employee comment and never offers internal notes or admin override', async () => {
    auth.request.mockResolvedValue(detail({}, ['COMMENT']));
    render(<MemoryRouter initialEntries={['/incidents/12']}><Routes><Route path="/incidents/:id" element={<IncidentDetail/>}/></Routes></MemoryRouter>);
    const field = await screen.findByLabelText('Add information or a support response');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByText('Administrator override')).not.toBeInTheDocument();
    fireEvent.change(field, { target: { value: 'Still unable to connect' } }); fireEvent.click(screen.getByText('Post update'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/incidents/12/comments', { method: 'POST', body: JSON.stringify({ body: 'Still unable to connect', internal: false }) }));
  });
  it('displays server denial without exposing a previous incident', async () => {
    auth.request.mockRejectedValue(new Error('Incident not found'));
    render(<MemoryRouter initialEntries={['/incidents/12']}><Routes><Route path="/incidents/:id" element={<IncidentDetail/>}/></Routes></MemoryRouter>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Incident not found');
    expect(screen.queryByText('Library Wi-Fi unavailable')).not.toBeInTheDocument();
  });
});
describe('Catalog management', () => {
  it('allows administrators to deactivate a service through a persisted update', async () => {
    auth.user.role = 'ADMIN'; auth.request.mockImplementation(async (path: string) => catalogs(path));
    render(<MemoryRouter><CatalogPage/></MemoryRouter>);
    await screen.findByText('Campus Wi-Fi'); fireEvent.click(screen.getAllByText('Edit')[0]);
    fireEvent.change(screen.getAllByLabelText('Availability')[0], { target: { value: 'false' } }); fireEvent.click(screen.getByText('Save changes'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith(`/catalog/services/${service.id}`, { method: 'PUT', body: JSON.stringify({ name: service.name, description: service.description, active: false, businessCriticality: 3 }) }));
  });
});
