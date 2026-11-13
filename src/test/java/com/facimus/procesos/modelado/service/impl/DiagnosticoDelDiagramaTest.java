package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ActividadRepository.Llamada;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 64: el diagnostico que decide si se publica, sobre el borrador de hoy y sobre un diagrama congelado. Lo congelado
 * se revisa como estaba; los procesos a los que llama, como estan hoy.
 */
class DiagnosticoDelDiagramaTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 3L;
    private static final Long FACTURAR = 7L;
    private static final Long COBRAR = 8L;

    private final JsonMapper json = JsonMapper.builder().build();
    private final ArmadoDelDiagrama armado = mock(ArmadoDelDiagrama.class);
    private final ActividadRepository actividadRepository = mock(ActividadRepository.class);
    private final ProcesoRepository procesoRepository = mock(ProcesoRepository.class);
    private DiagnosticoDelDiagrama diagnostico;
    private boolean facturarActivo;

    @BeforeEach
    void preparar() {
        diagnostico = new DiagnosticoDelDiagrama(armado, ObservationRegistry.NOOP, actividadRepository,
                procesoRepository, json);
        facturarActivo = true;
        when(procesoRepository.findAllByIdInAndEmpresaId(anyCollection(), eq(EMPRESA))).thenAnswer(llamada -> {
            Collection<Long> ids = llamada.getArgument(0);
            return ids.contains(FACTURAR) ? List.of(Proceso.builder().id(FACTURAR).nombre("Facturar")
                    .activo(facturarActivo).build()) : List.of();
        });
    }

    /** El proceso de ejemplo con una actividad de llamada entre recibir el pedido y pedir el pago. */
    private static DiagramaResponse llamandoA(Long llamado) {
        DiagramaArmado diagrama = DiagramaArmado.demo();
        diagrama.quitarSalidasDe("Receive order");
        diagrama.actividadEn(DiagramaArmado.VENTAS, "Bill customer", TipoActividad.LLAMADA, null, llamado);
        diagrama.arco("Receive order", "Bill customer");
        diagrama.arco("Bill customer", "Request payment authorization");
        return diagrama.diagrama();
    }

    private List<String> congelado(DiagramaResponse diagrama) {
        return diagnostico.errores(EMPRESA, PROCESO, json.writeValueAsString(diagrama));
    }

    @Test
    @DisplayName("un diagrama congelado limpio y sin llamadas no tiene errores, y no se busca ningun proceso")
    void limpio() {
        assertThat(congelado(DiagramaArmado.demo().diagrama())).isEmpty();
        verify(procesoRepository, never()).findAllByIdInAndEmpresaId(anyCollection(), anyLong());
    }

    @Test
    @DisplayName("lo congelado se revisa entero: un error del diagrama sale con su codigo y su elemento")
    void errorDelDiagrama() {
        DiagramaArmado roto = DiagramaArmado.demo();
        roto.quitarSalidasDe("Receive order");

        assertThat(congelado(roto.diagrama())).contains("E-05 Actividad \"Receive order\": No sale ningun flujo "
                + "de este nodo y no es un evento de fin: el caso se quedaria aqui.");
    }

    @Test
    @DisplayName("una llamada congelada a un proceso que hoy esta dado de baja, o que ya no esta, es un error")
    void llamadoDeBaja() {
        assertThat(congelado(llamandoA(FACTURAR))).isEmpty();

        facturarActivo = false;
        assertThat(congelado(llamandoA(FACTURAR))).containsExactly(
                "E-20 Actividad \"Bill customer\": llama a \"Facturar\", que esta dado de baja.");
        assertThat(congelado(llamandoA(99L))).containsExactly(
                "E-20 Actividad \"Bill customer\": llama a \"\", que esta dado de baja.");
    }

    @Test
    @DisplayName("las llamadas de hoy de los demas procesos cuentan para el circulo; las de hoy de este, no")
    void circulo() {
        // Hoy Facturar llama a este proceso: lo congelado, que llama a Facturar, cerraria el circulo
        when(actividadRepository.llamadasDeLaTienda(EMPRESA)).thenReturn(List.of(
                new Llamada(FACTURAR, PROCESO, "Despacho", true, "Volver")));
        assertThat(congelado(llamandoA(FACTURAR))).containsExactly(
                "E-21 Las actividades de llamada vuelven a este proceso: las llamadas no pueden ser circulares.");

        // El borrador de hoy de este proceso llama a Cobrar, que vuelve: lo congelado no llama a nadie
        when(actividadRepository.llamadasDeLaTienda(EMPRESA)).thenReturn(List.of(
                new Llamada(PROCESO, COBRAR, "Cobrar", false, "Cobrar"), new Llamada(COBRAR, PROCESO, "Despacho",
                        true, "Volver")));
        assertThat(congelado(DiagramaArmado.demo().diagrama())).isEmpty();
    }

    @Test
    @DisplayName("el borrador de hoy usa sus llamadas de hoy: una a un proceso de baja y un circulo")
    void borradorDeHoy() {
        DiagramaResponse demo = DiagramaArmado.demo().diagrama();
        ProcesoResponse proceso = demo.proceso();
        Long id = proceso.id();
        when(armado.armar(proceso, false, EMPRESA)).thenReturn(demo);
        when(actividadRepository.llamadasDeLaTienda(EMPRESA)).thenReturn(List.of(
                new Llamada(id, COBRAR, "Cobrar", false, "Cobrar"), new Llamada(COBRAR, id, "Despacho", true,
                        "Volver"), new Llamada(FACTURAR, COBRAR, "Cobrar", false, "Otra")));

        assertThat(diagnostico.errores(EMPRESA, proceso)).containsExactly(
                "E-20 Actividad \"Cobrar\": llama a \"Cobrar\", que esta dado de baja.",
                "E-21 Las actividades de llamada vuelven a este proceso: las llamadas no pueden ser circulares.");
    }
}
