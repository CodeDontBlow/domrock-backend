package br.com.camplana.campanha;

import br.com.camplana.entity.StatusCampanha;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Abas de Campanhas Passadas (Realizadas, Em andamento, Previstas): a {@code vigencia} de
 * {@code GET /api/campanhas/aprovadas} sai do relógio do servidor, nunca do navegador.
 */
class CampanhasAprovadasVigenciaApiTest extends CampanhaApiBase {

    // Meio-dia em Brasília: o dia não muda com o fuso.
    private static final Instant DIA_25_11_2025 = Instant.parse("2025-11-25T15:00:00Z");
    private static final Instant DIA_01_12_2025 = Instant.parse("2025-12-01T15:00:00Z");
    private static final Instant DIA_23_11_2025 = Instant.parse("2025-11-23T15:00:00Z");

    @Autowired private RelogioDeTeste relogio;

    @AfterEach
    void voltarRelogio() {
        relogio.voltarAoPadrao();
    }

    @Test
    void blackFridayEmAndamentoNoDia25DeNovembro() throws Exception {
        long id = aprovarBlackFriday();
        relogio.fixarEm(DIA_25_11_2025);

        enviar(get("/api/campanhas/aprovadas"), sergio)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].nome").value("Black Friday 2025"))
                .andExpect(jsonPath("$[0].vigencia").value("VIGENTE"));
    }

    @Test
    void blackFridayRealizadaNoDia1DeDezembro() throws Exception {
        aprovarBlackFriday();
        relogio.fixarEm(DIA_01_12_2025);

        enviar(get("/api/campanhas/aprovadas"), sergio)
                .andExpect(jsonPath("$[0].vigencia").value("ENCERRADA"));
    }

    @Test
    void blackFridayPrevistaAntesDoInicio() throws Exception {
        aprovarBlackFriday();
        relogio.fixarEm(DIA_23_11_2025);

        enviar(get("/api/campanhas/aprovadas"), sergio)
                .andExpect(jsonPath("$[0].vigencia").value("FUTURA"));
    }

    @Test
    void soAsAprovadasEntramNasAbas() throws Exception {
        long aprovada = aprovarBlackFriday();
        salvarBlackFriday(gabriela);                                      // rascunho
        submeterBlackFriday(gabriela);                                    // aguardando
        long reprovada = submeterBlackFriday(gabriela);
        decidir(reprovada, StatusCampanha.REPROVADA, sergio, REPROVACAO);
        long arquivada = salvarBlackFriday(gabriela);
        enviar(post("/api/campanhas/" + arquivada + "/arquivar"), gabriela).andExpect(status().isOk());
        relogio.fixarEm(DIA_25_11_2025);

        enviar(get("/api/campanhas/aprovadas"), sergio)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(aprovada))
                .andExpect(jsonPath("$[*].vigencia", containsInAnyOrder("VIGENTE")));
    }

    @Test
    void trazQuemAprovouEOsConflitosDaSimulacao() throws Exception {
        aprovarBlackFriday();

        enviar(get("/api/campanhas/aprovadas"), gabriela)
                .andExpect(jsonPath("$[0].autor.nome").value("Gabriela"))
                .andExpect(jsonPath("$[0].aprovador.nome").value("Sérgio"))
                .andExpect(jsonPath("$[0].conflitos", hasSize(0)));
    }

    @Test
    void conflitoDaSimulacaoAprovadaVemNoItem() throws Exception {
        String chatId = UUID.randomUUID().toString();
        simulacoes.concluida(900, gabriela.getId(), chatId, RESULTADO_COM_CONFLITO);
        String resposta = enviar(post("/api/campanhas"), gabriela, salvar("Black Friday 2025", chatId, 900))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        enviar(post("/api/campanhas/" + id + "/submeter"), gabriela).andExpect(status().isOk());
        decidir(id, StatusCampanha.APROVADA, sergio, null);

        enviar(get("/api/campanhas/aprovadas"), sergio)
                .andExpect(jsonPath("$[0].conflitos", hasSize(1)))
                .andExpect(jsonPath("$[0].conflitos[0].nome").value("Esquenta Black Friday"))
                .andExpect(jsonPath("$[0].conflitos[0].sobreposicao.data_inicio").value("2025-11-24"));
    }

    private static final String RESULTADO_COM_CONFLITO = """
            {"totais": {"baseline": 480263.24, "simulado": 503999.34, "diferenca": 23736.10},
             "conflitos": [{"campanha_id": 7, "versao": 1, "tipo": "ACRESCIMO", "nome": "Esquenta Black Friday",
                            "status": "APROVADA", "autor": "Roberval", "data_inicio": "2025-11-20",
                            "data_fim": "2025-11-26", "marcas": ["10"], "cargos": ["100"],
                            "sobreposicao": {"data_inicio": "2025-11-24", "data_fim": "2025-11-26"}}],
             "codigo": "resultado = calcular(vendas)"}""";

    private long aprovarBlackFriday() throws Exception {
        long id = submeterBlackFriday(gabriela);
        decidir(id, StatusCampanha.APROVADA, sergio, null);
        return id;
    }
}
