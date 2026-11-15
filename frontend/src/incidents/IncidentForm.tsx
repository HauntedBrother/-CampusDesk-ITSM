import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ErrorMessage, Field, Select, Textarea } from './components';
import { IntelligentSuggestions } from '@/intelligence/Suggestions';
import type { Category, Detail, Service } from './types';

export function IncidentForm({ edit = false }: { edit?: boolean }) {
  const { id } = useParams(); const { request, user } = useAuth(); const navigate = useNavigate();
  const [title, setTitle] = useState(''); const [description, setDescription] = useState(''); const [serviceId, setServiceId] = useState(''); const [categoryId, setCategoryId] = useState(''); const categoryTouched = useRef(false);
  const recommend = useCallback((value: string, automatic: boolean) => { if (automatic && (edit || categoryTouched.current)) return; if (!automatic) categoryTouched.current = true; setCategoryId(value); }, [edit]);
  const [catalog, setCatalog] = useState<{ services: Service[]; categories: Category[] } | null>(null);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [loading, setLoading] = useState(true); const [attempt, setAttempt] = useState(0);
  useEffect(() => { let active = true; setLoading(true); setError(''); setCatalog(null); setDetail(null);
    Promise.all([request<Service[]>('/catalog/services'), request<Category[]>('/catalog/categories'), edit ? request<Detail>(`/incidents/${id}`) : Promise.resolve(null)])
      .then(([services, categories, value]) => { if (!active) return; if (value && !value.actions.includes('UPDATE')) throw new Error('This incident cannot be edited in its current state.'); if (value) { if (!services.some(s => s.id === value.incident.service.id)) services.push(value.incident.service); if (!categories.some(c => c.id === value.incident.category.id)) categories.push(value.incident.category); } setCatalog({ services, categories }); setDetail(value); setTitle(value?.incident.title ?? ''); setDescription(value?.incident.description ?? ''); setServiceId(value?.incident.service.id ?? ''); setCategoryId(value?.incident.category.id ?? ''); categoryTouched.current = edit; })
      .catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; };
  }, [request, id, edit, attempt]);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget)); setBusy(true); setError('');
    try { const value = await request<Detail>(edit ? `/incidents/${id}` : '/incidents', { method: edit ? 'PUT' : 'POST', body: JSON.stringify({ ...values, impact: Number(values.impact), urgency: Number(values.urgency) }) }); navigate(`/incidents/${value.incident.id}`); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to save incident'); } finally { setBusy(false); }
  }
  if (!edit && user?.role !== 'EMPLOYEE') return <ErrorMessage error="Only employees can report incidents."/>;
  const incident = detail?.incident;
  return <div className="mx-auto max-w-3xl"><Link to={edit ? `/incidents/${id}` : '/incidents'} className="inline-flex items-center gap-2 text-sm text-slate-500 hover:text-teal-700"><ArrowLeft size={16}/>Back to {edit ? 'incident' : 'incidents'}</Link><h1 className="mb-2 mt-6 text-3xl font-semibold tracking-tight">{edit ? 'Update incident' : 'Report an incident'}</h1><p className="mb-7 text-sm text-slate-500">Tell us what happened and which campus service is affected.</p><ErrorMessage error={error}/>{loading ? <p role="status">Loading form…</p> : !catalog || (edit && !detail) ? <Button variant="outline" onClick={() => setAttempt(n => n + 1)}>Retry</Button> : <form onSubmit={submit} className="space-y-5 rounded-xl border border-slate-200 bg-white p-6 md:p-8">
    {(!catalog.services.length || !catalog.categories.length) && <p role="status" className="rounded-lg bg-amber-50 p-4 text-sm text-amber-800">An administrator must configure at least one active service and category before incidents can be reported.</p>}
    <Field title="Short title" htmlFor="title"><Input id="title" name="title" required maxLength={200} defaultValue={incident?.title} onChange={e => setTitle(e.target.value)} placeholder="For example, unable to connect to campus Wi-Fi"/></Field>
    <Field title="Description" htmlFor="description"><Textarea id="description" name="description" required maxLength={10000} defaultValue={incident?.description} onChange={e => setDescription(e.target.value)} placeholder="Describe the issue, when it started and any error messages."/></Field>
    <div className="grid gap-5 sm:grid-cols-2"><Field title="Affected service" htmlFor="serviceId"><Select id="serviceId" name="serviceId" required defaultValue={incident?.service.id ?? ''} onChange={e => setServiceId(e.target.value)}><option value="" disabled>Select a service</option>{catalog.services.map(s => <option key={s.id} value={s.id}>{s.name}{!s.active ? ' (inactive)' : ''}</option>)}</Select></Field><Field title="Category" htmlFor="categoryId"><Select id="categoryId" name="categoryId" required value={categoryId} onChange={e => { categoryTouched.current = true; setCategoryId(e.target.value); }}><option value="" disabled>Select a category</option>{catalog.categories.map(c => <option key={c.id} value={c.id}>{c.name}{!c.active ? ' (inactive)' : ''}</option>)}</Select></Field></div>
    <IntelligentSuggestions title={title} description={description} serviceId={serviceId} incidentId={edit ? Number(id) : undefined} onCategory={recommend}/>
    <div className="grid gap-5 sm:grid-cols-2"><Field title="Impact" htmlFor="impact"><Select id="impact" name="impact" defaultValue={incident?.impact ?? 1}>{[[1, 'Low — individual user'], [2, 'Medium — several users'], [3, 'High — campus-wide']].map(([v, text]) => <option value={v} key={v}>{text}</option>)}</Select></Field><Field title="Urgency" htmlFor="urgency"><Select id="urgency" name="urgency" defaultValue={incident?.urgency ?? 1}>{[[1, 'Low — can wait'], [2, 'Medium — work is affected'], [3, 'High — work is blocked']].map(([v, text]) => <option value={v} key={v}>{text}</option>)}</Select></Field></div>
    <p className="rounded-lg bg-slate-50 p-4 text-xs leading-6 text-slate-500">{edit ? 'Changes are recorded in the incident history.' : 'The backend calculates priority and SLA targets, then attempts qualified assignment using the configured strategy.'} Deadlines use 24/7 calendar time from the original report; unassigned incidents display the reason.</p><div className="flex gap-3 border-t border-slate-100 pt-5"><Button disabled={busy || !catalog.services.length || !catalog.categories.length}>{busy ? 'Saving…' : edit ? 'Save changes' : 'Submit incident'}</Button><Button asChild variant="outline"><Link to={edit ? `/incidents/${id}` : '/incidents'}>Cancel</Link></Button></div>
  </form>}</div>;
}
