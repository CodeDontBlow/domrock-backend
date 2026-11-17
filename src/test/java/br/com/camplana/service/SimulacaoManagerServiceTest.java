package br.com.camplana.service;

import br.com.camplana.dto.simulacao.SimulacaoStatusResponse;
import br.com.camplana.entity.EtapaSimulacao;
import br.com.camplana.entity.Perfil;
import br.com.camplana.entity.Simulacao;
import br.com.camplana.entity.StatusSimulacao;
import br.com.camplana.entity.Usuario;
import br.com.camplana.repository.CampanhaRepository;
import br.com.camplana.repository.CampanhaVersaoRepository;
import br.com.camplana.repository.SimulacaoRepository;
import br.com.camplana.security.FiltroConteudoTecnico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SimulacaoManagerServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private SimulacaoPersistenceService persistenceService;

    @Mock
    private SimulacaoJobService jobService;

    @Mock
    private SimulacaoRepository simulacaoRepository;

    @Mock
    private CampanhaRepository campanhaRepository;

    @Mock
    private CampanhaVersaoRepository campanhaVersaoRepository;

    private SimulacaoManagerService managerService;

    @BeforeEach
    void setUp() {
        managerService = new SimulacaoManagerService(
                persistenceService,
                jobService,
                simulacaoRepository,
                campanhaRepository,
                campanhaVersaoRepository,
                JSON,
                new FiltroConteudoTecnico(JSON)
        );
    }

    @Test
    void buscarPorIdOcultaCodigoESqlDoResultadoParaGerenteEMantemParaSupervisor() {
        Usuario gerente = new Usuario();
        gerente.setId(1);
        gerente.setNome("Gabriela");
        gerente.setPerfil(Perfil.GERENTE);

        Usuario supervisor = new Usuario();
        supervisor.setId(2);
        supervisor.setNome("Sérgio");
        supervisor.setPerfil(Perfil.SUPERVISOR);

        String resultadoOriginal = """
                {
                  "totais": {"baseline": 480263.24, "simulado": 503999.34, "diferenca": 23736.10},
                  "codigo": "resultado = calcular(vendas)",
                  "codigo_gerado": "resultado = calcular(vendas)",
                  "trilha": {
                    "modelo": "gemini-3.5-flash-lite",
                    "passos": [
                      {
                        "ordem": 1,
                        "ferramenta": "carregar_bases",
                        "tabelas": ["venda"],
                        "sql": "SELECT * FROM venda",
                        "sql_executado": "SELECT * FROM venda WHERE loja_id = 1"
                      }
                    ]
                  }
                }
                """;

        Simulacao simulacao = new Simulacao();
        simulacao.setId(345L);
        simulacao.setUsuario(gerente);
        simulacao.setChatId("chat-1");
        simulacao.setStatus(StatusSimulacao.CONCLUIDA);
        simulacao.setEtapa(EtapaSimulacao.CONCLUIDA);
        simulacao.setFicha("{\"rule_id\":\"r1\"}");
        simulacao.setResultado(resultadoOriginal);
        simulacao.setCodigoGerado("resultado = calcular(vendas)");
        simulacao.setCriadaEm(Instant.parse("2026-10-21T12:58:00Z"));
        simulacao.setConcluidaEm(Instant.parse("2026-10-21T12:59:00Z"));

        given(simulacaoRepository.findById(345L)).willReturn(Optional.of(simulacao));

        // Gerente (autora): não recebe codigo, codigo_gerado, sql nem sql_executado
        SimulacaoStatusResponse respGerente = managerService.buscarPorId(345L, gerente);
        JsonNode jsonGerente = JSON.readTree(respGerente.resultado());
        assertThat(jsonGerente.path("totais").path("diferenca").asDouble()).isEqualTo(23736.10);
        assertThat(jsonGerente.has("codigo")).isFalse();
        assertThat(jsonGerente.has("codigo_gerado")).isFalse();
        assertThat(jsonGerente.path("trilha").path("passos").get(0).has("sql")).isFalse();
        assertThat(jsonGerente.path("trilha").path("passos").get(0).has("sql_executado")).isFalse();
        assertThat(jsonGerente.path("trilha").path("passos").get(0).path("ferramenta").asString())
                .isEqualTo("carregar_bases");

        // Entidade original permanece intacta
        assertThat(simulacao.getResultado()).isEqualTo(resultadoOriginal);

        // Supervisor (quando autor ou com campanha submetida): recebe tudo
        simulacao.setUsuario(supervisor);
        SimulacaoStatusResponse respSupervisor = managerService.buscarPorId(345L, supervisor);
        JsonNode jsonSupervisor = JSON.readTree(respSupervisor.resultado());
        assertThat(jsonSupervisor.path("codigo").asString()).isEqualTo("resultado = calcular(vendas)");
        assertThat(jsonSupervisor.path("codigo_gerado").asString()).isEqualTo("resultado = calcular(vendas)");
        assertThat(jsonSupervisor.path("trilha").path("passos").get(0).path("sql").asString())
                .isEqualTo("SELECT * FROM venda");
        assertThat(jsonSupervisor.path("trilha").path("passos").get(0).path("sql_executado").asString())
                .isEqualTo("SELECT * FROM venda WHERE loja_id = 1");
    }

    @Test
    void buscarPorIdOcultaCodigoNoErroParaGerenteEMantemParaSupervisor() {
        Usuario gerente = new Usuario();
        gerente.setId(1);
        gerente.setNome("Gabriela");
        gerente.setPerfil(Perfil.GERENTE);

        Usuario supervisor = new Usuario();
        supervisor.setId(2);
        supervisor.setNome("Sérgio");
        supervisor.setPerfil(Perfil.SUPERVISOR);

        String erroOriginal = """
                {
                  "etapa": "execucao_codigo",
                  "mensagem": "KeyError ao calcular bonificação",
                  "codigo": "resultado = df['coluna_inexistente'].sum()",
                  "sql_executado": "SELECT * FROM venda"
                }
                """;

        Simulacao simulacao = new Simulacao();
        simulacao.setId(346L);
        simulacao.setUsuario(gerente);
        simulacao.setChatId("chat-erro-1");
        simulacao.setStatus(StatusSimulacao.ERRO);
        simulacao.setEtapa(EtapaSimulacao.ERRO);
        simulacao.setFicha("{\"rule_id\":\"r1\"}");
        simulacao.setErro(erroOriginal);
        simulacao.setCriadaEm(Instant.parse("2026-10-21T12:58:00Z"));
        simulacao.setConcluidaEm(Instant.parse("2026-10-21T12:59:00Z"));

        given(simulacaoRepository.findById(346L)).willReturn(Optional.of(simulacao));

        SimulacaoStatusResponse respGerente = managerService.buscarPorId(346L, gerente);
        JsonNode erroGerente = JSON.readTree(respGerente.erro());
        assertThat(erroGerente.path("etapa").asString()).isEqualTo("execucao_codigo");
        assertThat(erroGerente.path("mensagem").asString()).isEqualTo("KeyError ao calcular bonificação");
        assertThat(erroGerente.has("codigo")).isFalse();
        assertThat(erroGerente.has("sql_executado")).isFalse();
        assertThat(simulacao.getErro()).isEqualTo(erroOriginal);

        simulacao.setUsuario(supervisor);
        SimulacaoStatusResponse respSupervisor = managerService.buscarPorId(346L, supervisor);
        JsonNode erroSupervisor = JSON.readTree(respSupervisor.erro());
        assertThat(erroSupervisor.path("codigo").asString()).isEqualTo("resultado = df['coluna_inexistente'].sum()");
        assertThat(erroSupervisor.path("sql_executado").asString()).isEqualTo("SELECT * FROM venda");
    }
}
