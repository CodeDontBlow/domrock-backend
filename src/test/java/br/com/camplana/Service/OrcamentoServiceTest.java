package br.com.camplana.Service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.camplana.DTO.ResultadoOrcamento;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrcamentoServiceTest {

    private final OrcamentoService service = new OrcamentoService();

    @ParameterizedTest(name = "orçamento={0}, projeção={1}, excedido={2}, excedente={3}")
    @CsvSource({
            "10000.00, 12000.00, true, 2000.00",
            "10000.00, 8000.00, false, 0",
            "10000.00, 10000.00, false, 0",
            "10000.0, 10000.00, false, 0",
            "100.00, 100.01, true, 0.01",
            "100.00, 99.99, false, 0",
            "0, 0, false, 0",
            "0, 0.01, true, 0.01",
            "100.00, 0, false, 0",
            "100.00, 100.001, true, 0.001",
            "9999999999999999.99, 10000000000000000.00, true, 0.01"
    })
    void deveCompararProjecaoComOrcamento(BigDecimal orcamento, BigDecimal valorProjetado,
            boolean excedidoEsperado, BigDecimal excedenteEsperado) {
        ResultadoOrcamento resultado = service.verificarOrcamento(orcamento, valorProjetado);

        assertAll(
                () -> assertEquals(orcamento, resultado.orcamento()),
                () -> assertEquals(valorProjetado, resultado.valorProjetado()),
                () -> assertEquals(excedidoEsperado, resultado.orcamentoExcedido()),
                () -> assertEquals(0, excedenteEsperado.compareTo(resultado.valorExcedente())));
    }

    @ParameterizedTest
    @CsvSource({
            ", 100, Orçamento deve ser informado.",
            "100, , Valor projetado deve ser informado.",
            "-0.01, 100, Orçamento não pode ser negativo.",
            "100, -0.01, Valor projetado não pode ser negativo."
    })
    void deveRejeitarValoresInvalidos(BigDecimal orcamento, BigDecimal valorProjetado,
            String mensagemEsperada) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.verificarOrcamento(orcamento, valorProjetado));

        assertEquals(mensagemEsperada, exception.getMessage());
    }
}
