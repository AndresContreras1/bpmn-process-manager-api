package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

/** Que ve cada quien: la tienda duena su modelo vivo, con el aviso de cambios sin publicar; la invitada, lo publicado. */
@ExtendWith(MockitoExtension.class)
class DiagramaServiceTest {

    private static final Long LECTORA = 1L;
    private static final Long DUENA = 9L;
    private static final Long PROCESO = 1L;

    @Mock
    private ProcesoService procesoService;
    @Mock
    private VersionService versionService;
    @Mock
    private ArmadoDelDiagrama armado;
    @Spy
    private JsonMapper json = JsonMapper.builder().build();

    @InjectMocks
    private DiagramaServiceImpl diagramaService;

    @Test
    @DisplayName("Un proceso que nunca se publico no tiene borrador pendiente: no hay con que compararlo")
    void obtener_sinVersionPublicada_noHayBorradorPendiente() {
        DiagramaResponse vivo = DiagramaArmado.demo().diagrama();
        when(procesoService.obtenerParaLectura(LECTORA, PROCESO)).thenReturn(lectura(null, null));
        when(armado.armar(any(), anyBoolean(), anyLong())).thenReturn(vivo);

        DiagramaResponse diagrama = diagramaService.obtener(LECTORA, PROCESO);

        assertThat(diagrama.compartido()).isFalse();
        assertThat(diagrama.proceso().borradorPendiente()).isFalse();
        assertThat(diagrama.pools()).isNotEmpty();
    }

    @Test
    @DisplayName("El modelo vivo con otra huella que la version vigente es un borrador pendiente")
    void obtener_modeloDistintoDeLaVersion_avisaDelBorradorPendiente() {
        DiagramaResponse vivo = DiagramaArmado.demo().diagrama();
        when(procesoService.obtenerParaLectura(LECTORA, PROCESO)).thenReturn(lectura(1, "huella-de-la-version-1"));
        when(armado.armar(any(), anyBoolean(), anyLong())).thenReturn(vivo);

        assertThat(diagramaService.obtener(LECTORA, PROCESO).proceso().borradorPendiente()).isTrue();
    }

    @Test
    @DisplayName("El modelo vivo con la misma huella que la version vigente no tiene nada sin publicar")
    void obtener_modeloIgualALaVersion_noAvisaDeNada() {
        DiagramaResponse vivo = DiagramaArmado.demo().diagrama();
        String huella = Huella.de(DiagramaCanonico.de(vivo, json));
        when(procesoService.obtenerParaLectura(LECTORA, PROCESO)).thenReturn(lectura(1, huella));
        when(armado.armar(any(), anyBoolean(), anyLong())).thenReturn(vivo);

        assertThat(diagramaService.obtener(LECTORA, PROCESO).proceso().borradorPendiente()).isFalse();
    }

    @Test
    @DisplayName("HU-23: la invitada ve la version vigente de la otra tienda, no su borrador")
    void obtener_procesoCompartido_devuelveLaVersionVigente() {
        DiagramaResponse publicado = DiagramaArmado.demo().diagrama();
        // El JSON se arma antes de programar el mock: el mapper es un spy y llamarlo dentro de when lo confunde.
        String definicion = json.writeValueAsString(publicado);
        when(procesoService.obtenerParaLectura(LECTORA, PROCESO)).thenReturn(compartido());
        when(versionService.definicionVigente(DUENA, PROCESO)).thenReturn(Optional.of(definicion));

        DiagramaResponse diagrama = diagramaService.obtener(LECTORA, PROCESO);

        assertThat(diagrama.compartido()).isTrue();
        assertThat(diagrama.pools()).extracting(PoolResponse::nombre)
                .containsExactlyElementsOf(publicado.pools().stream().map(PoolResponse::nombre).toList());
        assertThat(diagrama.proceso().nombre()).isEqualTo("Order fulfillment");
        // El modelo vivo de la otra tienda no se toca: la invitada no lo puede ver.
        verify(armado, never()).armar(any(), anyBoolean(), anyLong());
    }

    @Test
    @DisplayName("Un proceso compartido sin ninguna version vigente no existe para la invitada")
    void obtener_procesoCompartidoSinVersion_noEncontrado() {
        when(procesoService.obtenerParaLectura(LECTORA, PROCESO)).thenReturn(compartido());
        when(versionService.definicionVigente(DUENA, PROCESO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> diagramaService.obtener(LECTORA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
    }

    private static ProcesoLectura lectura(Integer versionPublicada, String huellaPublicada) {
        return new ProcesoLectura(proceso(versionPublicada), LECTORA, false, huellaPublicada);
    }

    private static ProcesoLectura compartido() {
        return new ProcesoLectura(proceso(1), DUENA, true, "huella-de-la-version-1");
    }

    private static ProcesoResponse proceso(Integer versionPublicada) {
        LocalDateTime ahora = LocalDateTime.now();
        return new ProcesoResponse(PROCESO, "Order fulfillment", "De la compra a la entrega", "Fulfillment",
                versionPublicada == null ? EstadoProceso.BORRADOR : EstadoProceso.PUBLICADO, true, ahora, ahora, 0L,
                null, null, versionPublicada, null);
    }
}
