package br.com.camplana.repository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EventoRhRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public boolean existe(String matricula, String tipo, LocalDate inicio, LocalDate fim, String loja) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from evento_rh where matricula = :matricula and tipo = :tipo
                    and data_inicio = :inicio and data_fim is not distinct from cast(:fim as date)
                    and cod_loja is not distinct from cast(:loja as varchar))
                """, parametros(matricula, tipo, inicio, fim, loja), Boolean.class));
    }

    public void inserir(Integer solicitacaoId, String matricula, String tipo,
                        LocalDate inicio, LocalDate fim, String loja) {
        Map<String, Object> params = parametros(matricula, tipo, inicio, fim, loja);
        params.put("origem", "solicitacao_alteracao:" + solicitacaoId);
        jdbc.update("""
                insert into evento_rh (matricula, tipo, data_inicio, data_fim, cod_loja, origem)
                values (:matricula, :tipo, :inicio, :fim, :loja, :origem)
                """, params);
    }

    private Map<String, Object> parametros(String matricula, String tipo, LocalDate inicio,
                                          LocalDate fim, String loja) {
        Map<String, Object> params = new HashMap<>();
        params.put("matricula", matricula);
        params.put("tipo", tipo);
        params.put("inicio", inicio);
        params.put("fim", fim);
        params.put("loja", loja);
        return params;
    }
}
