package com.facimus.procesos.common.almacen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PR 61: un enlace firmado vale para lo que se firmo, hasta cuando se firmo, y con la clave de quien lo firmo. */
class EnlacesFirmadosTest {

    private static final Instant AHORA = Instant.parse("2026-11-12T09:30:00.750Z");
    private static final String SECRETO = "un-secreto-de-al-menos-treinta-y-dos-caracteres";

    private final EnlacesFirmados enlaces = new EnlacesFirmados(SECRETO, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("el vencimiento es ahora mas la vida, en segundos enteros")
    void vencimiento() {
        assertThat(enlaces.vencimiento(Duration.ofMinutes(5))).isEqualTo(Instant.parse("2026-11-12T09:35:00Z"));
    }

    @Test
    @DisplayName("la firma de un recurso y un vencimiento vale; cambiar cualquiera de los dos la rompe")
    void firma() {
        Instant vence = AHORA.plusSeconds(60);
        String firma = enlaces.firmar("documento:1:12:2", vence);

        assertThat(enlaces.vale("documento:1:12:2", vence, firma)).isTrue();
        assertThat(enlaces.vale("documento:1:12:3", vence, firma)).isFalse();
        assertThat(enlaces.vale("documento:2:12:2", vence, firma)).isFalse();
        assertThat(enlaces.vale("documento:1:12:2", vence.plusSeconds(1), firma)).isFalse();
        assertThat(enlaces.vale("documento:1:12:2", vence, firma + "x")).isFalse();
        assertThat(enlaces.vale("documento:1:12:2", vence, null)).isFalse();
        assertThat(enlaces.vale("documento:1:12:2", null, firma)).isFalse();
        assertThat(firma).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("un enlace vencido no vale aunque su firma sea correcta; justo en su vencimiento todavia si")
    void vencido() {
        Instant antes = AHORA.minusSeconds(1);
        Instant justo = AHORA;

        assertThat(enlaces.vale("r", antes, enlaces.firmar("r", antes))).isFalse();
        assertThat(enlaces.vale("r", justo, enlaces.firmar("r", justo))).isTrue();
    }

    @Test
    @DisplayName("otra instancia con el mismo secreto acepta la firma; con otro secreto, o sin secreto, no")
    void clave() {
        Instant vence = AHORA.plusSeconds(60);
        String firma = enlaces.firmar("r", vence);
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);

        assertThat(new EnlacesFirmados(SECRETO, reloj).vale("r", vence, firma)).isTrue();
        assertThat(new EnlacesFirmados(SECRETO + "!", reloj).vale("r", vence, firma)).isFalse();
        assertThat(new EnlacesFirmados("", reloj).vale("r", vence, firma)).isFalse();
        assertThat(new EnlacesFirmados(null, reloj).vale("r", vence, firma)).isFalse();
    }
}
