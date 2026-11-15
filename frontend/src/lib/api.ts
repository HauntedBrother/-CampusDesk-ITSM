export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'ADMIN';
export interface User { id: string; displayName: string; email: string; role: Role }
export interface AuthResponse { accessToken: string; expiresAt: string; user: User }
export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}
export async function api<T>(path: string, token?: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, { ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ApiError(response.status, problem?.detail ?? (response.status === 401 ? 'Your session has expired. Please sign in again.' : response.status === 403 ? 'You do not have permission to access this resource.' : 'The request could not be completed. Please try again.'));
  }
  if (response.status === 204) return undefined as T;
  return response.headers.get('content-type')?.includes('text/csv') ? await response.text() as T : response.json();
}
