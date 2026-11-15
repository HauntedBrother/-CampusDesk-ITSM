import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
import { ProtectedRoute } from '@/auth/ProtectedRoute';
import { AuthPage } from '@/pages/AuthPage';
import { AppLayout, Dashboard } from '@/pages/Dashboard';
import { IncidentList } from '@/incidents/IncidentList';
import { IncidentForm } from '@/incidents/IncidentForm';
import { IncidentDetail } from '@/incidents/IncidentDetail';
import { CatalogPage } from '@/incidents/CatalogPage';
import { AutomationPage } from '@/incidents/AutomationPage';
import { NotificationCenter } from '@/notifications/NotificationCenter';
import { RulesPage } from '@/intelligence/RulesPage';
function Home() { const { user, loading } = useAuth(); return loading ? <p role="status">Loading…</p> : <Navigate to={user ? `/${user.role.toLowerCase()}` : '/login'} replace/>; }
export default function App() {
  return <BrowserRouter><AuthProvider><Routes>
    <Route path="/login" element={<AuthPage/>}/><Route path="/register" element={<AuthPage register/>}/>
    <Route element={<ProtectedRoute/>}><Route element={<AppLayout/>}>
      <Route path="/incidents" element={<IncidentList/>}/>
      <Route path="/notifications" element={<NotificationCenter/>}/>
      <Route element={<ProtectedRoute role="ADMIN"/>}><Route path="/category-rules" element={<RulesPage/>}/></Route>
      <Route element={<ProtectedRoute role="EMPLOYEE"/>}><Route path="/incidents/new" element={<IncidentForm/>}/></Route>
      <Route path="/incidents/:id" element={<IncidentDetail/>}/>
      <Route path="/incidents/:id/edit" element={<IncidentForm edit/>}/>
      <Route element={<ProtectedRoute role="ADMIN"/>}><Route path="/catalog" element={<CatalogPage/>}/></Route>
      <Route element={<ProtectedRoute role="ADMIN"/>}><Route path="/automation" element={<AutomationPage/>}/></Route>
      {(['EMPLOYEE', 'TECHNICIAN', 'ADMIN'] as const).map(role => <Route key={role} element={<ProtectedRoute role={role}/>}><Route path={`/${role.toLowerCase()}`} element={<Dashboard role={role}/>}/></Route>)}
    </Route></Route><Route path="*" element={<Home/>}/>
  </Routes></AuthProvider></BrowserRouter>;
}
