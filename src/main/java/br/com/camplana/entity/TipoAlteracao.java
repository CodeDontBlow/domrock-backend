package br.com.camplana.entity;

public enum TipoAlteracao {
    TRANSFERENCIA_LOJA,
    TROCA_CARGO,
    DATA_DEMISSAO,
    DATA_ADMISSAO,
    AFASTAMENTO,
    FERIAS,
    LICENCA_MATERNIDADE,
    COBERTURA_LOJA,
    DEMISSAO;

    public boolean isIntercorrenciaRh() {
        return switch (this) {
            case AFASTAMENTO, FERIAS, LICENCA_MATERNIDADE, COBERTURA_LOJA, DEMISSAO -> true;
            default -> false;
        };
    }
}
