package br.com.camplana.campanha;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Relógio dos testes de campanha: parado em {@link CampanhaTestConfig#AGORA} até um teste mudar o dia.
 *
 * <pre>relogio.fixarEm(Instant.parse("2025-11-25T12:00:00Z")); // "hoje" = 25/11/2025 em Brasília</pre>
 */
public class RelogioDeTeste extends Clock {

    private final ZoneId fuso;
    private volatile Instant agora;

    public RelogioDeTeste(Instant agora, ZoneId fuso) {
        this.agora = agora;
        this.fuso = fuso;
    }

    public void fixarEm(Instant instante) {
        this.agora = instante;
    }

    public void voltarAoPadrao() {
        this.agora = CampanhaTestConfig.AGORA;
    }

    @Override
    public ZoneId getZone() {
        return fuso;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(agora, zona);
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
