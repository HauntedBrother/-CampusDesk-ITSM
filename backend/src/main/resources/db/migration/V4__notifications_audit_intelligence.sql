ALTER TABLE incident_history ADD COLUMN old_values JSONB NOT NULL DEFAULT '{}';
ALTER TABLE incident_history ADD COLUMN new_values JSONB NOT NULL DEFAULT '{}';
UPDATE incident_history SET old_values=jsonb_build_object('status',previous_status,'legacy',true),
    new_values=jsonb_build_object('status',new_status,'legacy',true);
CREATE FUNCTION protect_incident_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Incident audit history is append-only'; END;
$$;
CREATE TRIGGER incident_history_immutable BEFORE UPDATE OR DELETE ON incident_history
FOR EACH ROW EXECUTE FUNCTION protect_incident_history();
CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    history_id BIGINT NOT NULL UNIQUE REFERENCES incident_history(id),
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    action VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500)
);
CREATE INDEX notification_outbox_pending_idx ON notification_outbox(available_at) WHERE processed_at IS NULL;
CREATE TABLE notification_outbox_recipients (
    event_id UUID NOT NULL REFERENCES notification_outbox(id),
    user_id UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY(event_id,user_id)
);
CREATE TABLE in_app_notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES notification_outbox(id),
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    recipient_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(event_id,recipient_id)
);
CREATE INDEX in_app_notification_recipient_idx ON in_app_notifications(recipient_id,created_at DESC);
CREATE TABLE simulated_email_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES notification_outbox(id),
    incident_id BIGINT NOT NULL REFERENCES incidents(id),
    recipient_id UUID NOT NULL REFERENCES users(id),
    email VARCHAR(254) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status='SIMULATED'),
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(event_id,recipient_id)
);
CREATE TABLE category_rules (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES incident_categories(id),
    keyword VARCHAR(80) NOT NULL,
    weight INTEGER NOT NULL CHECK (weight BETWEEN 1 AND 100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(category_id,keyword)
);
CREATE TABLE development_seed_versions (name VARCHAR(80) PRIMARY KEY);
