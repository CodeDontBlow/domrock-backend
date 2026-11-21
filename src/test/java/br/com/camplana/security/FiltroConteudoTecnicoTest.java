package br.com.camplana.security;

import br.com.camplana.entity.Perfil;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

import static org.assertj.core.api.Assertions.assertThat;

class FiltroConteudoTecnicoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final FiltroConteudoTecnico filtro = new FiltroConteudoTecnico(JSON);

    @Test
    void podeVerCodigoGeradoApenasParaSupervisorEAuditor() {
        assertThat(filtro.podeVerCodigoGerado(Perfil.SUPERVISOR)).isTrue();
        assertThat(filtro.podeVerCodigoGerado(Perfil.AUDITOR)).isTrue();
        assertThat(filtro.podeVerCodigoGerado(Perfil.GERENTE)).isFalse();
        assertThat(filtro.podeVerCodigoGerado(Perfil.ADMIN)).isFalse();
        assertThat(filtro.podeVerCodigoGerado(null)).isFalse();
    }

    @Test
    void filtrarJsonNodeMantemCodigoDasLinhasDeQuebraERemoveOCodigoGerado() {
        // Regressão: o filtro tirava "codigo" de por_marca/por_cargo/maiores_lojas e o Gerente via
        // o detalhamento do custo sem o nome de cada linha.
        JsonNode filtrado = filtro.filtrarJsonNode(JSON.readTree("""
                {
                  "codigo": "resultado = calcular(vendas)",
                  "origem_codigo": "llm",
                  "por_marca": [{"codigo": "1", "baseline": 10.0, "simulado": 12.0, "diferenca": 2.0,
                                 "sql": "SELECT 1"}],
                  "maiores_lojas": [{"codigo": "8", "baseline": 5.0, "simulado": 6.0, "diferenca": 1.0}]
                }
                """), Perfil.GERENTE);

        assertThat(filtrado.has("codigo")).isFalse();
        assertThat(filtrado.path("por_marca").path(0).path("codigo").asString()).isEqualTo("1");
        assertThat(filtrado.path("por_marca").path(0).has("sql")).isFalse();
        assertThat(filtrado.path("maiores_lojas").path(0).path("codigo").asString()).isEqualTo("8");
    }

    @Test
    void filtrarJsonNodeRetornaIntactoParaSupervisorOuNosNulos() {
        JsonNode original = JSON.readTree("""
                {"sql": "SELECT 1", "sql_executado": "SELECT 2", "codigo": "x = 1", "codigo_gerado": "y = 2"}
                """);

        assertThat(filtro.filtrarJsonNode(original, Perfil.SUPERVISOR)).isSameAs(original);
        assertThat(filtro.filtrarJsonNode(null, Perfil.GERENTE)).isNull();
        assertThat(filtro.filtrarJsonNode(NullNode.getInstance(), Perfil.GERENTE)).isEqualTo(NullNode.getInstance());
        assertThat(filtro.filtrarJsonNode(MissingNode.getInstance(), Perfil.GERENTE)).isEqualTo(MissingNode.getInstance());
    }

    @Test
    void filtrarJsonNodeRemoveSqlECodigoRecursivamenteEmObjetosEArraysPreservandoChavesDeNegocioEOriginal() {
        JsonNode original = JSON.readTree("""
                {
                  "modelo": "gemini-3.5-flash-lite",
                  "code": "ERR_TIMEOUT",
                  "etapa": "validacao",
                  "totais": {"baseline": 100.0, "simulado": 110.0, "diferenca": 10.0},
                  "codigo": "resultado = calcular(vendas)",
                  "codigo_gerado": "df = carregar()",
                  "passos": [
                    {
                      "ordem": 1,
                      "ferramenta": "consultar_banco",
                      "tabelas": ["venda", "funcionario"],
                      "sql": "SELECT * FROM venda",
                      "sql_executado": "SELECT * FROM venda WHERE ativo = 1",
                      "detalhes": {
                        "sql": "SELECT COUNT(*) FROM funcionario",
                        "sql_executado": "SELECT COUNT(*) FROM funcionario WHERE ativo = 1",
                        "codigo": "internal_code()",
                        "codigo_gerado": "gen_code()",
                        "code": "OK_200"
                      }
                    }
                  ],
                  "events": [
                    {"tool": "execute_sql", "sql": "SELECT sum(valor) FROM venda", "sql_executado": "SELECT sum(valor) FROM venda"}
                  ]
                }
                """);

        for (Perfil perfilRestrito : new Perfil[]{Perfil.GERENTE, Perfil.ADMIN, null}) {
            JsonNode filtrado = filtro.filtrarJsonNode(original, perfilRestrito);

            assertThat(filtrado).isNotSameAs(original);
            // Chaves técnicas removidas em todos os níveis
            assertThat(filtrado.has("codigo")).isFalse();
            assertThat(filtrado.has("codigo_gerado")).isFalse();
            assertThat(filtrado.path("passos").get(0).has("sql")).isFalse();
            assertThat(filtrado.path("passos").get(0).has("sql_executado")).isFalse();
            assertThat(filtrado.path("passos").get(0).path("detalhes").has("sql")).isFalse();
            assertThat(filtrado.path("passos").get(0).path("detalhes").has("sql_executado")).isFalse();
            assertThat(filtrado.path("passos").get(0).path("detalhes").has("codigo")).isFalse();
            assertThat(filtrado.path("passos").get(0).path("detalhes").has("codigo_gerado")).isFalse();
            assertThat(filtrado.path("events").get(0).has("sql")).isFalse();
            assertThat(filtrado.path("events").get(0).has("sql_executado")).isFalse();

            // Chaves de negócio e de erro preservadas
            assertThat(filtrado.path("modelo").asString()).isEqualTo("gemini-3.5-flash-lite");
            assertThat(filtrado.path("code").asString()).isEqualTo("ERR_TIMEOUT");
            assertThat(filtrado.path("etapa").asString()).isEqualTo("validacao");
            assertThat(filtrado.path("totais").path("diferenca").asDouble()).isEqualTo(10.0);
            assertThat(filtrado.path("passos").get(0).path("ferramenta").asString()).isEqualTo("consultar_banco");
            assertThat(filtrado.path("passos").get(0).path("tabelas").get(0).asString()).isEqualTo("venda");
            assertThat(filtrado.path("passos").get(0).path("tabelas").get(1).asString()).isEqualTo("funcionario");
            assertThat(filtrado.path("passos").get(0).path("detalhes").path("code").asString()).isEqualTo("OK_200");
            assertThat(filtrado.path("events").get(0).path("tool").asString()).isEqualTo("execute_sql");
        }

        // Imutabilidade do JsonNode original garantida
        assertThat(original.has("codigo")).isTrue();
        assertThat(original.has("codigo_gerado")).isTrue();
        assertThat(original.path("passos").get(0).path("sql").asString()).isEqualTo("SELECT * FROM venda");
        assertThat(original.path("passos").get(0).path("sql_executado").asString())
                .isEqualTo("SELECT * FROM venda WHERE ativo = 1");
    }

    @Test
    void filtrarJsonStringLimpaParaGerenteEMantemParaSupervisor() {
        String raw = "{\"totais\":{\"diferenca\":23736.10},\"codigo\":\"resultado = 1\",\"codigo_gerado\":\"x = 2\",\"trilha\":{\"sql\":\"SELECT 1\",\"sql_executado\":\"SELECT 2\",\"tabelas\":[\"venda\"]}}";

        assertThat(filtro.filtrarJsonString(raw, Perfil.SUPERVISOR)).isSameAs(raw);
        assertThat(filtro.filtrarJsonString(null, Perfil.GERENTE)).isNull();
        assertThat(filtro.filtrarJsonString("   ", Perfil.GERENTE)).isEqualTo("   ");

        String filtradoGerente = filtro.filtrarJsonString(raw, Perfil.GERENTE);
        JsonNode node = JSON.readTree(filtradoGerente);
        assertThat(node.has("codigo")).isFalse();
        assertThat(node.has("codigo_gerado")).isFalse();
        assertThat(node.path("trilha").has("sql")).isFalse();
        assertThat(node.path("trilha").has("sql_executado")).isFalse();
        assertThat(node.path("trilha").path("tabelas").get(0).asString()).isEqualTo("venda");
        assertThat(node.path("totais").path("diferenca").asDouble()).isEqualTo(23736.10);
    }
}
