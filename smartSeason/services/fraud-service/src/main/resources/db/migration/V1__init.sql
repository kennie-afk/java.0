CREATE TABLE fraud_rules (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    code                   VARCHAR(255) NOT NULL UNIQUE,
    typology               VARCHAR(64) NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    description            TEXT,
    expression             TEXT NOT NULL,
    threshold              NUMERIC(18,4),
    severity               VARCHAR(64) NOT NULL,
    weight                 INTEGER NOT NULL,
    enabled                BOOLEAN NOT NULL,
    auto_hold_payout       BOOLEAN NOT NULL
);
CREATE INDEX ix_fraud_rules_tenant ON fraud_rules (tenant_id);

CREATE TABLE fraud_signals (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    subject_type           VARCHAR(64) NOT NULL,
    subject_id             UUID NOT NULL,
    rule_code              VARCHAR(255) NOT NULL,
    typology               VARCHAR(255) NOT NULL,
    score                  NUMERIC(18,4) NOT NULL,
    detected_at            TIMESTAMPTZ NOT NULL,
    source_event           VARCHAR(255),
    details                JSONB,
    case_id                UUID
);
CREATE INDEX ix_fraud_signals_tenant ON fraud_signals (tenant_id);
CREATE INDEX ix_fraud_signals_subject_id ON fraud_signals (subject_id);
CREATE INDEX ix_fraud_signals_rule_code ON fraud_signals (rule_code);
CREATE INDEX ix_fraud_signals_case_id ON fraud_signals (case_id);

CREATE TABLE fraud_cases (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    case_number            VARCHAR(255) NOT NULL UNIQUE,
    subject_type           VARCHAR(64) NOT NULL,
    subject_id             UUID NOT NULL,
    farm_id                UUID,
    typology               VARCHAR(255) NOT NULL,
    severity               VARCHAR(64) NOT NULL,
    confidence             NUMERIC(18,4) NOT NULL,
    opened_at              TIMESTAMPTZ NOT NULL,
    status                 VARCHAR(64) NOT NULL,
    assigned_to            UUID,
    resolved_at            TIMESTAMPTZ,
    resolution             TEXT,
    payout_held            BOOLEAN NOT NULL,
    appealed_at            TIMESTAMPTZ,
    appeal_outcome         VARCHAR(255)
);
CREATE INDEX ix_fraud_cases_tenant ON fraud_cases (tenant_id);
CREATE INDEX ix_fraud_cases_subject_id ON fraud_cases (subject_id);
CREATE INDEX ix_fraud_cases_farm_id ON fraud_cases (farm_id);

CREATE TABLE fraud_evidence (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    case_id                UUID NOT NULL,
    label                  VARCHAR(255) NOT NULL,
    evidence_type          VARCHAR(255) NOT NULL,
    payload                JSONB,
    weight                 NUMERIC(18,4),
    collected_at           TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_fraud_evidence_tenant ON fraud_evidence (tenant_id);
CREATE INDEX ix_fraud_evidence_case_id ON fraud_evidence (case_id);

CREATE TABLE worker_risk_scores (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0,
    worker_id              UUID NOT NULL UNIQUE,
    farm_id                UUID,
    score                  INTEGER NOT NULL,
    band                   VARCHAR(64) NOT NULL,
    open_cases             INTEGER NOT NULL,
    last_signal_at         TIMESTAMPTZ,
    decay_applied_at       TIMESTAMPTZ,
    components             JSONB
);
CREATE INDEX ix_worker_risk_scores_tenant ON worker_risk_scores (tenant_id);
CREATE INDEX ix_worker_risk_scores_farm_id ON worker_risk_scores (farm_id);
