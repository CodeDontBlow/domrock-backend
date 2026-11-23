package br.com.camplana.dto.campanha;

import br.com.camplana.campanha.Vigencia;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Item de {@code GET /api/campanhas/aprovadas}: uma campanha aprovada, lida da versão aprovada.
 * {@code marcas} já vem com "ALL" trocado pelas seis marcas. {@code vigencia} sai do relógio do servidor
 * (o front só agrupa por ela) e {@code conflitos} são os da simulação sobre a qual a aprovação valeu.
 */
public record CampanhaAprovadaResponse(
        long id,
        String nome,
        int versao,
        UsuarioResumo autor,
        UsuarioResumo aprovador,
        PeriodoCampanha periodo,
        Vigencia vigencia,
        @JsonProperty("pct_acrescimo") JsonNode pctAcrescimo,
        List<String> marcas,
        List<String> cargos,
        @JsonProperty("custo_adicional") BigDecimal custoAdicional,
        @JsonProperty("submetida_em") Instant submetidaEm,
        @JsonProperty("simulacao_id") long simulacaoId,
        JsonNode conflitos
) {}
