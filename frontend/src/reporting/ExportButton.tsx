import { useState } from 'react';
import { Download } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/ui/button';
export function ExportButton({ filters }: { filters: string }) {
  const { request } = useAuth(); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function download() {
    setBusy(true); setError('');
    try { const csv = await request<string>(`/reports/incidents.csv?${filters}`); const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); const link = document.createElement('a'); link.href = url; link.download = 'incidents.csv'; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url); }
    catch (e) { setError(e instanceof Error ? e.message : 'Export failed'); } finally { setBusy(false); }
  }
  return <div><Button type="button" variant="outline" disabled={busy} onClick={() => void download()}><Download size={15}/>{busy ? 'Exporting...' : 'Export CSV'}</Button>{error && <p role="alert" className="mt-2 max-w-sm text-xs text-red-700">{error}</p>}</div>;
}
