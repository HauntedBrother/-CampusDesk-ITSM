import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowRight, Filter, Plus, Search } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { ExportButton } from '@/reporting/ExportButton';
import { SlaBadge } from './SlaPanel';
import { ErrorMessage, Field, Select, StatusBadge } from './components';
import { dateTime, statuses, type Category, type IncidentPage, type Service } from './types';

export function IncidentList() {
  const [urlParams, setUrlParams] = useSearchParams();
  const { user, request } = useAuth();
  const [data, setData] = useState<IncidentPage | null>(null);
  const [catalog, setCatalog] = useState<{ services: Service[]; categories: Category[] }>({ services: [], categories: [] });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState(() => { const p = new URLSearchParams(urlParams); p.delete('page'); p.delete('size'); return p.toString(); });
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  useEffect(() => { let active = true; Promise.all([request<Service[]>('/catalog/services'), request<Category[]>('/catalog/categories')]).then(([services, categories]) => { if (active) setCatalog({ services, categories }); }).catch(e => { if (active) setError(e.message); }); return () => { active = false; }; }, [request]);
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    request<IncidentPage>(`/incidents?${filters}&page=${page}&size=20`).then(value => { if (active) setData(value); }).catch(e => { if (active) { setError(e.message); setData(null); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [request, filters, page, reload]);
  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const params = new URLSearchParams();
    new FormData(event.currentTarget).forEach((value, key) => { if (String(value).trim()) params.set(key, String(value).trim()); });
    setPage(0); setUrlParams(params); setFilters(params.toString()); setReload(n => n + 1);
  }
  return <><div className="mb-7 flex flex-wrap items-center justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-widest text-teal-700">Campus support</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">{user?.role === 'ADMIN' ? 'All incidents' : user?.role === 'TECHNICIAN' ? 'Assigned incidents' : 'My incidents'}</h1><p className="mt-2 text-sm text-slate-500">{user?.role === 'TECHNICIAN' ? 'Acknowledge, investigate and resolve your assigned work.' : 'Track every step from report to resolution.'}</p></div>{user?.role === 'EMPLOYEE' && <Button asChild><Link to="/incidents/new"><Plus size={16}/>Report an incident</Link></Button>}</div>
    <form key={filters} onSubmit={search} className="rounded-xl border border-slate-200 bg-white p-5"><div className="mb-4 flex items-center gap-2 text-sm font-semibold"><Filter size={16}/> Find an incident</div><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Field title="Incident ID" htmlFor="filter-id"><Input name="id" defaultValue={urlParams.get('id') ?? ''} id="filter-id" placeholder="INC-000001" pattern="([Ii][Nn][Cc]-)?[0-9]{1,18}"/></Field>
      <Field title="Status" htmlFor="filter-status"><Select name="status" defaultValue={urlParams.get('status') ?? ''} id="filter-status"><option value="">All statuses</option>{statuses.map(s => <option key={s}>{s}</option>)}</Select></Field>
      <Field title="Category" htmlFor="filter-category"><Select name="categoryId" defaultValue={urlParams.get('categoryId') ?? ''} id="filter-category"><option value="">All categories</option>{catalog.categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
      <Field title="Service" htmlFor="filter-service"><Select name="serviceId" defaultValue={urlParams.get('serviceId') ?? ''} id="filter-service"><option value="">All services</option>{catalog.services.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></Field>
      <Field title="Priority" htmlFor="filter-priority"><Select name="priority" defaultValue={urlParams.get('priority') ?? ''} id="filter-priority"><option value="">All priorities</option><option value="UNSET">Not calculated</option>{['P1', 'P2', 'P3', 'P4'].map(p => <option key={p}>{p}</option>)}</Select></Field>
      <Field title="Technician ID" htmlFor="filter-technician"><Input name="technicianId" defaultValue={urlParams.get('technicianId') ?? ''} id="filter-technician" placeholder="Technician UUID" pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"/></Field>
      <Field title="Created from (UTC)" htmlFor="filter-from"><Input name="createdFrom" defaultValue={urlParams.get('createdFrom') ?? ''} id="filter-from" type="date"/></Field>
      <Field title="Created through (UTC)" htmlFor="filter-to"><Input name="createdTo" defaultValue={urlParams.get('createdTo') ?? ''} id="filter-to" type="date"/></Field>
      <Field title="SLA status" htmlFor="filter-sla"><Select id="filter-sla" name="slaStatus" defaultValue={urlParams.get('slaStatus') ?? ''}><option value="">All SLA outcomes</option>{['TRACKING','APPROACHING','MET','BREACHED','UNCONFIGURED'].map(s => <option key={s}>{s}</option>)}</Select></Field>
      <Field title="Sort by" htmlFor="filter-sort"><Select id="filter-sort" name="sort" defaultValue={urlParams.get('sort') ?? 'createdAt'}>{[['createdAt','Created'],['id','Incident ID'],['priority','Priority'],['status','Status'],['title','Title'],['service','Service'],['category','Category'],['technician','Technician'],['responseDueAt','Response deadline'],['resolutionDueAt','Resolution deadline'],['resolvedAt','Resolved'],['updatedAt','Updated']].map(([v,t]) => <option value={v} key={v}>{t}</option>)}</Select></Field><Field title="Sort direction" htmlFor="filter-direction"><Select name="direction" id="filter-direction" defaultValue={urlParams.get('direction') ?? 'desc'}><option value="desc">Descending</option><option value="asc">Ascending</option></Select></Field>
    </div><div className="mt-4 flex flex-wrap items-center gap-3"><Button size="sm"><Search size={15}/>Apply filters</Button><Button type="button" variant="outline" size="sm" onClick={() => { setUrlParams({}); setFilters(''); setPage(0); setReload(n => n + 1); }}>Reset</Button><p className="text-xs text-slate-400">Priority is calculated automatically on the server.</p></div></form>
    <div className="mt-5 flex justify-end"><ExportButton filters={filters}/></div><ErrorMessage error={error}/><section className="mt-6 overflow-hidden rounded-xl border border-slate-200 bg-white" aria-busy={loading}>{loading ? <p className="p-10 text-center text-sm text-slate-500" role="status">Loading incidents…</p> : !data ? <div className="p-8"><Button variant="outline" onClick={() => setReload(n => n + 1)}>Retry loading incidents</Button></div> : data.content.length === 0 ? <div className="p-12 text-center"><Search className="mx-auto mb-4 text-slate-300" size={32}/><h2 className="font-semibold">No incidents found</h2><p className="mt-2 text-sm text-slate-500">Your accessible incidents will appear here. Try changing the filters.</p></div> : <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b bg-slate-50 text-xs text-slate-500"><tr>{['Incident', 'Status', 'Service / category', 'Priority', 'SLA', 'Technician', 'Created', ''].map((h, i) => <th key={i} className="px-5 py-4 font-medium">{h}</th>)}</tr></thead><tbody className="divide-y divide-slate-100">{data.content.map(i => <tr key={i.id} className="hover:bg-slate-50/70"><td className="max-w-xs px-5 py-4"><Link className="font-semibold text-teal-800 hover:underline" to={`/incidents/${i.id}`}>{i.reference}</Link><p className="mt-1 line-clamp-2 break-words text-slate-700">{i.title}</p></td><td className="px-5 py-4"><StatusBadge status={i.status}/></td><td className="px-5 py-4">{i.service.name}<p className="mt-1 text-xs text-slate-400">{i.category.name}</p></td><td className="px-5 py-4 text-xs text-slate-500">{i.priority ?? 'Not calculated'}</td><td className="px-5 py-4">{i.sla ? <div className="space-y-1"><div className="flex items-center gap-1"><span className="text-[9px] text-slate-400">Response</span><SlaBadge status={i.sla.responseStatus}/></div><div className="flex items-center gap-1"><span className="text-[9px] text-slate-400">Resolution</span><SlaBadge status={i.sla.resolutionStatus}/></div></div> : <span className="text-xs text-slate-400">Unconfigured</span>}</td><td className="px-5 py-4 text-xs">{i.technician?.displayName ?? 'Unassigned'}</td><td className="whitespace-nowrap px-5 py-4 text-xs text-slate-500">{dateTime(i.createdAt)}</td><td className="px-5 py-4"><Link aria-label={`View ${i.reference}`} to={`/incidents/${i.id}`}><ArrowRight size={17}/></Link></td></tr>)}</tbody></table></div>}</section>
    {data && !loading && <div className="mt-4 flex items-center justify-between text-xs text-slate-500"><span>{data.totalElements} incident{data.totalElements === 1 ? '' : 's'} · Page {data.page + 1} of {Math.max(data.totalPages, 1)}</span><div className="flex gap-2"><Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</Button><Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next</Button></div></div>}
  </>;
}
