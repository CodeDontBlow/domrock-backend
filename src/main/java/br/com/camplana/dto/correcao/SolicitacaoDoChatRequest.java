package br.com.camplana.dto.correcao;

import br.com.camplana.entity.OrigemSolicitacao;
import br.com.camplana.entity.TipoAlteracao;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * Pedido de correção ou intercorrência enviado pelo chat (A6-81).
 * Não inclui origem, chat_id nem autor; se enviados no corpo, são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SolicitacaoDoChatRequest(
        @NotNull(message = "Tipo de alteração é obrigatório")
        TipoAlteracao tipo,

        @NotBlank(message = "Matrícula é obrigatória")
        String matricula,

        @NotBlank(message = "Competência é obrigatória")
        String competencia,

        @JsonAlias("valor_antes")
        JsonNode antes,

        @NotNull(message = "Valor 'depois' é obrigatório")
        @JsonAlias("valor_depois")
        JsonNode depois,

        String motivo
) {
    public SolicitacaoRequest paraSolicitacaoRequest(String chatId) {
        return new SolicitacaoRequest(
                chatId,
                tipo,
                matricula,
                competencia,
                antes,
                depois,
                motivo,
                null,
                OrigemSolicitacao.LANA
        );
    }
}
