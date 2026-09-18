package br.com.camplana.Service;

import br.com.camplana.DTO.ResultadoOrcamento;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class OrcamentoService {

    /**
     * Compara o total projetado pela simulação com o orçamento, na mesma moeda.
     * Valores iguais estão dentro do orçamento. A precisão recebida é preservada,
     * sem arredondamento, e o excedente é zero quando não há estouro.
     *
     * @throws IllegalArgumentException se algum valor for nulo ou negativo
     */
    public ResultadoOrcamento verificarOrcamento(BigDecimal orcamento, BigDecimal valorProjetado) {
        validarValor(orcamento, "Orçamento");
        validarValor(valorProjetado, "Valor projetado");

        boolean orcamentoExcedido = valorProjetado.compareTo(orcamento) > 0;
        BigDecimal valorExcedente = orcamentoExcedido
                ? valorProjetado.subtract(orcamento)
                : BigDecimal.ZERO;

        return new ResultadoOrcamento(orcamento, valorProjetado, orcamentoExcedido, valorExcedente);
    }

    private void validarValor(BigDecimal valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException(nome + " deve ser informado.");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(nome + " não pode ser negativo.");
        }
    }
}
