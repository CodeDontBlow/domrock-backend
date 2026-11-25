package br.com.camplana.dto.campanha;

import jakarta.validation.constraints.NotBlank;

public record CriarAlertaRequest(
        String tipo,
        @NotBlank(message = "Descrição obrigatória") String descricao
) {}
