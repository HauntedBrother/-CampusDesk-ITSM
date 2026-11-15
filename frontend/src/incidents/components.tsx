import type { ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { cn } from '@/lib/utils';
import { label, type IncidentStatus } from './types';
export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select className={cn('h-11 w-full rounded-lg border border-border bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-teal-500', className)} {...props}/>;
}
export function Textarea({ className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn('min-h-28 w-full rounded-lg border border-border bg-white p-3 text-sm outline-none focus:ring-2 focus:ring-teal-500', className)} {...props}/>;
}
export function Field({ title, htmlFor, children }: { title: string; htmlFor: string; children: ReactNode }) {
  return <div><label className="field-label" htmlFor={htmlFor}>{title}</label>{children}</div>;
}
export function StatusBadge({ status }: { status: IncidentStatus }) {
  const color = status === 'CLOSED' || status === 'VERIFIED' ? 'bg-teal-50 text-teal-800' : status === 'RESOLVED' ? 'bg-blue-50 text-blue-700' : status === 'IN_PROGRESS' ? 'bg-amber-50 text-amber-800' : 'bg-slate-100 text-slate-600';
  return <span className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-medium ${color}`}>{label(status)}</span>;
}
export function ErrorMessage({ error }: { error: string }) { return error ? <p role="alert" className="my-4 rounded-lg border border-red-100 bg-red-50 p-3 text-sm text-red-700">{error}</p> : null; }
