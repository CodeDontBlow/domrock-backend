package br.com.camplana.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

/**
 * Mensagem do chat. `chat_id` identifica a conversa (thread do agente); `regra` é opcional e
 * leva os parâmetros como estão no painel, com as edições do usuário, para o agente partir deles;
 * `solicitacao` é opcional e leva o rascunho do cartão do chat ainda não confirmado (A6-81).
 */
public record ChatRequest(
        @JsonProperty("chat_id") @NotBlank String chatId,
        @NotBlank String message,
        JsonNode regra,
        JsonNode solicitacao
) {
    public ChatRequest(String chatId, String message, JsonNode regra) {
        this(chatId, message, regra, null);
    }
}
