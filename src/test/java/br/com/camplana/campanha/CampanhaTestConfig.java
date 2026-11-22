package br.com.camplana.campanha;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Troca o registro de simulações pelo fake e fixa "agora" em 21/10/2026 13:05 UTC; um teste pode mudar o
 * dia pelo {@link RelogioDeTeste} (e devolver ao padrão no fim).
 */
@TestConfiguration(proxyBeanMethods = false)
public class CampanhaTestConfig {

    public static final Instant AGORA = Instant.parse("2026-10-21T13:05:00Z");

    @Bean
    @Primary
    FakeRegistroSimulacoes fakeRegistroSimulacoes(JdbcTemplate jdbc) {
        return new FakeRegistroSimulacoes(jdbc);
    }

    @Bean
    @Primary
    RelogioDeTeste relogioFixo() {
        return new RelogioDeTeste(AGORA, ZoneId.of("America/Sao_Paulo"));
    }
}
