import { useState, type FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { ArrowRight, Check, Command, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
export function AuthPage({ register = false }: { register?: boolean }) {
  const { user, authenticate, loading } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (loading) return <main className="grid min-h-screen place-items-center" role="status">Loading…</main>;
  if (user) return <Navigate to={`/${user.role.toLowerCase()}`} replace/>;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('');
    const data = Object.fromEntries(new FormData(event.currentTarget)) as Record<string, string>;
    try { await authenticate(register ? 'register' : 'login', data); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to connect. Please try again.'); }
    finally { setBusy(false); }
  }
  return <main className="auth-grid min-h-screen">
    <section className="auth-story relative flex flex-col justify-between overflow-hidden bg-[#102d2c] p-10 text-white lg:p-16">
      <div className="relative z-10 flex items-center gap-3 text-xl font-bold"><span className="rounded-xl bg-white/10 p-2"><Command size={25}/></span> CampusDesk<span className="ml-2 rounded border border-white/20 px-2 py-1 text-[10px] font-medium tracking-widest text-teal-100">UNIVERSITY IT</span></div>
      <div className="relative z-10 my-16 max-w-lg"><div className="mb-6 text-xs font-semibold uppercase tracking-[.22em] text-teal-300">A better connected campus</div><h1 className="text-4xl font-semibold leading-tight tracking-tight lg:text-6xl">Great support.<br/><span className="text-teal-300">Less friction.</span></h1><p className="mt-6 max-w-sm text-base leading-7 text-slate-300">One shared space for your university’s IT services. Get the right help, with a clear path forward.</p><div className="mt-10 flex gap-3 text-sm text-teal-100"><Check size={18}/> Secure access for every campus role</div></div>
      <p className="relative z-10 text-xs text-slate-400">Intelligent IT Service Management · Academic year 2026–2027</p>
      <div className="orbit orbit-one"/><div className="orbit orbit-two"/>
    </section>
    <section className="flex items-center justify-center bg-[#f8faf9] px-6 py-12"><div className="w-full max-w-[390px]">
      <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-teal-100 bg-teal-50 px-3 py-1.5 text-xs font-medium text-teal-800"><ShieldCheck size={15}/> Your campus. Your workspace.</div>
      <h2 className="text-3xl font-semibold tracking-tight text-slate-900">{register ? 'Join your campus' : 'Welcome back'}</h2><p className="mb-8 mt-3 text-sm leading-6 text-slate-500">{register ? 'Create an employee account to access university IT support.' : 'Sign in to your university IT workspace.'}</p>
      <form onSubmit={submit} className="space-y-5">
        {register && <div><label htmlFor="displayName" className="field-label">Full name</label><Input id="displayName" name="displayName" required maxLength={100} autoComplete="name" placeholder="Your full name"/></div>}
        <div><label htmlFor="email" className="field-label">Email address</label><Input id="email" name="email" type="email" required maxLength={254} autoComplete="email" placeholder="you@university.edu"/></div>
        <div><label htmlFor="password" className="field-label">Password</label><Input id="password" name="password" type="password" required minLength={register ? 12 : undefined} maxLength={72} pattern={register ? '[\x20-\x7E]+' : undefined} autoComplete={register ? 'new-password' : 'current-password'} placeholder={register ? 'At least 12 characters' : 'Enter your password'}/>{register && <p className="mt-2 text-xs text-slate-500">12–72 printable ASCII characters.</p>}</div>
        {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
        <Button className="w-full" disabled={busy}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}<ArrowRight size={16}/></Button>
      </form>
      <p className="mt-7 text-center text-sm text-slate-500">{register ? 'Already have an account?' : 'New to CampusDesk?'} <Link className="font-semibold text-teal-800 hover:underline" to={register ? '/login' : '/register'}>{register ? 'Sign in' : 'Create an account'}</Link></p>
      <div className="mt-10 border-t border-slate-200 pt-6 text-center text-xs leading-5 text-slate-400">Access is managed by your university.<br/>Technician and administrator accounts are provisioned separately.</div>
    </div></section>
  </main>;
}
