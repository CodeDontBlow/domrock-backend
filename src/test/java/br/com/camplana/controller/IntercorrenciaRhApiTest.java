package br.com.camplana.controller;

import br.com.camplana.TestcontainersConfiguration;
import br.com.camplana.entity.Usuario;
import br.com.camplana.dto.correcao.DecisaoRequest;
import br.com.camplana.dto.correcao.SolicitacaoRequest;
import br.com.camplana.entity.OrigemSolicitacao;
import br.com.camplana.entity.TipoAlteracao;
import br.com.camplana.service.SolicitacaoAlteracaoService;
import br.com.camplana.repository.UsuarioRepository;
import br.com.camplana.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A6-111: HTTP autenticado, transações e locks reais em PostgreSQL. */
@SpringBootTest(properties = "spring.application.name=intercorrencia-rh-test")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntercorrenciaRhApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JwtService jwt;
    @Autowired private SolicitacaoAlteracaoService service;
    private Usuario gerente, supervisor, outraSupervisora, admin;

    @BeforeEach
    void preparar() {
        jdbc.update("delete from evento_rh where matricula = 'RH-111'");
        jdbc.update("delete from solicitacao_alteracao where matricula = 'RH-111'");
        jdbc.update("""
                insert into funcionario (matricula, data_admissao) values ('RH-111', '2024-01-01')
                on conflict (matricula) do nothing
                """);
        jdbc.update("""
                insert into marca (cod_marca, descr_marca) values ('RH111', 'Marca teste RH')
                on conflict (cod_marca) do nothing
                """);
        jdbc.update("""
                insert into loja (cod_loja, descr_loja, marca_id)
                select 'RH111', 'Loja teste RH', id from marca where cod_marca = 'RH111'
                on conflict (cod_loja) do nothing
                """);
        gerente = usuarios.findById(1).orElseThrow();
        supervisor = usuarios.findById(2).orElseThrow();
        outraSupervisora = usuarios.findById(3).orElseThrow();
        admin = usuarios.findById(4).orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AFASTAMENTO", "FERIAS", "LICENCA_MATERNIDADE", "COBERTURA_LOJA", "DEMISSAO"})
    void cincoTiposNascemPendentesEAprovacaoGravaEvento(String tipo) throws Exception {
        ObjectNode pedido = pedido(tipo);
        pedido.put("autor", "forjado");
        int id = criar(pedido, gerente);
        assertThat(eventos()).isZero();
        enviar(get("/api/correcoes/" + id), gerente, null)
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.autor.id").value(gerente.getId()))
                .andExpect(jsonPath("$.antes").doesNotExist())
                .andExpect(jsonPath("$.depois.data_inicio").value("2026-11-13"))
                .andExpect(jsonPath("$.depois.data_fim").value("2026-11-20"));
        decidir(id, supervisor, "APROVAR", "Conferido")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.decisao.por.id").value(supervisor.getId()))
                .andExpect(jsonPath("$.decisao.em").isNotEmpty())
                .andExpect(jsonPath("$.decisao.justificativa").value("Conferido"));
        assertThat(eventos()).isEqualTo(1);
        var evento = jdbc.queryForMap("select * from evento_rh where matricula = 'RH-111'");
        assertThat(evento.get("tipo")).isEqualTo(tipo.toLowerCase(java.util.Locale.ROOT));
        assertThat(evento.get("origem")).isEqualTo("solicitacao_alteracao:" + id);
        assertThat(evento.get("cod_loja")).isEqualTo(tipo.equals("COBERTURA_LOJA") ? "RH111" : null);
        // DEMISSAO é um evento, não uma edição silenciosa do cadastro do funcionário.
        assertThat(jdbc.queryForObject("select data_demissao from funcionario where matricula = 'RH-111'",
                java.sql.Date.class)).isNull();
    }

    @Test
    void matriculaInexistenteRecusada() throws Exception {
        enviar(post("/api/correcoes"), gerente, pedido("FERIAS").put("matricula", "INEXISTENTE"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"data_inicio", "data_fim"})
    void datasObrigatorias(String campo) throws Exception {
        ObjectNode pedido = pedido("FERIAS");
        dados(pedido).remove(campo);
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-12", "2026-02-30", "13/11/2026", ""})
    void datasInvalidasOuFimAnteriorRecusados(String fim) throws Exception {
        ObjectNode pedido = pedido("FERIAS");
        dados(pedido).put("data_fim", fim);
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
    }

    @Test
    void periodoDeUmDiaAceito() throws Exception {
        ObjectNode pedido = pedido("DEMISSAO");
        dados(pedido).put("data_fim", "2026-11-13");
        decidir(criar(pedido, gerente), supervisor, "APROVAR", null).andExpect(status().isOk());
        assertThat(eventos()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "INEXISTE"})
    void coberturaExigeLojaExistente(String loja) throws Exception {
        ObjectNode pedido = pedido("COBERTURA_LOJA");
        dados(pedido).put("cod_loja", loja);
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
    }

    @Test
    void coberturaSemLojaRecusada() throws Exception {
        ObjectNode pedido = pedido("COBERTURA_LOJA");
        dados(pedido).remove("cod_loja");
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
    }

    @Test
    void lojaEmTipoSemCoberturaRecusada() throws Exception {
        ObjectNode pedido = pedido("FERIAS");
        dados(pedido).put("cod_loja", "RH111");
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
    }

    @Test
    void motivoObrigatorio() throws Exception {
        ObjectNode pedido = pedido("FERIAS");
        pedido.remove("motivo");
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
        enviar(post("/api/correcoes"), gerente, pedido.put("motivo", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void payloadInvalidoRecusado() throws Exception {
        enviar(post("/api/correcoes"), gerente, pedido("FERIAS").put("depois", "2026-11-13"))
                .andExpect(status().isBadRequest());
        enviar(post("/api/correcoes"), gerente, pedido("FERIAS").put("antes", "valor"))
                .andExpect(status().isBadRequest());
        ObjectNode pedido = pedido("FERIAS");
        dados(pedido).put("data_inicio", 13);
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
        pedido = pedido("FERIAS");
        dados(pedido).put("campo_errado", "valor");
        enviar(post("/api/correcoes"), gerente, pedido).andExpect(status().isBadRequest());
    }

    @Test
    void pendenteDuplicadaRecusadaMesmoComOutroAutorMotivoEEspacos() throws Exception {
        criar(pedido("COBERTURA_LOJA"), gerente);
        ObjectNode repetido = pedido("COBERTURA_LOJA").put("matricula", " RH-111 ").put("motivo", "Outro motivo");
        dados(repetido).put("cod_loja", " RH111 ");
        enviar(post("/api/correcoes"), supervisor, repetido).andExpect(status().isConflict());
        assertThat(pedidos()).isEqualTo(1);
    }

    @Test
    void importadaContaComoEfetivaEBloqueiaDuplicada() throws Exception {
        importarEvento();
        enviar(post("/api/correcoes"), gerente, pedido("FERIAS")).andExpect(status().isConflict());
        assertThat(eventos()).isEqualTo(1);
        assertThat(pedidos()).isZero();
    }

    @Test
    void aprovadaBloqueiaNovoPedidoIgual() throws Exception {
        decidir(criar(pedido("FERIAS"), gerente), supervisor, "APROVAR", null).andExpect(status().isOk());
        enviar(post("/api/correcoes"), outraSupervisora, pedido("FERIAS")).andExpect(status().isConflict());
        assertThat(eventos()).isEqualTo(1);
    }

    @Test
    void importacaoPosteriorImpedeAprovacaoEMantemPendente() throws Exception {
        int id = criar(pedido("FERIAS"), gerente);
        importarEvento();
        decidir(id, supervisor, "APROVAR", null).andExpect(status().isConflict());
        enviar(get("/api/correcoes/" + id), gerente, null)
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.decisao").doesNotExist());
        assertThat(eventos()).isEqualTo(1);
    }

    @Test
    void lojaRemovidaImpedeAprovacao() throws Exception {
        int id = criar(pedido("COBERTURA_LOJA"), gerente);
        jdbc.update("delete from loja where cod_loja = 'RH111'");
        decidir(id, supervisor, "APROVAR", null).andExpect(status().isConflict());
        assertThat(eventos()).isZero();
        assertThat(jdbc.queryForObject("select status from solicitacao_alteracao where id = ?", String.class, id))
                .isEqualTo("PENDENTE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"APROVAR", "REPROVAR"})
    void supervisorNaoDecideProprioPedido(String decisao) throws Exception {
        int id = criar(pedido("FERIAS"), supervisor);
        decidir(id, supervisor, decisao, "Motivo").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Outra pessoa precisa aprovar esta correção"));
        assertThat(eventos()).isZero();
        decidir(id, outraSupervisora, "APROVAR", null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void reprovacaoSemJustificativaRecusada(String justificativa) throws Exception {
        int id = criar(pedido("FERIAS"), gerente);
        decidir(id, supervisor, "REPROVAR", justificativa).andExpect(status().isBadRequest());
        decidir(id, supervisor, "REPROVAR", null).andExpect(status().isBadRequest());
        assertThat(eventos()).isZero();
    }

    @Test
    void reprovacaoNaoCriaEventoEPermiteNovoPedido() throws Exception {
        int id = criar(pedido("FERIAS"), gerente);
        decidir(id, supervisor, "REPROVAR", " Datas incorretas ").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REPROVADA"))
                .andExpect(jsonPath("$.decisao.justificativa").value("Datas incorretas"));
        assertThat(eventos()).isZero();
        decidir(id, outraSupervisora, "APROVAR", null).andExpect(status().isConflict());
        criar(pedido("FERIAS"), gerente);
        assertThat(pedidos()).isEqualTo(2);
    }

    @Test
    void decisaoRepetidaNaoDuplicaEvento() throws Exception {
        int id = criar(pedido("FERIAS"), gerente);
        decidir(id, supervisor, "APROVAR", null).andExpect(status().isOk());
        decidir(id, outraSupervisora, "APROVAR", null).andExpect(status().isConflict());
        assertThat(eventos()).isEqualTo(1);
    }

    @Test
    void permissoesDeCriacaoFilaEDecisao() throws Exception {
        mvc.perform(post("/api/correcoes").contentType("application/json")
                .content(mapper.writeValueAsString(pedido("FERIAS")))).andExpect(status().isUnauthorized());
        enviar(post("/api/correcoes"), admin, pedido("FERIAS")).andExpect(status().isForbidden());
        int id = criar(pedido("FERIAS"), supervisor);
        decidir(id, gerente, "APROVAR", null).andExpect(status().isForbidden());
        decidir(id, admin, "APROVAR", null).andExpect(status().isForbidden());
        enviar(get("/api/correcoes/fila"), gerente, null).andExpect(status().isForbidden());
        enviar(get("/api/correcoes/minhas"), admin, null).andExpect(status().isForbidden());
        enviar(get("/api/correcoes/" + id), gerente, null).andExpect(status().isForbidden());
    }

    @Test
    void minhasSoDoAutorEFilaSoPendentesDeOutros() throws Exception {
        int gerenteId = criar(pedido("FERIAS"), gerente);
        int supervisorId = criar(pedido("AFASTAMENTO"), supervisor);
        enviar(get("/api/correcoes/minhas"), gerente, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(gerenteId));
        enviar(get("/api/correcoes/minhas"), supervisor, null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(supervisorId));
        enviar(get("/api/correcoes/fila"), supervisor, null)
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(gerenteId));
        decidir(gerenteId, supervisor, "REPROVAR", "Datas incorretas").andExpect(status().isOk());
        enviar(get("/api/correcoes/fila"), supervisor, null).andExpect(jsonPath("$", hasSize(0)));
        enviar(get("/api/correcoes/minhas"), gerente, null)
                .andExpect(jsonPath("$[0].status").value("REPROVADA"));
    }

    @Test
    void origemLanaNaoPodeSerForjadaPelaApi() throws Exception {
        enviar(post("/api/correcoes"), gerente, pedido("FERIAS").put("origem", "LANA").put("chat_id", "chat"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criacoesSimultaneasNaoDuplicamPendente() throws Exception {
        List<Integer> status = simultaneos(
                () -> enviar(post("/api/correcoes"), gerente, pedido("FERIAS")).andReturn().getResponse().getStatus(),
                () -> enviar(post("/api/correcoes"), supervisor, pedido("FERIAS")).andReturn().getResponse().getStatus());
        assertThat(status).containsExactlyInAnyOrder(201, 409);
        assertThat(pedidos()).isEqualTo(1);
    }

    @Test
    void decisoesSimultaneasNaoDuplicamEvento() throws Exception {
        int id = criar(pedido("FERIAS"), gerente);
        List<Integer> status = simultaneos(
                () -> decidir(id, supervisor, "APROVAR", null).andReturn().getResponse().getStatus(),
                () -> decidir(id, outraSupervisora, "APROVAR", null).andReturn().getResponse().getStatus());
        assertThat(status).containsExactlyInAnyOrder(200, 409);
        assertThat(eventos()).isEqualTo(1);
    }

    @Test
    void fluxoInternoDaLanaMantemAutorOrigemEEstadoPendente() {
        var request = new SolicitacaoRequest("chat-rh", TipoAlteracao.FERIAS, "RH-111", "2026-11",
                null, dados(pedido("FERIAS")), "Confirmado no chat", "autor-forjado", OrigemSolicitacao.LANA);
        var resposta = service.criarSolicitacaoDaLana(request, gerente);
        assertThat(resposta.autor().id()).isEqualTo(gerente.getId());
        assertThat(resposta.origem()).isEqualTo(OrigemSolicitacao.LANA);
        assertThat(resposta.chatId()).isEqualTo("chat-rh");
        assertThat(resposta.status().name()).isEqualTo("PENDENTE");
        assertThat(eventos()).isZero();
    }

    @Test
    void falhaAoPersistirDecisaoDesfazInsercaoDoEvento() throws Exception {
        int id = criar(pedido("FERIAS"), gerente);
        // Falha depois do INSERT em evento_rh: comprova que JDBC e JPA compartilham a transação.
        jdbc.execute("""
                create function impedir_decisao_teste() returns trigger language plpgsql as $$
                begin
                    if NEW.status = 'APROVADA' then raise exception 'falha simulada na decisão'; end if;
                    return NEW;
                end $$
                """);
        jdbc.execute("""
                create trigger impedir_decisao_teste before update on solicitacao_alteracao
                for each row execute function impedir_decisao_teste()
                """);
        try {
            assertThatThrownBy(() -> service.decidirSolicitacao(id, new DecisaoRequest("APROVAR", "Conferido"), supervisor))
                    .isInstanceOf(RuntimeException.class);
            assertThat(eventos()).isZero();
            assertThat(jdbc.queryForObject("select status from solicitacao_alteracao where id = ?", String.class, id))
                    .isEqualTo("PENDENTE");
        } finally {
            jdbc.execute("drop trigger impedir_decisao_teste on solicitacao_alteracao");
            jdbc.execute("drop function impedir_decisao_teste()");
        }
    }

    private List<Integer> simultaneos(Callable<Integer> a, Callable<Integer> b) throws Exception {
        CountDownLatch prontos = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var primeiro = executor.submit(() -> { prontos.countDown(); iniciar.await(10, TimeUnit.SECONDS); return a.call(); });
            var segundo = executor.submit(() -> { prontos.countDown(); iniciar.await(10, TimeUnit.SECONDS); return b.call(); });
            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            return List.of(primeiro.get(20, TimeUnit.SECONDS), segundo.get(20, TimeUnit.SECONDS));
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
        }
    }

    private ObjectNode pedido(String tipo) {
        ObjectNode pedido = mapper.createObjectNode().put("tipo", tipo).put("matricula", "RH-111")
                .put("competencia", "2026-11").put("motivo", "Solicitado pelo RH").put("origem", "USUARIO");
        pedido.putNull("antes");
        ObjectNode depois = pedido.putObject("depois").put("data_inicio", "2026-11-13").put("data_fim", "2026-11-20");
        if (tipo.equals("COBERTURA_LOJA")) depois.put("cod_loja", "RH111");
        return pedido;
    }

    private ObjectNode dados(ObjectNode pedido) { return (ObjectNode) pedido.get("depois"); }

    private int criar(ObjectNode pedido, Usuario autor) throws Exception {
        String json = enviar(post("/api/correcoes"), autor, pedido).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asInt();
    }

    private ResultActions decidir(int id, Usuario usuario, String decisao, String justificativa) throws Exception {
        return enviar(post("/api/correcoes/" + id + "/decidir"), usuario,
                mapper.createObjectNode().put("decisao", decisao).put("justificativa", justificativa));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder request, Usuario usuario, ObjectNode corpo) throws Exception {
        request.header("Authorization", "Bearer " + jwt.gerarToken(usuario, jwt.getExpirationDate()));
        if (corpo != null) request.contentType("application/json").content(mapper.writeValueAsString(corpo));
        return mvc.perform(request);
    }

    private int eventos() { return jdbc.queryForObject("select count(*) from evento_rh where matricula = 'RH-111'", Integer.class); }
    private int pedidos() { return jdbc.queryForObject("select count(*) from solicitacao_alteracao where matricula = 'RH-111'", Integer.class); }

    private void importarEvento() {
        jdbc.update("""
                insert into evento_rh (matricula, tipo, data_inicio, data_fim, origem)
                values ('RH-111', 'ferias', '2026-11-13', '2026-11-20', 'especificacao-importada')
                """);
    }
}
