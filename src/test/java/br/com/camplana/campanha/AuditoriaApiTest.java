package br.com.camplana.campanha;

import br.com.camplana.entity.*;
import br.com.camplana.repository.SolicitacaoAlteracaoRepository;
import br.com.camplana.service.SolicitacaoAlteracaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contratos consumidos pela tela A6-103, com JWT, migrations e PostgreSQL reais. */
class AuditoriaApiTest extends CampanhaApiBase {

    @Autowired private SolicitacaoAlteracaoRepository solicitacoes;
    @Autowired private SolicitacaoAlteracaoService correcoes;

    @BeforeEach
    @AfterEach
    void limparCorrecoesDaAuditoria() {
        solicitacoes.deleteAll();
        jdbc.update("delete from simulacao where chat_id = 'auditoria-teste-simulacao'");
    }

    @Test
    void listaCampanhasDeTodosOsAutoresEStatusComAutorPeriodoEUltimaAlteracao() throws Exception {
        salvarBlackFriday(gabriela);
        submeterBlackFriday(sergio);
        long aprovada = submeterBlackFriday(gabriela);
        decidir(aprovada, StatusCampanha.APROVADA, sergio, null);
        long reprovada = submeterBlackFriday(sergio);
        decidir(reprovada, StatusCampanha.REPROVADA, silvia, "Rever meta");
        long arquivada = salvarBlackFriday(silvia);
        enviar(post("/api/campanhas/" + arquivada + "/arquivar"), silvia).andExpect(status().isOk());

        enviar(get("/api/campanhas/auditoria"), paulo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].status", containsInAnyOrder("RASCUNHO", "AGUARDANDO_APROVACAO", "APROVADA", "REPROVADA", "ARQUIVADA")))
                .andExpect(jsonPath("$[*].autor.nome", hasItems("Gabriela", "Sérgio", "Sílvia")))
                .andExpect(jsonPath("$[*].autor.id", everyItem(notNullValue())))
                .andExpect(jsonPath("$[*].periodo.data_inicio", everyItem(is("2025-11-24"))))
                .andExpect(jsonPath("$[*].atualizada_em", everyItem(notNullValue())));
        enviar(get("/api/campanhas/auditoria?status=ARQUIVADA"), paulo)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(arquivada))
                .andExpect(jsonPath("$[0].autor.id").value(silvia.getId()));
        enviar(get("/api/campanhas/auditoria?status=INVALIDO"), paulo).andExpect(status().isBadRequest());
    }

    @Test
    void listaCorrecoesDeTodosOsAutoresEStatusNoFormatoDoDetalheSemAlterarDados() throws Exception {
        SolicitacaoAlteracao pendente = registrar(StatusSolicitacao.PENDENTE, paulo, 1);
        SolicitacaoAlteracao aprovada = registrar(StatusSolicitacao.APROVADA, gabriela, 2);
        SolicitacaoAlteracao reprovada = registrar(StatusSolicitacao.REPROVADA, silvia, 3);
        enviar(get("/api/correcoes/auditoria"), paulo)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].id", contains(reprovada.getId(), aprovada.getId(), pendente.getId())))
                .andExpect(jsonPath("$[0].status").value("REPROVADA"))
                .andExpect(jsonPath("$[0].decisao.por.nome").value("Sérgio"))
                .andExpect(jsonPath("$[0].decisao.justificativa").value("Conferido com RH"))
                .andExpect(jsonPath("$[0].decisao.em").value("2026-11-04T12:00:00Z"))
                .andExpect(jsonPath("$[1].antes").value("33"))
                .andExpect(jsonPath("$[1].depois").value("8"))
                .andExpect(jsonPath("$[1].matricula").value("MATRIC-38"))
                .andExpect(jsonPath("$[1].competencia").value("2026-11"))
                .andExpect(jsonPath("$[1].autor.nome").value("Gabriela"))
                .andExpect(jsonPath("$[1].decisao.decisao").value("APROVADA"))
                .andExpect(jsonPath("$[2].decisao").doesNotExist());
        enviar(get("/api/correcoes/" + aprovada.getId()), paulo)
                .andExpect(status().isOk()).andExpect(jsonPath("$.antes").value("33"))
                .andExpect(jsonPath("$.decisao.por.id").value(sergio.getId()));
        assertThat(solicitacoes.count()).isEqualTo(3);
        assertThat(solicitacoes.findById(pendente.getId()).orElseThrow().getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
        assertThat(jdbc.queryForObject("select count(*) from evento_rh", Integer.class)).isZero();
    }

    @Test
    void listaVaziaRetornaArrayVazio() throws Exception {
        enviar(get("/api/correcoes/auditoria"), paulo).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        enviar(get("/api/campanhas/auditoria"), paulo).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE", "SUPERVISOR", "ADMIN"})
    void consultasGlobaisSaoExclusivasDoAuditor(String perfil) throws Exception {
        Usuario usuario = switch (perfil) {
            case "GERENTE" -> gabriela;
            case "SUPERVISOR" -> sergio;
            default -> usuarioRepository.findById(4).orElseThrow();
        };
        for (String url : new String[]{"/api/correcoes/auditoria", "/api/campanhas/auditoria"}) {
            enviar(get(url), usuario).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Seu perfil não permite esta ação."));
        }
        assertThatThrownBy(() -> correcoes.listarAuditoria(usuario)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semSessaoNaoAcessaConsultasGlobais() throws Exception {
        mvc.perform(get("/api/correcoes/auditoria")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/campanhas/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    void quemVirouAuditorNaoRecebeAcoesMesmoEmCampanhaDeSuaAutoria() throws Exception {
        long id = salvarBlackFriday(gabriela);
        gabriela.setPerfil(Perfil.AUDITOR);
        usuarioRepository.saveAndFlush(gabriela);
        try {
            enviar(get("/api/campanhas/" + id), gabriela).andExpect(status().isOk())
                    .andExpect(jsonPath("$.acoes", empty()));
            enviar(put("/api/campanhas/" + id), gabriela, "{\"nome\":\"Alterado\"}")
                    .andExpect(status().isForbidden());
        } finally {
            gabriela.setPerfil(Perfil.GERENTE);
            usuarioRepository.saveAndFlush(gabriela);
        }
    }

    @Test
    void auditorConsultaResultadoDeSimulacaoAlheiaSemIniciarOutra() throws Exception {
        Long id = jdbc.queryForObject("""
                insert into simulacao (usuario_id, chat_id, status, ficha, resultado, criada_em)
                values (?, 'auditoria-teste-simulacao', 'CONCLUIDA', '{}', '{"totais":{"diferenca":10}}', now())
                returning id
                """, Long.class, gabriela.getId());
        enviar(get("/api/simulacoes/" + id), paulo).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado.totais.diferenca").value(10));
        enviar(post("/api/simulacoes"), paulo, "{}").andExpect(status().isForbidden());
    }

    @Test
    void auditorNaoDecideCorrecoesNemEntraNaFilaDoSupervisor() throws Exception {
        int id = registrar(StatusSolicitacao.PENDENTE, gabriela, 1).getId();
        enviar(post("/api/correcoes/" + id + "/decidir"), paulo, "{\"decisao\":\"APROVAR\"}")
                .andExpect(status().isForbidden());
        enviar(get("/api/correcoes/fila"), paulo).andExpect(status().isForbidden());
        assertThat(solicitacoes.findById(id).orElseThrow().getStatus()).isEqualTo(StatusSolicitacao.PENDENTE);
    }

    private SolicitacaoAlteracao registrar(StatusSolicitacao status, Usuario autor, int dia) {
        boolean decidida = status != StatusSolicitacao.PENDENTE;
        return solicitacoes.saveAndFlush(SolicitacaoAlteracao.builder()
                .tipo(TipoAlteracao.TRANSFERENCIA_LOJA).matricula("MATRIC-38")
                .competencia(LocalDate.of(2026, 11, 1)).valorAntes("\"33\"").valorDepois("\"8\"")
                .motivo("Transferência de loja").origem(OrigemSolicitacao.USUARIO).autor(autor).status(status)
                .criadaEm(LocalDateTime.of(2026, 11, dia, 12, 0))
                .decisor(decidida ? sergio : null)
                .decididaEm(decidida ? LocalDateTime.of(2026, 11, 4, 12, 0) : null)
                .justificativa(decidida ? "Conferido com RH" : null).build());
    }
}
