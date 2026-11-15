import { describe, it, expect, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { api, ApiError } from '@/lib/api';
import { ProtectedRoute } from '@/auth/ProtectedRoute';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
describe('API client', () => {
  it('sends bearer tokens to the backend', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ role: 'EMPLOYEE' }), { status: 200 }));
    await api('/auth/me', 'real-token');
    expect(fetcher).toHaveBeenCalledWith('/api/auth/me', expect.objectContaining({ headers: { Authorization: 'Bearer real-token' } }));
  });
  it('preserves backend authorization errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 403 }));
    await expect(api('/dashboards/admin', 'employee-token')).rejects.toMatchObject({ status: 403 });
  });
  it('handles empty logout responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    await expect(api('/auth/logout', 'token', { method: 'POST' })).resolves.toBeUndefined();
  });
  it('returns validation errors from the server', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ detail: 'Invalid input' }), { status: 400 }));
    await expect(api('/auth/register')).rejects.toEqual(new ApiError(400, 'Invalid input'));
  });
});
describe('Protected routes', () => {
  it('redirects anonymous users to login', async () => {
    render(<MemoryRouter initialEntries={['/admin']}><AuthProvider><Routes><Route path="/login" element={<p>Sign in page</p>}/><Route element={<ProtectedRoute role="ADMIN"/>}><Route path="/admin" element={<p>Admin workspace</p>}/></Route></Routes></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('Sign in page')).toBeInTheDocument();
    expect(screen.queryByText('Admin workspace')).not.toBeInTheDocument();
  });
  it('rejects stale persisted authentication', async () => {
    sessionStorage.setItem('campusdesk.session', 'invalid');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 401 }));
    render(<MemoryRouter initialEntries={['/admin']}><AuthProvider><Routes><Route path="/login" element={<p>Sign in page</p>}/><Route element={<ProtectedRoute/>}><Route path="/admin" element={<p>Admin workspace</p>}/></Route></Routes></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('Sign in page')).toBeInTheDocument();
    expect(sessionStorage.getItem('campusdesk.session')).toBeNull();
  });
  it('redirects a signed-in employee away from the administrator route', async () => {
    const token = `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`;
    sessionStorage.setItem('campusdesk.session', token);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ id: '1', displayName: 'Employee', email: 'employee@uni.edu', role: 'EMPLOYEE' }), { status: 200 }));
    render(<MemoryRouter initialEntries={['/admin']}><AuthProvider><Routes><Route path="/employee" element={<p>Employee workspace</p>}/><Route element={<ProtectedRoute role="ADMIN"/>}><Route path="/admin" element={<p>Admin workspace</p>}/></Route></Routes></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('Employee workspace')).toBeInTheDocument();
    expect(screen.queryByText('Admin workspace')).not.toBeInTheDocument();
  });
});

function AuthHarness() {
  const { user, authenticate, logout } = useAuth();
  return <><p>{user ? user.displayName : 'Signed out'}</p><button onClick={() => void authenticate('login', { email: 'employee@uni.edu', password: 'test-password' })}>Log in</button><button onClick={() => void logout()}>Log out</button></>;
}
describe('Authentication state', () => {
  it('stores a login session and clears it after server logout', async () => {
    const token = `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.signature`;
    const fetcher = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => String(url).endsWith('/login')
      ? new Response(JSON.stringify({ accessToken: token, expiresAt: new Date(Date.now() + 3600000).toISOString(), user: { id: '1', displayName: 'Employee', email: 'employee@uni.edu', role: 'EMPLOYEE' } }), { status: 200 })
      : new Response(null, { status: 204 }));
    render(<AuthProvider><AuthHarness/></AuthProvider>);
    fireEvent.click(screen.getByText('Log in'));
    expect(await screen.findByText('Employee')).toBeInTheDocument();
    expect(sessionStorage.getItem('campusdesk.session')).toBe(token);
    fireEvent.click(screen.getByText('Log out'));
    await waitFor(() => expect(sessionStorage.getItem('campusdesk.session')).toBeNull());
    expect(screen.getByText('Signed out')).toBeInTheDocument();
    expect(fetcher).toHaveBeenLastCalledWith('/api/auth/logout', expect.objectContaining({ method: 'POST', headers: { Authorization: `Bearer ${token}` } }));
  });
});
