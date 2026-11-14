package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.modelado.dto.response.AsignacionResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoAsignacion;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.DiagramaArmado;

/**
 * PR 70: a quien le llega una tarea al crearse, con cada tipo de asignacion, y por que se queda en la bandeja del rol
 * cuando la regla no da con nadie que la pueda completar.
 */
@ExtendWith(MockitoExtension.class)
class AsignacionAlCrearTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long ANA = 5L;

    @Mock
    private PersonasDeLaTarea personas;

    private AsignacionAlCrear asignacion;
    private Caso caso;

    @BeforeEach
    void unCaso() {
        asignacion = new AsignacionAlCrear(personas);
        caso = Caso.builder().id(42L).empresa(Empresa.builder().id(TIENDA_ID).build()).variables("{}").build();
    }

    @Test
    @DisplayName("Sin regla, la tarea espera en la bandeja de su rol sin nota y sin preguntar por nadie")
    void sinRegla_alRol() {
        assertThat(decidir(null, Map.of())).isEqualTo(AsignacionAlCrear.Destino.AL_ROL);
        assertThat(decidir(new AsignacionResponse(null, ANA, null), Map.of()))
                .isEqualTo(AsignacionAlCrear.Destino.AL_ROL);
        assertThat(decidir(new AsignacionResponse(TipoAsignacion.ROL, ANA, null), Map.of()))
                .isEqualTo(AsignacionAlCrear.Destino.AL_ROL);
        verifyNoInteractions(personas);
    }

    @Test
    @DisplayName("PERSONA: a la persona fija, si la puede tener")
    void persona_fija() {
        given(personas.siPuedeTenerla(TIENDA_ID, ANA)).willReturn(Optional.of(ANA));

        assertThat(decidir(new AsignacionResponse(TipoAsignacion.PERSONA, ANA, null), Map.of()))
                .isEqualTo(new AsignacionAlCrear.Destino(ANA, "asignada a una persona fija", null));
    }

    @Test
    @DisplayName("PERSONA: la que ya no puede tener tareas deja la tarea en el rol, con su nota")
    void persona_queNoPuede() {
        given(personas.siPuedeTenerla(TIENDA_ID, ANA)).willReturn(Optional.empty());

        assertThat(decidir(new AsignacionResponse(TipoAsignacion.PERSONA, ANA, null), Map.of()))
                .isEqualTo(AsignacionAlCrear.Destino.noSePudo("Iba para el usuario 5, que no puede tener tareas: "
                        + "esta desactivado, solo lee o no es de la organizacion."));
    }

    @Test
    @DisplayName("PERSONA sin persona: al rol, sin preguntar por nadie")
    void persona_sinId() {
        assertThat(decidir(new AsignacionResponse(TipoAsignacion.PERSONA, null, null), Map.of()))
                .isEqualTo(AsignacionAlCrear.Destino.noSePudo("Iba para una persona fija, y la version no dice "
                        + "cual."));
        verify(personas, never()).siPuedeTenerla(any(), any());
    }

    @Test
    @DisplayName("INICIADOR: a quien abrio el caso; sin iniciador, al rol con su nota")
    void iniciador() {
        given(personas.siPuedeTenerla(TIENDA_ID, ANA)).willReturn(Optional.of(ANA));
        AsignacionResponse alIniciador = new AsignacionResponse(TipoAsignacion.INICIADOR, null, null);

        assertThat(decidir(alIniciador, Map.of())).isEqualTo(AsignacionAlCrear.Destino.noSePudo(
                "Iba para quien abrio el caso, y este caso no lo abrio una persona."));
        caso.setIniciadoPor(ANA);
        assertThat(decidir(alIniciador, Map.of()))
                .isEqualTo(new AsignacionAlCrear.Destino(ANA, "para quien abrio el caso", null));
    }

    @Test
    @DisplayName("CAMPO: a la persona de la variable, venga como Integer o como Long, tambien anidada")
    void campo_dePersona() {
        given(personas.siPuedeTenerla(TIENDA_ID, ANA)).willReturn(Optional.of(ANA));

        assertThat(decidir(new AsignacionResponse(TipoAsignacion.CAMPO, null, "aprobador"),
                Map.of("aprobador", 5))).isEqualTo(new AsignacionAlCrear.Destino(ANA, "por la variable \"aprobador\"",
                        null));
        assertThat(decidir(new AsignacionResponse(TipoAsignacion.CAMPO, null, "tarea.revisar.aprobador"),
                Map.of("tarea", Map.of("revisar", Map.of("aprobador", 5L))))).extracting(
                        AsignacionAlCrear.Destino::usuarioId).isEqualTo(ANA);
    }

    @Test
    @DisplayName("CAMPO: sin la variable, o con una que no es una persona, al rol con su nota")
    void campo_queNoDaConNadie() {
        AsignacionResponse alCampo = new AsignacionResponse(TipoAsignacion.CAMPO, null, "aprobador");

        assertThat(decidir(alCampo, Map.of())).isEqualTo(AsignacionAlCrear.Destino.noSePudo(
                "Iba para la persona de \"aprobador\", y el caso no tiene esa variable."));
        assertThat(decidir(alCampo, Map.of("aprobador", "5"))).isEqualTo(AsignacionAlCrear.Destino.noSePudo(
                "Iba para la persona de \"aprobador\", y esa variable no es una persona."));
        assertThat(decidir(alCampo, Map.of("aprobador", 5.0))).extracting(AsignacionAlCrear.Destino::usuarioId)
                .isNull();
        assertThat(decidir(new AsignacionResponse(TipoAsignacion.CAMPO, null, null), Map.of("aprobador", 5)))
                .extracting(AsignacionAlCrear.Destino::porQueNo).asString().contains("no tiene esa variable");
        verify(personas, never()).siPuedeTenerla(any(), any());
    }

    private AsignacionAlCrear.Destino decidir(AsignacionResponse regla, Map<String, Object> variables) {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, 2L);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Approve", TipoActividad.USUARIO);
        armado.arco("Start", "Approve");
        armado.asignacion("Approve", regla);
        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
        return asignacion.decidir(caso, grafo, grafo.nodo(armado.id("Approve")).orElseThrow(),
                Variables.de(variables));
    }
}
