package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.event.SesionesCerradas;
import com.facimus.procesos.gestion.service.SesionService;

class RevokedSessionsTest {

    private static final long VIGENCIA_TOKEN = 900;

    private final SesionService sesionService = mock(SesionService.class);
    private final RelojDePrueba reloj = new RelojDePrueba();
    private final RevokedSessions revocadas = new RevokedSessions(sesionService, VIGENCIA_TOKEN, reloj);

    @Test
    @DisplayName("Una sesion cerrada queda revocada al instante; las demas no")
    void alCerrarse_revocaSoloEsasSesiones() {
        revocadas.alCerrarse(new SesionesCerradas(List.of("cerrada-1", "cerrada-2")));

        assertThat(revocadas.estaRevocada("cerrada-1")).isTrue();
        assertThat(revocadas.estaRevocada("cerrada-2")).isTrue();
        assertThat(revocadas.estaRevocada("abierta")).isFalse();
    }

    @Test
    @DisplayName("Cuando ya vencio el ultimo token que pudo emitir, la sesion se olvida")
    void sesionRevocada_seOlvidaCuandoVencenSusTokens() {
        revocadas.alCerrarse(new SesionesCerradas(List.of("cerrada")));

        reloj.avanzar(Duration.ofSeconds(VIGENCIA_TOKEN - 1));
        assertThat(revocadas.estaRevocada("cerrada")).isTrue();

        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(revocadas.estaRevocada("cerrada")).isFalse();
    }

    @Test
    @DisplayName("Tras un reinicio recupera de la base las sesiones cerradas cuyos tokens todavia no vencen")
    void recuperarCerradas_traeLasDeLaUltimaVigenciaDeUnToken() {
        given(sesionService.cerradasEnLosUltimos(Duration.ofSeconds(VIGENCIA_TOKEN))).willReturn(List.of("antes"));

        revocadas.recuperarCerradas();

        assertThat(revocadas.estaRevocada("antes")).isTrue();
    }
}
