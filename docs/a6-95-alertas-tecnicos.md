# A6-95 (US-20) — Alertas Técnicos que Travam a Aprovação

## 1. Visão Geral

Um alerta técnico é um aviso preso a uma versão de campanha (`CampanhaVersao`), indicando que há algo suspeito naquela versão (código gerado ou consultas executadas).
Enquanto existir alerta técnico aberto (`status = 'ABERTO'`), a versão não pode ser aprovada por nenhum supervisor. Só o **Auditor** (`Perfil.AUDITOR`) resolve alertas, e sempre mediante justificativa obrigatória.

Alertas técnicos podem se originar de dois modos:
1. **Automático (`AUTOMATICO`)**: a Lana bloqueou uma consulta (guardrail/validação SQL) ou detectou código Python suspeito.
2. **Manual (`MANUAL`)**: o Auditor leu o código/trilha da versão e decidiu abrir um alerta técnico.

A trava de aprovação impede a aprovação (`APROVAR`) com `409 Conflict`, mas **reprovar** continua permitido normalmente.

---

## 2. Banco de Dados

Migration `V14__create_alerta_tecnico.sql`:

```sql
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
```

---

## 3. Rotas e Permissões

Todas as rotas exigem autenticação Bearer JWT.

| Método e Rota | Perfis Permitidos | Perfis Bloqueados (403) | Descrição |
|---|---|---|---|
| `GET /api/campanhas/{id}/versoes/{numero}/alertas` | SUPERVISOR, AUDITOR | GERENTE, ADMIN | Lista todos os alertas da versão |
| `POST /api/campanhas/{id}/versoes/{numero}/alertas` | AUDITOR | GERENTE, SUPERVISOR, ADMIN | Abre alerta técnico manual |
| `POST /api/campanhas/{id}/versoes/{numero}/alertas/{alertaId}/resolver` | AUDITOR | GERENTE, SUPERVISOR, ADMIN | Resolve alerta técnico com justificativa |
| `POST /api/campanhas/{id}/decidir` | SUPERVISOR | GERENTE, AUDITOR, ADMIN | Decisão de versão (trava se houver alerta aberto) |

---

## 4. Contrato dos Endpoints

### 4.1. `GET /api/campanhas/{id}/versoes/{numero}/alertas`
Retorna a lista de alertas da versão:

```json
[
  {
    "id": 1,
    "campanha_id": 12,
    "numero_versao": 1,
    "origem": "AUTOMATICO",
    "tipo": "CONSULTA_BLOQUEADA",
    "descricao": "Tentativa de acesso à tabela protegida: usuario",
    "status": "ABERTO",
    "aberto_por": null,
    "aberto_em": "2026-11-23T14:00:00Z",
    "resolvido_por": null,
    "resolvido_em": null,
    "justificativa": null
  },
  {
    "id": 2,
    "campanha_id": 12,
    "numero_versao": 1,
    "origem": "MANUAL",
    "tipo": "CODIGO_SUSPEITO",
    "descricao": "Código suspeito no cálculo",
    "status": "RESOLVIDO",
    "aberto_por": { "id": 4, "nome": "Paulo" },
    "aberto_em": "2026-11-23T14:05:00Z",
    "resolvido_por": { "id": 4, "nome": "Paulo" },
    "resolvido_em": "2026-11-23T14:15:00Z",
    "justificativa": "Código analisado e verificado como seguro."
  }
]
```

### 4.2. `POST /api/campanhas/{id}/versoes/{numero}/alertas`
Abre um alerta técnico manual. Apenas `AUDITOR`.

**Requisição:**
```json
{
  "tipo": "CODIGO_SUSPEITO",
  "descricao": "Uso de função potencialmente insegura no cálculo."
}
```
* `tipo`: opcional, default `"MANUAL"`.
* `descricao`: obrigatória (se vazia, retorna **400**).

**Resposta (201 Created):**
Retorna o objeto do alerta com `status: "ABERTO"`, `origem: "MANUAL"`, `aberto_por: { id, nome }` e campos de resolução nulos.

### 4.3. `POST /api/campanhas/{id}/versoes/{numero}/alertas/{alertaId}/resolver`
Resolve um alerta técnico. Apenas `AUDITOR`.

**Requisição:**
```json
{
  "justificativa": "Justificativa obrigatória da resolução do alerta."
}
```
* `justificativa`: obrigatória. Se nula, vazia ou ausente, responde **400** com a mensagem:
  `"Informe a justificativa para resolver o alerta."`

**Resposta (200 OK):**
Retorna o objeto do alerta atualizado com `status: "RESOLVIDO"`, `resolvido_por: { id, nome }`, `resolvido_em` e `justificativa`.

### 4.4. Trava na Aprovação (`POST /api/campanhas/{id}/decidir`)
Quando `decisao: "APROVAR"`:
* Se houver qualquer alerta técnico com `status: "ABERTO"` na versão:
  Responde **409 Conflict** com a mensagem:
  `"Esta versão tem alerta técnico aberto. O Auditor precisa resolvê-lo antes da aprovação."`
* Se todos os alertas estiverem `RESOLVIDO` (ou não houver alertas):
  Aprova normalmente.
* Se `decisao: "REPROVAR"`:
  Reprovar é permitido mesmo que haja alertas abertos.

### 4.5. Resposta da Versão (`GET /api/campanhas/{id}/versoes/{numero}`)
O JSON de `VersaoCampanhaResponse` inclui o campo `"alertas"` contendo a lista dos alertas da versão, permitindo que o front-end exiba os avisos e bloqueie visualmente os botões apropriados. Além disso, quando há alerta aberto, `"APROVAR"` não é retornado na lista `"acoes"`.

---

## 5. Formato de Alertas da Lana (Integração Backend ↔ Lana)

A Lana devolve alertas técnicos automáticos via:
1. **gRPC (`InvokeResponse.alertas_json`)**: campo 5 de `InvokeResponse` (definido em `ia_service.proto`).
2. **HTTP `/simulacao`**: campo `"alertas"` ou `"alertas_json"` no corpo da resposta da simulação.

Estrutura JSON padrão enviada pela Lana:
```json
[
  {
    "tipo": "CONSULTA_BLOQUEADA",
    "descricao": "Tentativa de acesso à tabela protegida: usuario"
  },
  {
    "tipo": "CODIGO_SUSPEITO",
    "descricao": "Uso de exec/eval detectado no código gerado"
  }
]
```
O backend também aceita listas de strings ou envelopes com chave `"alertas"` / `"alertas_json"`.
Quando recebidos, são gravados como `status: "ABERTO"`, `origem: "AUTOMATICO"` e `aberto_por: null` na respectiva versão da campanha.
