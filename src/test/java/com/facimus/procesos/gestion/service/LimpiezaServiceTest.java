package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.gestion.repository.ClaveIdempotenciaRepository;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.service.impl.LimpiezaServiceImpl;

/**
 * Lo unico que decide la limpieza es hasta que fecha barre cada tabla y en que orden. Las dos cosas se miran aqui;
 * que el borrado se lleve las filas correctas lo mira {@code LimpiezaIntegracionTest} contra la base.
 */
@ExtendWith(MockitoExtension.class)
class LimpiezaServiceTest {

    /** Los 15 minutos de un access token, que son el suelo de la retencion de sesiones. */
    private static final long VIGENCIA_DEL_TOKEN = 900;

    @Mock
    private SesionRepository sesionRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ClaveIdempotenciaRepository claveIdempotenciaRepository;

    private final ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);

    @Test
    @DisplayName("Cada tabla se barre hasta su propia fecha limite")
    void limpiar_cadaTablaHastaSuFecha() {
        LocalDateTime antes = LocalDateTime.now();

        limpieza(Duration.ofDays(7), Duration.ofHours(24)).limpiar();

        LocalDateTime despues = LocalDateTime.now();
        verify(refreshTokenRepository).borrarVencidosAntesDe(limite.capture());
        assertThat(limite.getValue()).isBetween(antes.minusDays(7), despues.minusDays(7));
        verify(sesionRepository).borrarSinTokensAntesDe(limite.capture());
        assertThat(limite.getValue()).isBetween(antes.minusDays(7), despues.minusDays(7));
        verify(claveIdempotenciaRepository).borrarAntesDe(limite.capture());
        assertThat(limite.getValue()).isBetween(antes.minusHours(24), despues.minusHours(24));
    }

    @Test
    @DisplayName("Una sesion cerrada no se va antes de que venza el ultimo access token que pudo emitir")
    void limpiar_laRetencionDeSesionesNoBajaDeLaVigenciaDelToken() {
        LocalDateTime antes = LocalDateTime.now();

        // Un minuto de retencion es menos que los quince del access token: al arrancar, RevokedSessions ya no
        // encontraria la sesion cerrada y su token volveria a servir.
        limpieza(Duration.ofMinutes(1), Duration.ofHours(24)).limpiar();

        LocalDateTime despues = LocalDateTime.now();
        verify(sesionRepository).borrarSinTokensAntesDe(limite.capture());
        assertThat(limite.getValue())
                .isBetween(antes.minusSeconds(VIGENCIA_DEL_TOKEN), despues.minusSeconds(VIGENCIA_DEL_TOKEN));
    }

    @Test
    @DisplayName("Los refresh tokens se barren antes que las sesiones, para que una sesion libre se vaya ya")
    void limpiar_losTokensAntesQueLasSesiones() {
        limpieza(Duration.ofDays(7), Duration.ofHours(24)).limpiar();

        InOrder orden = inOrder(refreshTokenRepository, sesionRepository);
        orden.verify(refreshTokenRepository).borrarVencidosAntesDe(any());
        orden.verify(sesionRepository).borrarSinTokensAntesDe(any());
    }

    @Test
    @DisplayName("La limpieza cuenta lo que se llevo de cada tabla")
    void limpiar_cuentaLoQueSeLlevo() {
        when(refreshTokenRepository.borrarVencidosAntesDe(any())).thenReturn(4);
        when(sesionRepository.borrarSinTokensAntesDe(any())).thenReturn(3);
        when(claveIdempotenciaRepository.borrarAntesDe(any())).thenReturn(2);

        Limpieza limpieza = limpieza(Duration.ofDays(7), Duration.ofHours(24)).limpiar();

        assertThat(limpieza).isEqualTo(new Limpieza(4, 3, 2));
        assertThat(limpieza.total()).isEqualTo(9);
    }

    private LimpiezaService limpieza(Duration retencionSesiones, Duration retencionIdempotencia) {
        return new LimpiezaServiceImpl(sesionRepository, refreshTokenRepository, claveIdempotenciaRepository,
                retencionSesiones, retencionIdempotencia, VIGENCIA_DEL_TOKEN);
    }
}
