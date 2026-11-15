package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Fallo;
import com.facimus.procesos.gestion.service.VerificacionDeRespaldoService.Informe;

/**
 * PR 67: la API como verificadora de una base restaurada. Solo con la propiedad encendida; corre como sistema, porque
 * cruza tiendas; y con un fallo, el arranque falla, que es lo que le da al simulacro un codigo distinto de cero.
 */
class VerificacionDeRespaldoConfigTest {

    private final VerificacionDeRespaldoService verificacion = mock(VerificacionDeRespaldoService.class);
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(VerificacionDeRespaldoConfig.class)
            .withBean(VerificacionDeRespaldoService.class, () -> verificacion);

    @Test
    @DisplayName("Sin RESPALDO_VERIFICAR la API arranca como siempre: no hay verificacion")
    void apagadaPorDefecto() {
        contexto.run(arrancado -> assertThat(arrancado).doesNotHaveBean(ApplicationRunner.class));
        contexto.withPropertyValues("respaldo.verificar=false")
                .run(arrancado -> assertThat(arrancado).doesNotHaveBean(ApplicationRunner.class));
    }

    @Test
    @DisplayName("Con la base bien, la verificacion corre como sistema y el arranque sigue")
    void baseCorrecta() throws Exception {
        List<Alcance> alcances = new ArrayList<>();
        when(verificacion.verificar()).thenAnswer(invocacion -> {
            alcances.add(ContextoDeTienda.actual().orElseThrow());
            return new Informe(2, 10, 3, List.of());
        });

        correr();

        assertThat(alcances).containsExactly(Alcance.SISTEMA);
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("Con un fallo, el arranque falla y dice cuantos")
    void baseConFallos() {
        when(verificacion.verificar()).thenReturn(new Informe(2, 10, 3,
                List.of(new Fallo(3L, "La bitácora se rompe en el registro 2: otra cosa."), new Fallo(8L, "Otra."))));

        assertThatThrownBy(this::correr).isInstanceOf(IllegalStateException.class)
                .hasMessage("La base restaurada no pasa la verificación: 2 fallos.");
    }

    private void correr() throws Exception {
        ApplicationRunner[] corredor = new ApplicationRunner[1];
        contexto.withPropertyValues("respaldo.verificar=true").run(arrancado -> {
            assertThat(arrancado).hasSingleBean(ApplicationRunner.class);
            corredor[0] = arrancado.getBean(ApplicationRunner.class);
        });
        corredor[0].run(new DefaultApplicationArguments());
    }
}
