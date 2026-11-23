package br.com.camplana.campanha;

import br.com.camplana.dto.campanha.AlertaTecnicoResponse;
import br.com.camplana.dto.campanha.CriarAlertaRequest;
import br.com.camplana.dto.campanha.ResolverAlertaRequest;
import br.com.camplana.entity.AlertaTecnico;
import br.com.camplana.entity.Campanha;
import br.com.camplana.entity.OrigemAlerta;
import br.com.camplana.entity.StatusAlerta;
import br.com.camplana.entity.StatusCampanha;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-20 (A6-95): Alertas técnicos que travam a aprovação.
 * Cobre cada critério de aceite da história:
 * - Alerta automático criado quando a Lana manda
 * - Auditor abre e resolve alerta; Supervisor não consegue (403)
 * - Aprovar com alerta aberto dá 409; depois de resolvido, aprova normal
 * - Resolver sem justificativa dá 400
 * - Alerta resolvido continua no histórico com quem, quando e justificativa
 * - Reprovação continua permitida mesmo com alerta aberto
 */
class AlertaTecnicoApiTest extends CampanhaApiBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private AlertaTecnicoService alertaTecnicoService;

    @Test
    void alertaAutomaticoECriadoQuandoLanaManda() throws Exception {
        long id = submeterBlackFriday(gabriela);
        Campanha campanha = campanhaRepository.findById(id).orElseThrow();

        // Lana manda alerta automático para a versão da campanha
        String jsonAlertasLana = """
                [
                  {
                    "tipo": "CONSULTA_BLOQUEADA",
                    "descricao": "Tentativa de acesso à tabela protegida: usuario"
                  },
                  {
                    "tipo": "CODIGO_SUSPEITO",
                    "descricao": "Uso de eval() detectado no script Python gerado"
                  }
                ]
                """;
        alertaTecnicoService.gravarAlertasAutomaticos(campanha, 1, jsonAlertasLana);

        // Supervisor lista alertas
        enviar(get("/api/campanhas/" + id + "/versoes/1/alertas"), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].origem").value("AUTOMATICO"))
                .andExpect(jsonPath("$[0].tipo").value("CONSULTA_BLOQUEADA"))
                .andExpect(jsonPath("$[0].descricao").value("Tentativa de acesso à tabela protegida: usuario"))
                .andExpect(jsonPath("$[0].status").value("ABERTO"))
                .andExpect(jsonPath("$[0].aberto_por").doesNotExist())
                .andExpect(jsonPath("$[0].resolvido_por").doesNotExist())
                .andExpect(jsonPath("$[1].origem").value("AUTOMATICO"))
                .andExpect(jsonPath("$[1].tipo").value("CODIGO_SUSPEITO"))
                .andExpect(jsonPath("$[1].descricao").value("Uso de eval() detectado no script Python gerado"))
                .andExpect(jsonPath("$[1].status").value("ABERTO"));

        // A resposta da versão (GET /api/campanhas/{id}/versoes/1) também traz os alertas para o front
        enviar(get("/api/campanhas/" + id + "/versoes/1"), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertas", hasSize(2)))
                .andExpect(jsonPath("$.alertas[0].tipo").value("CONSULTA_BLOQUEADA"))
                .andExpect(jsonPath("$.alertas[1].tipo").value("CODIGO_SUSPEITO"));
    }

    @Test
    void auditorAbreEResolveAlertaESupervisorNaoConsegue403() throws Exception {
        long id = submeterBlackFriday(gabriela);

        // 1. Supervisor tenta abrir alerta manual -> 403 Forbidden
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), sergio,
                JSON.writeValueAsString(new CriarAlertaRequest("MANUAL", "Supervisor tentando abrir")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));

        // 2. Gerente tenta listar alertas -> 403 Forbidden
        enviar(get("/api/campanhas/" + id + "/versoes/1/alertas"), gabriela)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));

        // 3. Auditor abre alerta manual com sucesso -> 201 Created
        String respostaCriacao = enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                JSON.writeValueAsString(new CriarAlertaRequest("CODIGO_SUSPEITO", "Código requer revisão de auditoria")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.origem").value("MANUAL"))
                .andExpect(jsonPath("$.tipo").value("CODIGO_SUSPEITO"))
                .andExpect(jsonPath("$.descricao").value("Código requer revisão de auditoria"))
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.aberto_por.id").value(paulo.getId()))
                .andExpect(jsonPath("$.aberto_por.nome").value("Paulo"))
                .andReturn().getResponse().getContentAsString();

        long alertaId = ((Number) JsonPath.read(respostaCriacao, "$.id")).longValue();

        // 4. Supervisor tenta resolver alerta -> 403 Forbidden
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), sergio,
                JSON.writeValueAsString(new ResolverAlertaRequest("Supervisor tentando resolver")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));

        // 5. Auditor resolve alerta com justificativa -> 200 OK
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), paulo,
                JSON.writeValueAsString(new ResolverAlertaRequest("Código revisado e considerado seguro pelo auditor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertaId))
                .andExpect(jsonPath("$.status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.resolvido_por.id").value(paulo.getId()))
                .andExpect(jsonPath("$.resolvido_por.nome").value("Paulo"))
                .andExpect(jsonPath("$.justificativa").value("Código revisado e considerado seguro pelo auditor"));
    }

    @Test
    void aprovarComAlertaAbertoDa409DepoisDeResolvidoAprovaNormal() throws Exception {
        long id = submeterBlackFriday(gabriela);

        // Auditor abre um alerta manual
        String respostaCriacao = enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                JSON.writeValueAsString(new CriarAlertaRequest("MANUAL", "Alerta que impede aprovação")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long alertaId = ((Number) JsonPath.read(respostaCriacao, "$.id")).longValue();

        // Na versão, a lista de ações NÃO inclui APROVAR enquanto o alerta estiver aberto
        enviar(get("/api/campanhas/" + id + "/versoes/1"), sergio)
                .andExpect(jsonPath("$.acoes", not(hasItem("APROVAR"))))
                .andExpect(jsonPath("$.acoes", hasItem("REPROVAR")));

        // Supervisor tenta aprovar -> 409 Conflict com a mensagem exata
        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "APROVAR", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Esta versão tem alerta técnico aberto. O Auditor precisa resolvê-lo antes da aprovação."));

        // Auditor resolve o alerta
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), paulo,
                JSON.writeValueAsString(new ResolverAlertaRequest("Verificado e aprovado pelo auditor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"));

        // Após resolver, a ação APROVAR volta a aparecer nas ações da versão
        enviar(get("/api/campanhas/" + id + "/versoes/1"), sergio)
                .andExpect(jsonPath("$.acoes", hasItem("APROVAR")));

        // Supervisor aprova normal -> 200 OK
        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "APROVAR", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"));
    }

    @Test
    void resolverSemJustificativaDa400() throws Exception {
        long id = submeterBlackFriday(gabriela);

        String respostaCriacao = enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                JSON.writeValueAsString(new CriarAlertaRequest("MANUAL", "Alerta para teste de justificativa")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long alertaId = ((Number) JsonPath.read(respostaCriacao, "$.id")).longValue();

        // 1. Justificativa vazia ("") -> 400
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), paulo,
                "{\"justificativa\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe a justificativa para resolver o alerta."));

        // 2. Justificativa em branco ("   ") -> 400
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), paulo,
                "{\"justificativa\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe a justificativa para resolver o alerta."));

        // 3. Objeto sem campo justificativa ({}) -> 400
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), paulo,
                "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Informe a justificativa para resolver o alerta."));
    }

    @Test
    void alertaResolvidoContinuaNoHistoricoComQuemQuandoEJustificativa() throws Exception {
        long id = submeterBlackFriday(gabriela);

        String respostaCriacao = enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                JSON.writeValueAsString(new CriarAlertaRequest("CODIGO_SUSPEITO", "Código suspeito no cálculo")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long alertaId = ((Number) JsonPath.read(respostaCriacao, "$.id")).longValue();

        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas/" + alertaId + "/resolver"), paulo,
                JSON.writeValueAsString(new ResolverAlertaRequest("Justificativa registrada pelo auditor Paulo")))
                .andExpect(status().isOk());

        // Consultando pelo endpoint de alertas (Auditor ou Supervisor)
        enviar(get("/api/campanhas/" + id + "/versoes/1/alertas"), paulo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alertaId))
                .andExpect(jsonPath("$[0].status").value("RESOLVIDO"))
                .andExpect(jsonPath("$[0].aberto_por.id").value(paulo.getId()))
                .andExpect(jsonPath("$[0].aberto_por.nome").value("Paulo"))
                .andExpect(jsonPath("$[0].aberto_em").isNotEmpty())
                .andExpect(jsonPath("$[0].resolvido_por.id").value(paulo.getId()))
                .andExpect(jsonPath("$[0].resolvido_por.nome").value("Paulo"))
                .andExpect(jsonPath("$[0].resolvido_em").isNotEmpty())
                .andExpect(jsonPath("$[0].justificativa").value("Justificativa registrada pelo auditor Paulo"));

        // Consultando pela versão (VersaoCampanhaResponse.alertas)
        enviar(get("/api/campanhas/" + id + "/versoes/1"), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertas[0].id").value(alertaId))
                .andExpect(jsonPath("$.alertas[0].status").value("RESOLVIDO"))
                .andExpect(jsonPath("$.alertas[0].resolvido_por.nome").value("Paulo"))
                .andExpect(jsonPath("$.alertas[0].justificativa").value("Justificativa registrada pelo auditor Paulo"));
    }

    @Test
    void reprovarContinuaPermitidoComAlertaAberto() throws Exception {
        long id = submeterBlackFriday(gabriela);

        // Abre alerta técnico
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                JSON.writeValueAsString(new CriarAlertaRequest("MANUAL", "Alerta que impede aprovação mas não reprovação")))
                .andExpect(status().isCreated());

        // Supervisor reprova com justificativa válida -> 200 OK
        enviar(post("/api/campanhas/" + id + "/decidir"), sergio, decisao(1, "REPROVAR", REPROVACAO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADA"));
    }

    @Test
    void abrirAlertaSemDescricaoDa400() throws Exception {
        long id = submeterBlackFriday(gabriela);

        // Sem descrição
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                "{\"tipo\":\"MANUAL\"}")
                .andExpect(status().isBadRequest());

        // Descrição vazia
        enviar(post("/api/campanhas/" + id + "/versoes/1/alertas"), paulo,
                "{\"descricao\":\"   \"}")
                .andExpect(status().isBadRequest());
    }
}
