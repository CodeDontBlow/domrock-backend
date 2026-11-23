package br.com.camplana.dto.campanha;

import br.com.camplana.campanha.AcaoVersao;
import br.com.camplana.entity.StatusCampanha;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;

/**
 * A versão congelada (contrato 5.6), com as {@code acoes} de quem está vendo, a re-simulação pendente,
 * as aprovações do período depois da submissão (A6-68) e alertas técnicos (A6-95).
 */
public record VersaoCampanhaResponse(
        @JsonProperty("campanha_id") long campanhaId,
        int numero,
        StatusCampanha status,
        List<AcaoVersao> acoes,
        UsuarioResumo autor,
        @JsonProperty("submetida_em") Instant submetidaEm,
        @JsonProperty("simulacao_id") long simulacaoId,
        @JsonRawValue String regra,
        @JsonRawValue String resultado,
        @JsonProperty("ultima_mensagem_id") Long ultimaMensagemId,
        @JsonProperty("resimulacao_necessaria") boolean resimulacaoNecessaria,
        @JsonProperty("aprovadas_depois") List<AprovadaDepoisResponse> aprovadasDepois,
        ResimulacaoResponse resimulacao,
        DecisaoResponse decisao,
        @JsonProperty("intercorrencias") List<IntercorrenciaResponse> intercorrencias,
        List<AlertaTecnicoResponse> alertas
) {
    public VersaoCampanhaResponse(long campanhaId, int numero, StatusCampanha status, List<AcaoVersao> acoes,
                                  UsuarioResumo autor, Instant submetidaEm, long simulacaoId, String regra,
                                  String resultado, Long ultimaMensagemId, boolean resimulacaoNecessaria,
                                  List<AprovadaDepoisResponse> aprovadasDepois, ResimulacaoResponse resimulacao,
                                  DecisaoResponse decisao, List<IntercorrenciaResponse> intercorrencias) {
        this(campanhaId, numero, status, acoes, autor, submetidaEm, simulacaoId, regra, resultado,
                ultimaMensagemId, resimulacaoNecessaria, aprovadasDepois, resimulacao, decisao, intercorrencias, List.of());
    }
}
