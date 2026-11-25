package br.com.camplana.dto.campanha;

import br.com.camplana.entity.AlertaTecnico;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AlertaTecnicoResponse(
        Long id,
        @JsonProperty("campanha_id") Long campanhaId,
        @JsonProperty("numero_versao") int numeroVersao,
        String origem,
        String tipo,
        String descricao,
        String status,
        @JsonProperty("aberto_por") UsuarioResumo abertoPor,
        @JsonProperty("aberto_em") Instant abertoEm,
        @JsonProperty("resolvido_por") UsuarioResumo resolvidoPor,
        @JsonProperty("resolvido_em") Instant resolvidoEm,
        String justificativa
) {
    public static AlertaTecnicoResponse from(AlertaTecnico alerta) {
        UsuarioResumo aberto = alerta.getAbertoPor() != null
                ? new UsuarioResumo(alerta.getAbertoPor().getId(), alerta.getAbertoPor().getNome())
                : null;
        UsuarioResumo resolvido = alerta.getResolvidoPor() != null
                ? new UsuarioResumo(alerta.getResolvidoPor().getId(), alerta.getResolvidoPor().getNome())
                : null;
        return new AlertaTecnicoResponse(
                alerta.getId(),
                alerta.getCampanha().getId(),
                alerta.getNumeroVersao(),
                alerta.getOrigem().name(),
                alerta.getTipo(),
                alerta.getDescricao(),
                alerta.getStatus().name(),
                aberto,
                alerta.getAbertoEm(),
                resolvido,
                alerta.getResolvidoEm(),
                alerta.getJustificativa()
        );
    }
}
