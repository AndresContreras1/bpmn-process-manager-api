package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.gestion.service.ExportacionService;

/** PR 66: la limpieza de cada hora cruza tiendas: sin el alcance del sistema, la base no le muestra ninguna fila. */
class LimpiezaDeExportacionesConfigTest {

    @Test
    @DisplayName("limpia como el sistema, y despues deja el contexto como estaba")
    void comoSistema() {
        ExportacionService exportaciones = mock(ExportacionService.class);
        List<Optional<Alcance>> alcances = new ArrayList<>();
        when(exportaciones.limpiar()).thenAnswer(llamada -> {
            alcances.add(ContextoDeTienda.actual());
            return 2;
        });

        new LimpiezaDeExportacionesConfig(exportaciones).limpiar();

        verify(exportaciones).limpiar();
        assertThat(alcances).containsExactly(Optional.of(Alcance.SISTEMA));
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("sin nada que limpiar tambien corre como el sistema")
    void sinNada() {
        ExportacionService exportaciones = mock(ExportacionService.class);
        List<Optional<Alcance>> alcances = new ArrayList<>();
        when(exportaciones.limpiar()).thenAnswer(llamada -> {
            alcances.add(ContextoDeTienda.actual());
            return 0;
        });

        new LimpiezaDeExportacionesConfig(exportaciones).limpiar();

        assertThat(alcances).containsExactly(Optional.of(Alcance.SISTEMA));
    }
}
