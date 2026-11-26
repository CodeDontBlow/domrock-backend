-- Seed idempotente para ambiente de homologação (homolog).
-- Popula 6 campanhas em todos os status (RASCUNHO, AGUARDANDO_APROVACAO, APROVADA, REPROVADA, ARQUIVADA),
-- 8 simulações com blocos de orçamento/meta/por_marca/por_cargo para gráficos e relatórios,
-- versões congeladas (campanha_versao), logs de status e histórico de chat (mensagem_chat).
-- Seguro para reexecução: verifica existência por chat_id antes de inserir cada campanha.

DO $seed$
DECLARE
    v_gabriela_id INT;
    v_sergio_id   INT;
    v_silvia_id   INT;
    v_camp_id     BIGINT;
    v_sim_id      BIGINT;
    v_sim_v2_id   BIGINT;
    v_versao_id   BIGINT;
    v_msg_id      BIGINT;
    v_ficha       JSONB;
    v_resultado   JSONB;
BEGIN
    SELECT id INTO v_gabriela_id FROM usuario WHERE email = 'gabriela@camplana.com.br' ORDER BY id ASC LIMIT 1;
    SELECT id INTO v_sergio_id   FROM usuario WHERE email = 'sergio@camplana.com.br'   ORDER BY id ASC LIMIT 1;
    SELECT id INTO v_silvia_id   FROM usuario WHERE email = 'silvia@camplana.com.br'   ORDER BY id ASC LIMIT 1;

    IF v_gabriela_id IS NULL OR v_sergio_id IS NULL OR v_silvia_id IS NULL THEN
        RAISE NOTICE 'Usuários padrão da V4 não encontrados; ignorando seed V11.';
        RETURN;
    END IF;

    --------------------------------------------------------------------------
    -- 1. Campanha RASCUNHO (autoria Gabriela) - Orçamento dentro do limite
    --------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-rascunho') THEN
        v_ficha := '{
          "status": "DRAFT_PENDING_REVIEW",
          "rule_id": "homolog-rascunho-natal",
          "faltantes": [],
          "tipo_regra": "BONUS_TEMPORARIO",
          "raw_prompt": "Incentivo Natal +1,0% para vendedores e assistentes de 15 a 24/12/2025",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-12-15", "data_fim": "2025-12-24"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 1.0, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["ALL"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "200", "300"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 1500000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 55000.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-rascunho-natal",
          "competencias": ["2025-12"],
          "origem_codigo": "llm",
          "tentativas": 1,
          "totais": {"baseline": 180000.0, "simulado": 225000.0, "diferenca": 45000.0, "diferenca_pct": 25.0},
          "meta": {"atingida": true, "meta_vendas": 1500000.0, "vendas_periodo": 1850000.0, "diferenca": 350000.0, "pct_atingimento": 123.33},
          "orcamento": {"limite": 55000.0, "custo_estimado": 45000.0, "saldo": 10000.0, "dentro_orcamento": true, "categoria": "Bônus Sazonal"},
          "impacto": {"pessoas_total": 467, "pessoas_impactadas": 380, "media_por_pessoa": 118.42, "maior_acrescimo": 325.35},
          "por_cargo": [
            {"codigo": "100", "baseline": 102304.29, "simulado": 138322.13, "diferenca": 36017.84},
            {"codigo": "200", "baseline": 23265.45, "simulado": 29750.70, "diferenca": 6485.25},
            {"codigo": "300", "baseline": 4240.30, "simulado": 6737.21, "diferenca": 2496.91}
          ],
          "por_marca": [
            {"codigo": "10", "baseline": 138558.56, "simulado": 174258.26, "diferenca": 35699.70},
            {"codigo": "20", "baseline": 16089.16, "simulado": 19624.75, "diferenca": 3535.59},
            {"codigo": "30", "baseline": 6065.48, "simulado": 8562.39, "diferenca": 2496.91}
          ],
          "explicacao": "No período de 15 a 24/12/2025, as vendas estimadas somam R$ 1.850.000,00 (123,3% da meta), com custo adicional de R$ 45.000,00 dentro do orçamento de R$ 55.000,00."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Campanha Natal - Rascunho', v_gabriela_id, 'homolog-chat-rascunho', 'RASCUNHO', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa)
            VALUES (v_camp_id, NULL, NULL, 'RASCUNHO', v_gabriela_id, NOW() - INTERVAL '6 days', NULL);

            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_gabriela_id, 'homolog-chat-rascunho', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 2400, NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days')
            RETURNING id INTO v_sim_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em) VALUES
            ('homolog-chat-rascunho', v_gabriela_id, 'usuario', NULL, NULL, 'Quero criar um incentivo de Natal de 15 a 24/12/2025 com +1,0% para vendedores e assistentes e orçamento máximo de R$ 55.000,00.', NULL, NULL, NOW() - INTERVAL '6 days'),
            ('homolog-chat-rascunho', v_gabriela_id, 'lana', NULL, NULL, 'Ficha registrada com meta de R$ 1.500.000,00 e orçamento máximo de R$ 55.000,00 para os cargos 100, 200 e 300.', v_ficha, NULL, NOW() - INTERVAL '6 days' + INTERVAL '5 seconds'),
            ('homolog-chat-rascunho', v_gabriela_id, 'sistema', 'SIMULACAO', v_sim_id, 'Simulação concluída: custo adicional de R$ 45.000,00 (dentro do orçamento).', NULL, NULL, NOW() - INTERVAL '6 days' + INTERVAL '10 seconds');
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- 2. Campanha AGUARDANDO_APROVACAO (autoria Gabriela) - Orçamento pendente
    --------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-aguardando-1') THEN
        v_ficha := '{
          "status": "READY_FOR_APPROVAL",
          "rule_id": "homolog-virada-novembro",
          "faltantes": [],
          "tipo_regra": "BONUS_TEMPORARIO",
          "raw_prompt": "Campanha Virada de Novembro +1,0% de 20 a 28/11/2025",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-11-20", "data_fim": "2025-11-28"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 1.0, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["ALL"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "200", "300"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 2200000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 30000.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-virada-novembro",
          "competencias": ["2025-11"],
          "origem_codigo": "llm",
          "tentativas": 1,
          "totais": {"baseline": 165000.0, "simulado": 189061.26, "diferenca": 24061.26, "diferenca_pct": 14.58},
          "meta": {"atingida": true, "meta_vendas": 2200000.0, "vendas_periodo": 2406125.93, "diferenca": 206125.93, "pct_atingimento": 109.37},
          "orcamento": {"limite": 30000.0, "custo_estimado": 24061.26, "saldo": 5938.74, "dentro_orcamento": true, "categoria": "Aceleração Mensal"},
          "impacto": {"pessoas_total": 467, "pessoas_impactadas": 342, "media_por_pessoa": 70.35, "maior_acrescimo": 280.10},
          "por_cargo": [
            {"codigo": "100", "baseline": 98000.0, "simulado": 115200.0, "diferenca": 17200.0},
            {"codigo": "200", "baseline": 45000.0, "simulado": 49800.0, "diferenca": 4800.0},
            {"codigo": "300", "baseline": 22000.0, "simulado": 24061.26, "diferenca": 2061.26}
          ],
          "por_marca": [
            {"codigo": "10", "baseline": 112000.0, "simulado": 129500.0, "diferenca": 17500.0},
            {"codigo": "20", "baseline": 31000.0, "simulado": 34800.0, "diferenca": 3800.0},
            {"codigo": "40", "baseline": 22000.0, "simulado": 24761.26, "diferenca": 2761.26}
          ],
          "explicacao": "Vendas acumuladas de R$ 2.406.125,93 superam a meta de R$ 2.200.000,00 com custo adicional de R$ 24.061,26 dentro do orçamento de R$ 30.000,00."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Virada de Novembro - Todas as Marcas', v_gabriela_id, 'homolog-chat-aguardando-1', 'AGUARDANDO_APROVACAO', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_gabriela_id, 'homolog-chat-aguardando-1', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 2100, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days')
            RETURNING id INTO v_sim_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em) VALUES
            ('homolog-chat-aguardando-1', v_gabriela_id, 'usuario', NULL, NULL, 'Criar campanha Virada de Novembro de 20 a 28/11/2025 com +1,0% e limite de orçamento R$ 30.000,00.', NULL, NULL, NOW() - INTERVAL '5 days'),
            ('homolog-chat-aguardando-1', v_gabriela_id, 'lana', NULL, NULL, 'Parâmetros registrados para 20 a 28/11/2025 com meta de R$ 2.200.000,00 e orçamento de R$ 30.000,00.', v_ficha, NULL, NOW() - INTERVAL '5 days' + INTERVAL '4 seconds');

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-aguardando-1', v_gabriela_id, 'sistema', 'SIMULACAO', v_sim_id, 'Simulação concluída: custo adicional de R$ 24.061,26.', NULL, NULL, NOW() - INTERVAL '5 days' + INTERVAL '8 seconds')
            RETURNING id INTO v_msg_id;

            INSERT INTO campanha_versao (campanha_id, numero, simulacao_id, regra, resultado, data_inicio, data_fim, ultima_mensagem_id, submetida_em)
            VALUES (v_camp_id, 1, v_sim_id, v_ficha, v_resultado, '2025-11-20', '2025-11-28', v_msg_id, NOW() - INTERVAL '5 days' + INTERVAL '15 seconds')
            RETURNING id INTO v_versao_id;

            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa) VALUES
            (v_camp_id, NULL, NULL, 'RASCUNHO', v_gabriela_id, NOW() - INTERVAL '5 days', NULL),
            (v_camp_id, v_versao_id, 'RASCUNHO', 'AGUARDANDO_APROVACAO', v_gabriela_id, NOW() - INTERVAL '5 days' + INTERVAL '15 seconds', NULL);
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- 3. Campanha AGUARDANDO_APROVACAO (autoria Sérgio) - Orçamento estourado
    --------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-aguardando-2') THEN
        v_ficha := '{
          "status": "READY_FOR_APPROVAL",
          "rule_id": "homolog-expansao-azul",
          "faltantes": [],
          "tipo_regra": "EXPANSAO_MARCA",
          "raw_prompt": "Incentivo Marca Azul +1,5% em outubro de 2025",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-10-01", "data_fim": "2025-10-31"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 1.5, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["30"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "200"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 650000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 10000.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-expansao-azul",
          "competencias": ["2025-10"],
          "origem_codigo": "llm",
          "tentativas": 1,
          "totais": {"baseline": 14200.0, "simulado": 26150.0, "diferenca": 11950.0, "diferenca_pct": 84.15},
          "meta": {"atingida": true, "meta_vendas": 650000.0, "vendas_periodo": 796666.0, "diferenca": 146666.0, "pct_atingimento": 122.56},
          "orcamento": {"limite": 10000.0, "custo_estimado": 11950.0, "saldo": -1950.0, "dentro_orcamento": false, "categoria": "Expansão de Marca"},
          "impacto": {"pessoas_total": 85, "pessoas_impactadas": 74, "media_por_pessoa": 161.49, "maior_acrescimo": 412.00},
          "por_cargo": [
            {"codigo": "100", "baseline": 10100.0, "simulado": 18850.0, "diferenca": 8750.0},
            {"codigo": "200", "baseline": 4100.0, "simulado": 7300.0, "diferenca": 3200.0}
          ],
          "por_marca": [
            {"codigo": "30", "baseline": 14200.0, "simulado": 26150.0, "diferenca": 11950.0}
          ],
          "explicacao": "A marca AZUL (30) atingiu R$ 796.666,00 em outubro/2025, porém o custo adicional de R$ 11.950,00 excedeu em R$ 1.950,00 o orçamento de R$ 10.000,00."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Expansão Marca Azul - Outubro', v_sergio_id, 'homolog-chat-aguardando-2', 'AGUARDANDO_APROVACAO', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_sergio_id, 'homolog-chat-aguardando-2', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 1950, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days')
            RETURNING id INTO v_sim_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-aguardando-2', v_sergio_id, 'usuario', NULL, NULL, 'Simular incentivo de +1,5% na marca Azul em outubro de 2025 com orçamento de R$ 10.000,00.', NULL, NULL, NOW() - INTERVAL '4 days');

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-aguardando-2', v_sergio_id, 'lana', NULL, NULL, 'Simulação concluída: o custo adicional de R$ 11.950,00 ultrapassou o teto de R$ 10.000,00.', v_ficha, NULL, NOW() - INTERVAL '4 days' + INTERVAL '4 seconds')
            RETURNING id INTO v_msg_id;

            INSERT INTO campanha_versao (campanha_id, numero, simulacao_id, regra, resultado, data_inicio, data_fim, ultima_mensagem_id, submetida_em)
            VALUES (v_camp_id, 1, v_sim_id, v_ficha, v_resultado, '2025-10-01', '2025-10-31', v_msg_id, NOW() - INTERVAL '4 days' + INTERVAL '10 seconds')
            RETURNING id INTO v_versao_id;

            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa) VALUES
            (v_camp_id, NULL, NULL, 'RASCUNHO', v_sergio_id, NOW() - INTERVAL '4 days', NULL),
            (v_camp_id, v_versao_id, 'RASCUNHO', 'AGUARDANDO_APROVACAO', v_sergio_id, NOW() - INTERVAL '4 days' + INTERVAL '10 seconds', NULL);
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- 4. Campanha APROVADA (autoria Gabriela, aprovada por Sérgio) - 2 versões + 2 orçamentos
    --------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-aprovada-1') THEN
        v_ficha := '{
          "status": "APPROVED",
          "rule_id": "homolog-black-friday",
          "faltantes": [],
          "tipo_regra": "SAZONAL",
          "raw_prompt": "Black Friday 2025: +0,8% para todas as marcas de 24 a 30/11/2025",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-11-24", "data_fim": "2025-11-30"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 0.8, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["ALL"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "200", "300"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 2500000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 28000.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-black-friday",
          "competencias": ["2025-11"],
          "origem_codigo": "llm",
          "tentativas": 1,
          "totais": {"baseline": 190000.0, "simulado": 213736.10, "diferenca": 23736.10, "diferenca_pct": 12.49},
          "meta": {"atingida": true, "meta_vendas": 2500000.0, "vendas_periodo": 2967012.50, "diferenca": 467012.50, "pct_atingimento": 118.68},
          "orcamento": {"limite": 28000.0, "custo_estimado": 23736.10, "saldo": 4263.90, "dentro_orcamento": true, "categoria": "Black Friday"},
          "impacto": {"pessoas_total": 467, "pessoas_impactadas": 410, "media_por_pessoa": 57.89, "maior_acrescimo": 245.80},
          "por_cargo": [
            {"codigo": "100", "baseline": 125000.0, "simulado": 141200.0, "diferenca": 16200.0},
            {"codigo": "200", "baseline": 48000.0, "simulado": 53400.0, "diferenca": 5400.0},
            {"codigo": "300", "baseline": 17000.0, "simulado": 19136.10, "diferenca": 2136.10}
          ],
          "por_marca": [
            {"codigo": "10", "baseline": 135000.0, "simulado": 152100.0, "diferenca": 17100.0},
            {"codigo": "20", "baseline": 30000.0, "simulado": 33500.0, "diferenca": 3500.0},
            {"codigo": "40", "baseline": 25000.0, "simulado": 28136.10, "diferenca": 3136.10}
          ],
          "explicacao": "Campanha Black Friday aprovada com vendas de R$ 2.967.012,50 e custo adicional de R$ 23.736,10 (abaixo do teto de R$ 28.000,00)."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Black Friday Oficial 2025', v_gabriela_id, 'homolog-chat-aprovada-1', 'APROVADA', NOW() - INTERVAL '10 days', NOW() - INTERVAL '8 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_gabriela_id, 'homolog-chat-aprovada-1', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 2200, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days')
            RETURNING id INTO v_sim_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em) VALUES
            ('homolog-chat-aprovada-1', v_gabriela_id, 'usuario', NULL, NULL, 'Configurar Black Friday 2025 de 24 a 30/11 com +0,8% e orçamento de R$ 28.000,00.', NULL, NULL, NOW() - INTERVAL '10 days'),
            ('homolog-chat-aprovada-1', v_gabriela_id, 'lana', NULL, NULL, 'Campanha configurada e simulada com custo adicional de R$ 23.736,10.', v_ficha, NULL, NOW() - INTERVAL '10 days' + INTERVAL '4 seconds');

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-aprovada-1', v_sergio_id, 'sistema', 'STATUS', v_sim_id, 'Aprovado por Sérgio — v1: dentro do orçamento previsto para Black Friday.', NULL, NULL, NOW() - INTERVAL '8 days')
            RETURNING id INTO v_msg_id;

            INSERT INTO campanha_versao (campanha_id, numero, simulacao_id, regra, resultado, data_inicio, data_fim, ultima_mensagem_id, submetida_em)
            VALUES (v_camp_id, 1, v_sim_id, v_ficha, v_resultado, '2025-11-24', '2025-11-30', v_msg_id, NOW() - INTERVAL '9 days')
            RETURNING id INTO v_versao_id;

            -- Re-simulação de auditoria vinculada à versão (V9)
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, versao_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_sergio_id, 'homolog-chat-aprovada-1', NULL, v_versao_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 1800, NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days');

            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa) VALUES
            (v_camp_id, NULL, NULL, 'RASCUNHO', v_gabriela_id, NOW() - INTERVAL '10 days', NULL),
            (v_camp_id, v_versao_id, 'RASCUNHO', 'AGUARDANDO_APROVACAO', v_gabriela_id, NOW() - INTERVAL '9 days', NULL),
            (v_camp_id, v_versao_id, 'AGUARDANDO_APROVACAO', 'APROVADA', v_sergio_id, NOW() - INTERVAL '8 days', 'Orçamento aprovado conforme teto de Black Friday.');
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- 5. Campanha APROVADA (autoria Sílvia, aprovada por Sérgio) - Marcas Premium
    --------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-aprovada-2') THEN
        v_ficha := '{
          "status": "APPROVED",
          "rule_id": "homolog-premium-set",
          "faltantes": [],
          "tipo_regra": "MIX_PRODUTOS",
          "raw_prompt": "Incentivo Marcas Vermelho e Amarelo +1,2% em setembro de 2025",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-09-01", "data_fim": "2025-09-30"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 1.2, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["40", "50"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "200", "300"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 800000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 15000.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-premium-set",
          "competencias": ["2025-09"],
          "origem_codigo": "llm",
          "tentativas": 1,
          "totais": {"baseline": 26500.0, "simulado": 37420.0, "diferenca": 10920.0, "diferenca_pct": 41.21},
          "meta": {"atingida": true, "meta_vendas": 800000.0, "vendas_periodo": 910000.0, "diferenca": 110000.0, "pct_atingimento": 113.75},
          "orcamento": {"limite": 15000.0, "custo_estimado": 10920.0, "saldo": 4080.0, "dentro_orcamento": true, "categoria": "Mix Premium"},
          "impacto": {"pessoas_total": 140, "pessoas_impactadas": 118, "media_por_pessoa": 92.54, "maior_acrescimo": 298.40},
          "por_cargo": [
            {"codigo": "100", "baseline": 18200.0, "simulado": 25800.0, "diferenca": 7600.0},
            {"codigo": "200", "baseline": 6100.0, "simulado": 8520.0, "diferenca": 2420.0},
            {"codigo": "300", "baseline": 2200.0, "simulado": 3100.0, "diferenca": 900.0}
          ],
          "por_marca": [
            {"codigo": "40", "baseline": 22100.0, "simulado": 31200.0, "diferenca": 9100.0},
            {"codigo": "50", "baseline": 4400.0, "simulado": 6220.0, "diferenca": 1820.0}
          ],
          "explicacao": "Campanha de Mix Premium (Vermelho e Amarelo) atingiu 113,8% da meta com custo de R$ 10.920,00 dentro do orçamento de R$ 15.000,00."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Mix Premium Setembro - Vermelho e Amarelo', v_silvia_id, 'homolog-chat-aprovada-2', 'APROVADA', NOW() - INTERVAL '15 days', NOW() - INTERVAL '13 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_silvia_id, 'homolog-chat-aprovada-2', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 2050, NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days')
            RETURNING id INTO v_sim_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em) VALUES
            ('homolog-chat-aprovada-2', v_silvia_id, 'usuario', NULL, NULL, 'Incentivo de +1,2% para marcas Vermelho e Amarelo em setembro/2025.', NULL, NULL, NOW() - INTERVAL '15 days'),
            ('homolog-chat-aprovada-2', v_silvia_id, 'lana', NULL, NULL, 'Simulação concluída: R$ 10.920,00 de custo adicional dentro do limite de R$ 15.000,00.', v_ficha, NULL, NOW() - INTERVAL '15 days' + INTERVAL '4 seconds');

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-aprovada-2', v_sergio_id, 'sistema', 'STATUS', v_sim_id, 'Aprovado por Sérgio — v1: alinhado ao plano de margem.', NULL, NULL, NOW() - INTERVAL '13 days')
            RETURNING id INTO v_msg_id;

            INSERT INTO campanha_versao (campanha_id, numero, simulacao_id, regra, resultado, data_inicio, data_fim, ultima_mensagem_id, submetida_em)
            VALUES (v_camp_id, 1, v_sim_id, v_ficha, v_resultado, '2025-09-01', '2025-09-30', v_msg_id, NOW() - INTERVAL '14 days')
            RETURNING id INTO v_versao_id;

            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa) VALUES
            (v_camp_id, NULL, NULL, 'RASCUNHO', v_silvia_id, NOW() - INTERVAL '15 days', NULL),
            (v_camp_id, v_versao_id, 'RASCUNHO', 'AGUARDANDO_APROVACAO', v_silvia_id, NOW() - INTERVAL '14 days', NULL),
            (v_camp_id, v_versao_id, 'AGUARDANDO_APROVACAO', 'APROVADA', v_sergio_id, NOW() - INTERVAL '13 days', 'Margem e orçamento validados.');
        END IF;
    END IF;

    --------------------------------------------------------------------------
    -- 6. Campanha REPROVADA (autoria Gabriela, reprovada por Sílvia) + 7. ARQUIVADA
    --------------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-reprovada') THEN
        v_ficha := '{
          "status": "REJECTED",
          "rule_id": "homolog-reprovada-agosto",
          "faltantes": [],
          "tipo_regra": "BONUS_TEMPORARIO",
          "raw_prompt": "Campanha Agosto +2,5% todas as marcas incluindo gerentes",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-08-01", "data_fim": "2025-08-31"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 2.5, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["ALL"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "150", "200", "300"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 3000000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 60000.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-reprovada-agosto",
          "competencias": ["2025-08"],
          "origem_codigo": "llm",
          "tentativas": 1,
          "totais": {"baseline": 310000.0, "simulado": 405000.0, "diferenca": 95000.0, "diferenca_pct": 30.65},
          "meta": {"atingida": false, "meta_vendas": 3000000.0, "vendas_periodo": 2740000.0, "diferenca": -260000.0, "pct_atingimento": 91.33},
          "orcamento": {"limite": 60000.0, "custo_estimado": 95000.0, "saldo": -35000.0, "dentro_orcamento": false, "categoria": "Agressiva"},
          "impacto": {"pessoas_total": 467, "pessoas_impactadas": 467, "media_por_pessoa": 203.43, "maior_acrescimo": 890.00},
          "por_cargo": [
            {"codigo": "100", "baseline": 170000.0, "simulado": 220000.0, "diferenca": 50000.0},
            {"codigo": "150", "baseline": 85000.0, "simulado": 115000.0, "diferenca": 30000.0},
            {"codigo": "200", "baseline": 40000.0, "simulado": 51000.0, "diferenca": 11000.0},
            {"codigo": "300", "baseline": 15000.0, "simulado": 19000.0, "diferenca": 4000.0}
          ],
          "por_marca": [
            {"codigo": "10", "baseline": 220000.0, "simulado": 290000.0, "diferenca": 70000.0},
            {"codigo": "20", "baseline": 50000.0, "simulado": 65000.0, "diferenca": 15000.0},
            {"codigo": "30", "baseline": 40000.0, "simulado": 50000.0, "diferenca": 10000.0}
          ],
          "explicacao": "O acréscimo de 2,5% incluindo gerentes gerou custo adicional de R$ 95.000,00, excedendo em R$ 35.000,00 o orçamento máximo de R$ 60.000,00."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Aceleração Agosto - Revisar Teto', v_gabriela_id, 'homolog-chat-reprovada', 'REPROVADA', NOW() - INTERVAL '20 days', NOW() - INTERVAL '18 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_gabriela_id, 'homolog-chat-reprovada', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 2300, NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days')
            RETURNING id INTO v_sim_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-reprovada', v_gabriela_id, 'usuario', NULL, NULL, 'Simular +2,5% em agosto incluindo gerentes.', NULL, NULL, NOW() - INTERVAL '20 days');

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-reprovada', v_silvia_id, 'sistema', 'STATUS', v_sim_id, 'Reprovado por Sílvia: custo adicional de R$ 95.000,00 ultrapassou o orçamento de R$ 60.000,00.', NULL, NULL, NOW() - INTERVAL '18 days')
            RETURNING id INTO v_msg_id;

            INSERT INTO campanha_versao (campanha_id, numero, simulacao_id, regra, resultado, data_inicio, data_fim, ultima_mensagem_id, submetida_em)
            VALUES (v_camp_id, 1, v_sim_id, v_ficha, v_resultado, '2025-08-01', '2025-08-31', v_msg_id, NOW() - INTERVAL '19 days')
            RETURNING id INTO v_versao_id;

            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa) VALUES
            (v_camp_id, NULL, NULL, 'RASCUNHO', v_gabriela_id, NOW() - INTERVAL '20 days', NULL),
            (v_camp_id, v_versao_id, 'RASCUNHO', 'AGUARDANDO_APROVACAO', v_gabriela_id, NOW() - INTERVAL '19 days', NULL),
            (v_camp_id, v_versao_id, 'AGUARDANDO_APROVACAO', 'REPROVADA', v_silvia_id, NOW() - INTERVAL '18 days', 'Custo excedeu o orçamento em R$ 35.000,00; remover cargo 150 ou reduzir percentual.');
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM campanha WHERE chat_id = 'homolog-chat-arquivada') THEN
        v_ficha := '{
          "status": "ARCHIVED",
          "rule_id": "homolog-arquivada-julho",
          "faltantes": [],
          "tipo_regra": "LIQUIDACAO",
          "raw_prompt": "Liquidação de Inverno Julho +0,5% marca Cinza",
          "parametros": [
            {"key": "periodo", "type": "date_range", "label": "Prazo da Campanha", "value": {"data_inicio": "2025-07-01", "data_fim": "2025-07-31"}, "required": true, "categoria": "REGRA"},
            {"key": "pct_acrescimo", "type": "percentage", "label": "Acréscimo de Comissão (%)", "value": 0.5, "required": true, "categoria": "REGRA"},
            {"key": "marcas_alvo", "type": "multi_select", "label": "Marcas Participantes", "value": ["60"], "required": true, "categoria": "REGRA"},
            {"key": "cargos_alvo", "type": "multi_select", "label": "Cargos Elegíveis", "value": ["100", "200"], "required": true, "categoria": "REGRA"},
            {"key": "meta_vendas", "type": "currency", "label": "Meta Financeira da Campanha (R$)", "value": 120000.0, "required": true, "categoria": "CONSTRAINT"},
            {"key": "orcamento_limite", "type": "currency", "label": "Orçamento Máximo de Incentivo (R$)", "value": 2500.0, "required": false, "categoria": "CONSTRAINT"}
          ]
        }'::jsonb;

        v_resultado := '{
          "rule_id": "homolog-arquivada-julho",
          "competencias": ["2025-07"],
          "origem_codigo": "modelo",
          "tentativas": 1,
          "totais": {"baseline": 3800.0, "simulado": 5450.0, "diferenca": 1650.0, "diferenca_pct": 43.42},
          "meta": {"atingida": true, "meta_vendas": 120000.0, "vendas_periodo": 145000.0, "diferenca": 25000.0, "pct_atingimento": 120.83},
          "orcamento": {"limite": 2500.0, "custo_estimado": 1650.0, "saldo": 850.0, "dentro_orcamento": true, "categoria": "Liquidação"},
          "impacto": {"pessoas_total": 30, "pessoas_impactadas": 26, "media_por_pessoa": 63.46, "maior_acrescimo": 140.00},
          "por_cargo": [
            {"codigo": "100", "baseline": 2600.0, "simulado": 3750.0, "diferenca": 1150.0},
            {"codigo": "200", "baseline": 1200.0, "simulado": 1700.0, "diferenca": 500.0}
          ],
          "por_marca": [
            {"codigo": "60", "baseline": 3800.0, "simulado": 5450.0, "diferenca": 1650.0}
          ],
          "explicacao": "Campanha concluída e arquivada de julho/2025 com custo adicional de R$ 1.650,00 dentro do orçamento de R$ 2.500,00."
        }'::jsonb;

        INSERT INTO campanha (nome, autor_id, chat_id, status, criada_em, atualizada_em)
        VALUES ('Liquidação de Inverno - Julho (Arquivada)', v_gabriela_id, 'homolog-chat-arquivada', 'ARQUIVADA', NOW() - INTERVAL '30 days', NOW() - INTERVAL '25 days')
        ON CONFLICT (chat_id) DO NOTHING
        RETURNING id INTO v_camp_id;

        IF v_camp_id IS NOT NULL THEN
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_gabriela_id, 'homolog-chat-arquivada', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 1500, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days')
            RETURNING id INTO v_sim_id;

            -- Segunda simulação de ajuste na mesma campanha (totalizando 8 simulações no seed)
            INSERT INTO simulacao (usuario_id, chat_id, campanha_id, status, etapa, ficha, resultado, modelo, tentativas, duracao_ms, criada_em, concluida_em)
            VALUES (v_gabriela_id, 'homolog-chat-arquivada', v_camp_id, 'CONCLUIDA', 'Concluída', v_ficha, v_resultado, 'llama-3.3-70b-versatile', 1, 1450, NOW() - INTERVAL '29 days', NOW() - INTERVAL '29 days')
            RETURNING id INTO v_sim_v2_id;

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-arquivada', v_gabriela_id, 'usuario', NULL, NULL, 'Liquidação de inverno para marca Cinza em julho/2025.', NULL, NULL, NOW() - INTERVAL '30 days');

            INSERT INTO mensagem_chat (chat_id, usuario_id, papel, tipo, simulacao_id, texto, ficha, trilha, criada_em)
            VALUES ('homolog-chat-arquivada', v_gabriela_id, 'sistema', 'STATUS', v_sim_v2_id, 'Campanha arquivada após encerramento da competência de julho/2025.', NULL, NULL, NOW() - INTERVAL '25 days')
            RETURNING id INTO v_msg_id;

            INSERT INTO campanha_versao (campanha_id, numero, simulacao_id, regra, resultado, data_inicio, data_fim, ultima_mensagem_id, submetida_em)
            VALUES (v_camp_id, 1, v_sim_v2_id, v_ficha, v_resultado, '2025-07-01', '2025-07-31', v_msg_id, NOW() - INTERVAL '28 days')
            RETURNING id INTO v_versao_id;

            INSERT INTO campanha_status_log (campanha_id, versao_id, de, para, usuario_id, em, justificativa) VALUES
            (v_camp_id, NULL, NULL, 'RASCUNHO', v_gabriela_id, NOW() - INTERVAL '30 days', NULL),
            (v_camp_id, v_versao_id, 'RASCUNHO', 'AGUARDANDO_APROVACAO', v_gabriela_id, NOW() - INTERVAL '28 days', NULL),
            (v_camp_id, v_versao_id, 'AGUARDANDO_APROVACAO', 'APROVADA', v_sergio_id, NOW() - INTERVAL '27 days', 'Aprovada para liquidação de julho.'),
            (v_camp_id, v_versao_id, 'APROVADA', 'ARQUIVADA', v_gabriela_id, NOW() - INTERVAL '25 days', 'Encerramento do ciclo de julho.');
        END IF;
    END IF;
END
$seed$;
