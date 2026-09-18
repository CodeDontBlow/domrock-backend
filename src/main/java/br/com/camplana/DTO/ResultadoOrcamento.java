package br.com.camplana.DTO;

import java.math.BigDecimal;

/** Resultado da comparação do custo projetado com o orçamento da campanha. */
public record ResultadoOrcamento(
        BigDecimal orcamento,
        BigDecimal valorProjetado,
        boolean orcamentoExcedido,
        BigDecimal valorExcedente) {
}
