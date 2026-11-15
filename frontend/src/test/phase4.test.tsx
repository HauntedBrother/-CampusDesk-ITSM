import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NotificationCenter } from '@/notifications/NotificationCenter';
import { IntelligentSuggestions } from '@/intelligence/Suggestions';
import { IncidentForm } from '@/incidents/IncidentForm';
import { RulesPage } from '@/intelligence/RulesPage';
const { auth } = vi.hoisted(() => ({ auth: { user: { role: 'EMPLOYEE' }, request: vi.fn() } }));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
const notification = { id: 'n1', incidentId: 7, reference: 'INC-000007', action: 'CREATED', message: 'INC-000007: created', createdAt: '2026-11-15T10:00:00Z', readAt: null };
const suggestions = { duplicates: [{ id: 7, reference: 'INC-000007', title: 'Network failure', similarity: 0.9, status: 'NEW' }], categories: [{ categoryId: 'network', categoryName: 'Network', score: 10, matchedKeywords: ['wifi'] }], method: 'Trigrams' };
beforeEach(() => { auth.user.role = 'EMPLOYEE'; auth.request.mockReset(); });
describe('Notification center', () => {
  it('loads real inbox data and persists read state', async () => {
    auth.request.mockImplementation(async (path: string) => path.endsWith('/read') ? { ...notification, readAt: '2026-11-15T11:00:00Z' } : { content: [notification], totalElements: 1, unreadCount: 1, totalPages: 1 });
    render(<MemoryRouter><NotificationCenter/></MemoryRouter>);
    expect(await screen.findByText('1 unread')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: notification.message })).toHaveAttribute('href', '/incidents/7');
    fireEvent.click(screen.getByText('Mark read'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/notifications/n1/read', { method: 'POST', body: '{"read":true}' }));
    expect(screen.queryByText('Simulated email logs')).not.toBeInTheDocument();
  });
  it('filters unread notifications through the API', async () => {
    auth.request.mockResolvedValue({ content: [], totalElements: 0, unreadCount: 0, totalPages: 0 });
    render(<MemoryRouter><NotificationCenter/></MemoryRouter>);await screen.findByText('No notifications yet');fireEvent.click(screen.getByLabelText('Unread only'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/notifications?unreadOnly=true&page=0&size=20'));
    expect(await screen.findByText('You are all caught up')).toBeInTheDocument();
  });
  it('shows delivery failure with retry instead of a fake empty inbox', async () => {
    auth.request.mockRejectedValue(new Error('Database unavailable'));render(<MemoryRouter><NotificationCenter/></MemoryRouter>);
    expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable');expect(screen.getByText('Retry')).toBeInTheDocument();expect(screen.queryByText('No notifications yet')).not.toBeInTheDocument();
  });
  it('lets administrators inspect persisted simulated email records', async () => {
    auth.user.role = 'ADMIN';auth.request.mockImplementation(async (path: string) => path.includes('email-deliveries') ? { content: [{ id: 'e1', incidentId: 7, email: 'student@uni.edu', subject: 'INC-000007: created', status: 'SIMULATED', deliveredAt: notification.createdAt }], totalPages: 1 } : { content: [], totalElements: 0, unreadCount: 0, totalPages: 0 });
    render(<MemoryRouter><NotificationCenter/></MemoryRouter>);await screen.findByText('No notifications yet');fireEvent.click(screen.getByText('Simulated email logs'));
    expect(await screen.findByText('student@uni.edu')).toBeInTheDocument();expect(screen.getByText('SIMULATED')).toBeInTheDocument();
  });
});
describe('Intelligent suggestions', () => {
  it('suggests category and duplicate links without merging', async () => {
    auth.request.mockResolvedValue(suggestions);const recommend = vi.fn();render(<MemoryRouter><IntelligentSuggestions title="Wi-Fi failure" description="Cannot connect to library wireless network" serviceId="campus" onCategory={recommend}/></MemoryRouter>);
    expect(await screen.findByText('Network', {}, { timeout: 2000 })).toBeInTheDocument();expect(recommend).toHaveBeenCalledWith('network', true);
    expect(screen.getByRole('link', { name: 'INC-000007 · Network failure' })).toHaveAttribute('href', '/incidents/7');fireEvent.click(screen.getByText('Use Network'));expect(recommend).toHaveBeenCalledWith('network', false);
  });
  it('does not request suggestions for incomplete reports', () => { render(<MemoryRouter><IntelligentSuggestions title="" description="" serviceId="campus"/></MemoryRouter>);expect(auth.request).not.toHaveBeenCalled(); });
  it('preserves a manually corrected category when new recommendations arrive', async () => {
    auth.request.mockImplementation(async (path: string) => path.includes('/services') ? [{ id: 'campus', name: 'Campus', active: true }] : path.includes('/categories') ? [{ id: 'network', name: 'Network', active: true }, { id: 'software', name: 'Software', active: true }] : suggestions);
    render(<MemoryRouter><IncidentForm/></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText('Short title'), { target: { value: 'Wi-Fi failure' } });fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Wireless network unavailable' } });fireEvent.change(screen.getByLabelText('Affected service'), { target: { value: 'campus' } });
    await waitFor(() => expect(screen.getByLabelText('Category')).toHaveValue('network'), { timeout: 2000 });fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'software' } });fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Wireless network unavailable again' } });
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/intelligence/suggestions', expect.objectContaining({ body: expect.stringContaining('again') })), { timeout: 2000 });expect(screen.getByLabelText('Category')).toHaveValue('software');
  });
});
describe('Category rule configuration', () => {
  it('persists the administrator rule and its weight', async () => {
    auth.user.role = 'ADMIN';auth.request.mockImplementation(async (path: string) => path === '/catalog/categories' ? [{ id: 'network', name: 'Network', active: true }] : []);
    render(<MemoryRouter><RulesPage/></MemoryRouter>);
    fireEvent.change(await screen.findByLabelText('Category'), { target: { value: 'network' } });fireEvent.change(screen.getByLabelText('Keyword or phrase'), { target: { value: 'wifi' } });fireEvent.change(screen.getByLabelText('Rule weight'), { target: { value: '25' } });fireEvent.click(screen.getByText('Save rule'));
    await waitFor(() => expect(auth.request).toHaveBeenCalledWith('/intelligence/rules', { method: 'PUT', body: JSON.stringify({ categoryId: 'network', keyword: 'wifi', weight: 25, active: true }) }));
  });
  it('does not show missing rules as an empty success when loading fails', async () => {
    auth.request.mockRejectedValue(new Error('Rules unavailable'));render(<MemoryRouter><RulesPage/></MemoryRouter>);expect(await screen.findByRole('alert')).toHaveTextContent('Rules unavailable');expect(screen.getByText('Retry')).toBeInTheDocument();expect(screen.queryByText('No category rules configured.')).not.toBeInTheDocument();
  });
});
