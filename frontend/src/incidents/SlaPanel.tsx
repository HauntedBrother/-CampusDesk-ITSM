import { useEffect, useMemo, useState } from 'react';
import { Clock3 } from 'lucide-react';
import { dateTime, label, type Incident, type SlaOutcome } from './types';
export function duration(milliseconds: number) {
  let seconds = Math.max(0, Math.floor(Math.abs(milliseconds) / 1000)); const days = Math.floor(seconds / 86400); seconds %= 86400;
  const hours = Math.floor(seconds / 3600); seconds %= 3600; const minutes = Math.floor(seconds / 60); seconds %= 60;
  return `${days ? days + 'd ' : ''}${hours ? hours + 'h ' : ''}${minutes}m ${seconds}s`;
}
export function slaOutcome(due: string | null, completion: string | null, breach: string | null, seconds: number | null, warning: number | null, now: number): SlaOutcome {
  if (breach) return 'BREACHED'; if (!due || !seconds) return 'UNCONFIGURED'; const deadline = Date.parse(due);
  if ((completion ? Date.parse(completion) : now) > deadline) return 'BREACHED';
  if (completion) return 'MET';
  return now >= deadline - seconds * 1000 * (100 - (warning ?? 80)) / 100 ? 'APPROACHING' : 'TRACKING';
}
export function SlaBadge({ status }: { status: SlaOutcome }) {
  const color = status === 'BREACHED' ? 'bg-red-50 text-red-700' : status === 'APPROACHING' ? 'bg-amber-50 text-amber-800' : status === 'MET' ? 'bg-teal-50 text-teal-800' : 'bg-slate-100 text-slate-600';
  return <span className={`inline-flex rounded-full px-2 py-1 text-[10px] font-semibold ${color}`}>{label(status)}</span>;
}
export function SlaPanel({ incident }: { incident: Incident }) {
  const sla = incident.sla; const [tick, setTick] = useState(Date.now());
  const offset = useMemo(() => sla ? Date.parse(sla.serverTime) - Date.now() : 0, [sla?.serverTime]);
  useEffect(() => { const interval = setInterval(() => setTick(Date.now()), 1000); return () => clearInterval(interval); }, []);
  if (!sla) return null; const now = tick + offset;
  const rows = [
    { title: 'Response', due: sla.responseDueAt, completion: sla.firstResponseAt, breach: sla.responseBreachedAt, seconds: sla.responseSeconds },
    { title: 'Resolution', due: sla.resolutionDueAt, completion: incident.resolvedAt, breach: sla.resolutionBreachedAt, seconds: sla.resolutionSeconds },
  ];
  return <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-5"><h2 className="flex items-center gap-2 text-sm font-semibold"><Clock3 size={17}/>Service level agreement</h2><p className="text-xs text-slate-400">24/7 calendar time · clock starts at reporting</p>{sla.issue && <p role="status" className="rounded-lg bg-amber-50 p-3 text-xs leading-5 text-amber-800">{sla.issue}</p>}{rows.map(row => {
    const outcome = slaOutcome(row.due, row.completion, row.breach, row.seconds, sla.warningPercent, now);
    const remaining = row.due ? Date.parse(row.due) - now : null;
    return <div key={row.title} className="rounded-lg border border-slate-100 p-3"><div className="flex items-center justify-between gap-2"><h3 className="text-xs font-semibold">{row.title}</h3><SlaBadge status={outcome}/></div>{row.due ? <><p className={`mt-3 text-sm font-semibold tabular-nums ${outcome === 'BREACHED' ? 'text-red-700' : 'text-slate-800'}`}>{row.completion ? `Completed in ${duration(Date.parse(row.completion) - Date.parse(incident.createdAt))}` : `${remaining! < 0 ? 'Overdue by ' : 'Due in '}${duration(remaining!)}`}</p><p className="mt-2 text-[10px] leading-5 text-slate-400">Deadline: {dateTime(row.due)}</p>{row.breach && <p className="mt-1 text-[10px] text-red-600">Recorded breach: {dateTime(row.breach)}</p>}</> : <p className="mt-3 text-xs text-slate-400">No configured deadline</p>}</div>;
  })}{sla.firstAcknowledgedAt && <p className="text-[10px] leading-5 text-slate-400">First acknowledgement: {dateTime(sla.firstAcknowledgedAt)}</p>}<p className="text-[10px] leading-5 text-slate-400">Breach records are retained after changes. Countdown uses server time; monitoring records outcomes on the backend.</p></section>;
}
