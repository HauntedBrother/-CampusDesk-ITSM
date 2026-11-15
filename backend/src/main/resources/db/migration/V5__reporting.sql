-- Pure functions keep lists, reports and exports consistent with SlaSnapshot outcomes.
CREATE FUNCTION itsm_sla_outcome(due_at timestamptz, completed_at timestamptz, breached_at timestamptz,
    target_seconds integer, warning_percent integer, observed_at timestamptz)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
 SELECT CASE WHEN breached_at IS NOT NULL THEN 'BREACHED'
 WHEN due_at IS NULL THEN 'UNCONFIGURED'
 WHEN coalesce(completed_at,observed_at)>due_at THEN 'BREACHED'
 WHEN completed_at IS NOT NULL THEN 'MET'
 WHEN observed_at>=due_at-(target_seconds*(100-warning_percent)/100.0)*interval '1 second' THEN 'APPROACHING'
 ELSE 'TRACKING' END
$$;
CREATE FUNCTION itsm_incident_sla(response_due timestamptz,resolution_due timestamptz,responded timestamptz,resolved timestamptz,
    response_breach timestamptz,resolution_breach timestamptz,response_seconds integer,resolution_seconds integer,warning integer,observed timestamptz)
RETURNS text LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
 SELECT CASE WHEN 'BREACHED' IN (r,s) THEN 'BREACHED' WHEN 'UNCONFIGURED' IN (r,s) THEN 'UNCONFIGURED'
 WHEN 'APPROACHING' IN (r,s) THEN 'APPROACHING' WHEN r='MET' AND s='MET' THEN 'MET' ELSE 'TRACKING' END
 FROM (SELECT itsm_sla_outcome(response_due,responded,response_breach,response_seconds,warning,observed) r,
 itsm_sla_outcome(resolution_due,resolved,resolution_breach,resolution_seconds,warning,observed) s) outcomes
$$;
CREATE INDEX incidents_created_id_idx ON incidents(created_at DESC,id DESC);
CREATE INDEX incidents_reporter_created_idx ON incidents(reporter_id,created_at DESC);
CREATE INDEX incidents_technician_status_created_idx ON incidents(technician_id,status,created_at DESC);
