package br.com.camplana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * V13 (A6-108): a Lana conta as intercorrências pendentes lendo só tipo, status e valor_depois de
 * solicitacao_alteracao. Motivo, autor e decisão continuam fora do alcance do papel lana_leitura.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "api.security.token.secret=segredo-de-teste-com-mais-de-32-caracteres",
        "spring.flyway.placeholders.lana_db_password=lana_leitura",
        "lana.url=http://localhost:8000",
        "grpc.lana.host=localhost",
        "grpc.lana.port=50051"
})
@ActiveProfiles("test")
class LanaLeituraGrantTest {

    @Autowired
    private DataSource dataSource;

    @FunctionalInterface
    private interface ConsultaComoLana {
        void executar(Statement comando) throws SQLException;
    }

    @Test
    void lanaLeAsColunasDasIntercorrenciasPendentes() throws SQLException {
        comoLana(comando -> {
            try (ResultSet linhas = comando.executeQuery("""
                    SELECT tipo, status, valor_depois FROM solicitacao_alteracao WHERE status = 'PENDENTE'
                    """)) {
                assertThat(linhas.getMetaData().getColumnCount()).isEqualTo(3);
            }
        });
    }

    @Test
    void lanaNaoLeAsDemaisColunas() {
        assertThatThrownBy(() -> comoLana(comando -> comando.executeQuery("SELECT * FROM solicitacao_alteracao")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    /** Roda a consulta como lana_leitura na mesma conexão e devolve a conexão ao pool no papel original. */
    private void comoLana(ConsultaComoLana consulta) throws SQLException {
        try (Connection conexao = dataSource.getConnection(); Statement comando = conexao.createStatement()) {
            comando.execute("SET ROLE lana_leitura");
            try {
                consulta.executar(comando);
            } finally {
                comando.execute("RESET ROLE");
            }
        }
    }
}
