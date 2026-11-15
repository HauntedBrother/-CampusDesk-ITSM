import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { ReportingDashboard } from '@/reporting/ReportingDashboard';
import { Activity, ChevronRight, Command, LayoutDashboard, LogOut, Menu, PanelLeftClose, ShieldCheck, Sparkles, UserRound, Wrench } from 'lucide-react';
import { NotificationBell } from '@/notifications/NotificationCenter';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/ui/button';
import type { Role } from '@/lib/api';
const roleDetails = {
  EMPLOYEE: { title: 'Your support workspace', intro: 'A clear starting point for your campus IT support.', label: 'Employee', icon: UserRound, cards: ['Report an issue', 'Follow your requests', 'Review a resolution'] },
  TECHNICIAN: { title: 'Your service workspace', intro: 'A focused space to keep campus services moving.', label: 'Technician', icon: Wrench, cards: ['Your assignments', 'Investigation workspace', 'Workload overview'] },
  ADMIN: { title: 'Your operations workspace', intro: 'A shared view of the people and services behind campus IT.', label: 'Administrator', icon: ShieldCheck, cards: ['People & access', 'Service configuration', 'Operational reporting'] },
};
export function AppLayout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (!user) return null;
  const detail = roleDetails[user.role];
  async function signOut() { setBusy(true); setError(''); try { await logout(); } catch { setError('Unable to sign out. Check your connection and retry.'); } finally { setBusy(false); } }
  return <div className="min-h-screen bg-[#f5f7f8] text-slate-800">
    {open && <button aria-label="Close navigation overlay" className="fixed inset-0 z-30 bg-slate-900/40 lg:hidden" onClick={() => setOpen(false)}/>}
    <aside className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col bg-[#102d2c] px-5 py-7 text-white transition-transform lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
      <div className="mb-10 flex items-center gap-3 px-2 text-xl font-bold"><Command className="text-teal-300"/> CampusDesk<Button variant="ghost" size="icon" className="ml-auto lg:hidden" onClick={() => setOpen(false)} aria-label="Close navigation"><PanelLeftClose size={18}/></Button></div>
      <div className="px-3 text-[10px] uppercase tracking-[.2em] text-teal-200/50">Workspace</div>
      <nav className="mt-3 space-y-2"><NavLink to={`/${user.role.toLowerCase()}`} onClick={() => setOpen(false)} className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium ${isActive ? 'bg-white/10 text-teal-200' : 'text-slate-300 hover:bg-white/5'}`}><LayoutDashboard size={18}/> Overview</NavLink><NavLink to="/incidents" onClick={() => setOpen(false)} className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium ${isActive ? 'bg-white/10 text-teal-200' : 'text-slate-300 hover:bg-white/5'}`}><Activity size={18}/>Incidents</NavLink>{user.role === 'ADMIN' && <NavLink to="/catalog" onClick={() => setOpen(false)} className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium ${isActive ? 'bg-white/10 text-teal-200' : 'text-slate-300 hover:bg-white/5'}`}><Wrench size={18}/>Services & categories</NavLink>}{user.role === 'ADMIN' && <NavLink to="/automation" onClick={() => setOpen(false)} className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-3 text-sm font-medium ${isActive ? 'bg-white/10 text-teal-200' : 'text-slate-300 hover:bg-white/5'}`}><ShieldCheck size={18}/>Assignment & SLA</NavLink>}<NavLink to="/notifications" onClick={() => setOpen(false)} className="flex items-center gap-3 rounded-lg px-3 py-3 text-sm text-slate-300 hover:bg-white/10">Notifications</NavLink>{user.role === 'ADMIN' && <NavLink to="/category-rules" onClick={() => setOpen(false)} className="flex items-center gap-3 rounded-lg px-3 py-3 text-sm text-slate-300 hover:bg-white/10"><Sparkles size={18}/>Category rules</NavLink>}</nav>
      <div className="mt-8 px-3"><div className="text-[10px] uppercase tracking-[.2em] text-teal-200/50">Your access</div><div className="mt-4 flex items-center gap-2 text-sm text-slate-300"><detail.icon size={16}/>{detail.label}</div></div>
      <div className="mt-auto rounded-xl border border-white/10 bg-white/5 p-4"><div className="flex items-center gap-2 text-xs font-semibold text-teal-200"><ShieldCheck size={16}/> University IT</div><p className="mt-2 text-xs leading-5 text-slate-400">A secure workspace for a better supported campus.</p><div className="mt-4 text-[10px] tracking-wide text-slate-500">2026–2027 · PHASE 05</div></div>
    </aside>
    <div className="lg:pl-64"><header className="flex min-h-20 items-center justify-between gap-3 border-b border-slate-200 bg-white px-5 md:px-10"><div className="flex items-center gap-3"><Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setOpen(true)} aria-label="Open navigation"><Menu size={20}/></Button><span className="text-sm text-slate-400">Workspace</span><ChevronRight size={14} className="text-slate-300"/><span className="text-sm font-medium">Overview</span></div><div className="flex items-center gap-3"><NotificationBell/><div className="hidden text-right sm:block"><p className="text-sm font-semibold">{user.displayName}</p><p className="text-xs text-slate-500">{detail.label}</p></div><div className="grid h-10 w-10 place-items-center rounded-full bg-teal-50 text-sm font-semibold text-teal-800">{user.displayName.slice(0, 1).toUpperCase()}</div><Button variant="ghost" size="icon" aria-label="Sign out" title="Sign out" disabled={busy} onClick={signOut}><LogOut size={18}/></Button></div></header>
    {error && <p role="alert" className="mx-5 mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}<main className="mx-auto max-w-7xl px-5 py-8 md:px-10 md:py-10"><Outlet/></main><footer className="px-5 pb-6 text-xs text-slate-400 md:px-10">CampusDesk · Intelligent IT Service Management</footer></div>
  </div>;
}
export function Dashboard({ role: _role }: { role: Role }) { return <ReportingDashboard/>; }
