-- A6-95 (US-20): alertas técnicos que travam a aprovação da versão da campanha.

CREATE TABLE alerta_tecnico (
    id BIGSERIAL PRIMARY KEY,
    campanha_id BIGINT NOT NULL,
    numero_versao INT NOT NULL,
    origem VARCHAR(20) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    descricao TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    aberto_por INT,
    aberto_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolvido_por INT,
    resolvido_em TIMESTAMPTZ,
    justificativa TEXT,
    CONSTRAINT fk_alerta_campanha FOREIGN KEY (campanha_id) REFERENCES campanha (id) ON DELETE CASCADE,
    CONSTRAINT fk_alerta_versao FOREIGN KEY (campanha_id, numero_versao) REFERENCES campanha_versao (campanha_id, numero) ON DELETE CASCADE,
    CONSTRAINT fk_alerta_aberto_por FOREIGN KEY (aberto_por) REFERENCES usuario (id),
    CONSTRAINT fk_alerta_resolvido_por FOREIGN KEY (resolvido_por) REFERENCES usuario (id),
    CONSTRAINT ck_alerta_origem CHECK (origem IN ('AUTOMATICO', 'MANUAL')),
    CONSTRAINT ck_alerta_status CHECK (status IN ('ABERTO', 'RESOLVIDO'))
);

CREATE INDEX idx_alerta_campanha_versao ON alerta_tecnico (campanha_id, numero_versao);
CREATE INDEX idx_alerta_status ON alerta_tecnico (status);
