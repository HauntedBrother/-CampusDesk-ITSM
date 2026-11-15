import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import type { Role } from '@/lib/api';
export function ProtectedRoute({ role }: { role?: Role }) {
  const { user, loading } = useAuth();
  if (loading) return <main className="grid min-h-screen place-items-center" role="status">Loading your workspace…</main>;
  if (!user) return <Navigate to="/login" replace/>;
  if (role && user.role !== role) return <Navigate to={`/${user.role.toLowerCase()}`} replace/>;
  return <Outlet/>;
}
