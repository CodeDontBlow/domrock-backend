package br.com.camplana.client;

import br.com.camplana.exception.LanaIndisponivelException;
import br.com.camplana.exception.LanaRespostaException;
import br.com.camplana.grpc.InvokeResponse;
import br.com.camplana.service.IaGrpcClientService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Chat com a Lana (agente de IA) por gRPC (A6-53) ou HTTP (/agent/invoke quando
 * lana.chat.transport=http, útil em PaaS sem suporte a gRPC raw entre serviços).
 * A simulação continua em HTTP: {@link LanaSimulacaoClient}.
 * Ex.: lanaClient.invocarAgente(Map.of("chat_id", "c1", "message", "oi"))
 */
@Component
public class LanaClient {

    private static final String CAMINHO_HTTP = "/agent/invoke";

    private final IaGrpcClientService iaGrpcClientService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String chatTransport;

    public LanaClient(IaGrpcClientService iaGrpcClientService, ObjectMapper objectMapper) {
        this(iaGrpcClientService, objectMapper, null, "", "grpc");
    }

    @Autowired
    public LanaClient(
            IaGrpcClientService iaGrpcClientService,
            ObjectMapper objectMapper,
            @Autowired(required = false) RestClient.Builder restClientBuilder,
            @Value("${lana.url:}") String lanaUrl,
            @Value("${lana.chat.transport:grpc}") String chatTransport) {
        this.iaGrpcClientService = iaGrpcClientService;
        this.objectMapper = objectMapper;
        this.chatTransport = chatTransport != null ? chatTransport.trim().toLowerCase() : "grpc";
        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        String baseUrl = lanaUrl != null ? lanaUrl.trim().replaceAll("/+$", "") : "";
        this.restClient = !baseUrl.isEmpty() ? builder.baseUrl(baseUrl).build() : null;
    }

    public JsonNode invocarAgente(Object corpo) {
        return invocarAgente(corpo, "");
    }

    public JsonNode invocarAgente(Object corpo, String historicoJson) {
        JsonNode pedido = objectMapper.valueToTree(corpo);
        String chatId = pedido.hasNonNull("chat_id") ? pedido.get("chat_id").asString() : "default";
        String mensagem = pedido.hasNonNull("message") ? pedido.get("message").asString() : "";
        String historico = historicoJson != null ? historicoJson : "";
        if ("http".equals(chatTransport)) {
            return chamarViaHttp(chatId, mensagem, pedido.path("regra"), historico);
        }
        InvokeResponse resposta = chamar(mensagem, chatId, regraEmJson(pedido), historico);
        return montarResposta(chatId, resposta);
    }

    private JsonNode chamarViaHttp(String chatId, String mensagem, JsonNode regra, String historicoJson) {
        if (restClient == null) {
            throw new LanaIndisponivelException("LANA_URL não configurada para transporte HTTP do chat.");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("chat_id", chatId);
        payload.put("message", mensagem);
        if (regra.isObject()) {
            payload.set("regra", regra);
        }
        if (!historicoJson.isBlank()) {
            payload.set("historico", objectMapper.readTree(historicoJson));
        }
        try {
            return restClient.post()
                    .uri(CAMINHO_HTTP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new LanaRespostaException(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new LanaIndisponivelException("Lana indisponível em " + CAMINHO_HTTP + ": " + e.getMessage());
        }
    }

    // A ficha editada no painel vai junto, para o agente partir dela; sem ficha, "".
    private String regraEmJson(JsonNode pedido) {
        JsonNode regra = pedido.path("regra");
        return regra.isObject() ? objectMapper.writeValueAsString(regra) : "";
    }

    private InvokeResponse chamar(String mensagem, String chatId, String regraJson, String historicoJson) {
        try {
            return iaGrpcClientService.chamarAgenteLana(mensagem, chatId, regraJson, historicoJson);
        } catch (StatusRuntimeException e) {
            throw traduzirFalha(e.getStatus());
        }
    }

    // Os mesmos status do antigo HTTP: cota da IA = 503 e demora = 504, com etapa e mensagem
    // para o front mostrar; qualquer outra falha = Lana indisponível (502).
    private RuntimeException traduzirFalha(Status status) {
        String detalhe = status.getDescription() == null ? "" : status.getDescription();
        if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
            return new LanaRespostaException(503, falhaEmJson("cota_llm", detalhe));
        }
        if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
            return new LanaRespostaException(504, falhaEmJson("tempo", detalhe));
        }
        return new LanaIndisponivelException("Lana falhou no chat (gRPC " + status.getCode() + "): " + detalhe);
    }

    private String falhaEmJson(String etapa, String mensagem) {
        ObjectNode falha = objectMapper.createObjectNode();
        falha.put("etapa", etapa);
        falha.put("mensagem", mensagem);
        return objectMapper.writeValueAsString(falha);
    }

    private JsonNode montarResposta(String chatId, InvokeResponse resposta) {
        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.put("chat_id", chatId);
        corpo.put("response", resposta.getResposta());
        corpo.set("events", lerJson(resposta.getEventsJson(), objectMapper.createArrayNode()));
        corpo.set("regra", lerJson(resposta.getRegraJson(), objectMapper.getNodeFactory().nullNode()));
        corpo.set("trilha", lerJson(resposta.getTrilhaJson(), objectMapper.getNodeFactory().nullNode()));
        return corpo;
    }

    // Eventos, ficha e trilha viajam como texto JSON no .proto; vazio = não vieram (guardrail, turno sem
    // ficha nova ou resposta sem ferramenta).
    private JsonNode lerJson(String json, JsonNode seVazio) {
        return json.isEmpty() ? seVazio : objectMapper.readTree(json);
    }
}
