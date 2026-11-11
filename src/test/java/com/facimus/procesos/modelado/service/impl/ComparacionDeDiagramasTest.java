package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse.Campo;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse.Elemento;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

class ComparacionDeDiagramasTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ComparacionResponse comparar(DiagramaResponse uno, DiagramaResponse otro) {
        return ComparacionDeDiagramas.comparar("1", uno, "borrador", otro, JSON);
    }

    @Test
    @DisplayName("Dos diagramas iguales no tienen diferencias")
    void iguales() {
        assertThat(comparar(DiagramaArmado.demo().diagrama(), DiagramaArmado.demo().diagrama()).sinDiferencias())
                .isTrue();
    }

    @Test
    @DisplayName("Lo que aparece y lo que desaparece, con su bpmnId, su tipo y su nombre")
    void agregadosYQuitados() {
        DiagramaArmado armado = DiagramaArmado.demo();
        Long nuevo = armado.actividad(DiagramaArmado.VENTAS, "Brand new", TipoActividad.USUARIO);

        ComparacionResponse ida = comparar(DiagramaArmado.demo().diagrama(), armado.diagrama());
        ComparacionResponse vuelta = comparar(armado.diagrama(), DiagramaArmado.demo().diagrama());

        Elemento esperado = new Elemento("Activity_" + nuevo, "ACTIVIDAD", "Brand new");
        assertThat(ida.agregados()).containsExactly(esperado);
        assertThat(ida.quitados()).isEmpty();
        assertThat(vuelta.quitados()).containsExactly(esperado);
    }

    @Test
    @DisplayName("Un cambio dice que campos, antes y despues; el modo es del proceso")
    void cambiados() {
        DiagramaArmado documental = DiagramaArmado.demo();
        documental.modo(ModoProceso.DOCUMENTAL);

        ComparacionResponse comparacion = comparar(DiagramaArmado.demo().diagrama(), documental.diagrama());

        assertThat(comparacion.cambiados()).singleElement().satisfies(cambiado -> {
            assertThat(cambiado.tipo()).isEqualTo("PROCESO");
            assertThat(cambiado.campos()).containsExactly(new Campo("modo", "EJECUTABLE", "DOCUMENTAL"));
        });
    }

    @Test
    @DisplayName("Lo que solo cambio de sitio esta movido, no cambiado")
    void movidos() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        String nota = armado.artefacto(DiagramaArmado.TIENDA, "Sales", "Nota", TipoArtefacto.ANOTACION, "Hello");
        DiagramaResponse uno = armado.diagrama();
        var artefacto = uno.artefactos().getFirst();
        var movido = new com.facimus.procesos.modelado.dto.response.ArtefactoResponse(artefacto.id(), artefacto.bpmnId(),
                artefacto.tipo(), artefacto.texto(), artefacto.poolId(), artefacto.laneId(), 90, 80, artefacto.ancho(),
                artefacto.alto(), 7L, null, null, null, null);
        DiagramaResponse otro = new DiagramaResponse(uno.proceso(), false, uno.pools(), uno.lanes(), uno.actividades(),
                uno.gateways(), uno.eventos(), uno.arcos(), uno.mensajes(), uno.correlaciones(),
                java.util.List.of(movido), uno.asociaciones());

        ComparacionResponse comparacion = comparar(uno, otro);

        assertThat(comparacion.cambiados()).isEmpty();
        assertThat(comparacion.movidos()).extracting(Elemento::bpmnId).containsExactly(nota);
    }

    @Test
    @DisplayName("Los ids de la base no cuentan: una referencia se compara por el bpmnId al que apunta")
    void referenciasPorBpmnId() {
        DiagramaResponse uno = DiagramaArmado.demo().diagrama();
        var original = uno.lanes().getFirst();
        var conOtroId = new com.facimus.procesos.modelado.dto.response.LaneResponse(original.id() + 1000,
                original.bpmnId(), original.nombre(), original.orden(), original.poolId(), original.rolProcesoId(),
                original.rolProcesoNombre(), original.version(), original.creadoPor(), original.fechaCreacion(),
                original.modificadoPor(), original.fechaModificacion());
        var lanes = new java.util.ArrayList<>(uno.lanes());
        lanes.set(0, conOtroId);
        var actividades = uno.actividades().stream()
                .map(a -> a.laneId().equals(original.id())
                        ? new com.facimus.procesos.modelado.dto.response.ActividadResponse(a.id(), a.bpmnId(), a.nombre(),
                                a.descripcion(), a.tipoActividad(), a.posicionX(), a.posicionY(), original.id() + 1000,
                                a.version(), a.creadoPor(), a.fechaCreacion(), a.modificadoPor(), a.fechaModificacion())
                        : a)
                .toList();
        var eventos = uno.eventos().stream()
                .map(e -> e.laneId().equals(original.id())
                        ? new com.facimus.procesos.modelado.dto.response.EventoResponse(e.id(), e.bpmnId(), e.nombre(),
                                e.tipoEvento(), e.posicionX(), e.posicionY(), original.id() + 1000, e.version(),
                                e.creadoPor(), e.fechaCreacion(), e.modificadoPor(), e.fechaModificacion())
                        : e)
                .toList();
        var gateways = uno.gateways().stream()
                .map(g -> g.laneId().equals(original.id())
                        ? new com.facimus.procesos.modelado.dto.response.GatewayResponse(g.id(), g.bpmnId(), g.nombre(),
                                g.tipoGateway(), g.posicionX(), g.posicionY(), original.id() + 1000, g.version(),
                                g.creadoPor(), g.fechaCreacion(), g.modificadoPor(), g.fechaModificacion())
                        : g)
                .toList();
        DiagramaResponse otro = new DiagramaResponse(uno.proceso(), false, uno.pools(), lanes, actividades, gateways,
                eventos, uno.arcos(), uno.mensajes(), uno.correlaciones());

        assertThat(comparar(uno, otro).sinDiferencias()).isTrue();
    }

    @Test
    @DisplayName("La posicion de un evento de borde no cuenta: se dibuja sobre su actividad")
    void bordeNoSeMueve() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(DiagramaArmado.TIENDA, "Sales");
        armado.actividad("Sales", "Work", TipoActividad.USUARIO);
        armado.temporizador("Sales", "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, "PT1H", "Work");
        DiagramaResponse uno = armado.diagrama();
        var e = uno.eventos().getFirst();
        var movido = new com.facimus.procesos.modelado.dto.response.EventoResponse(e.id(), e.bpmnId(), e.nombre(),
                e.tipoEvento(), 500, 500, e.laneId(), e.version(), null, null, null, null, e.tipoTemporizador(),
                e.temporizador(), e.adjuntoAId(), e.interrumpe());
        DiagramaResponse otro = new DiagramaResponse(uno.proceso(), false, uno.pools(), uno.lanes(), uno.actividades(),
                uno.gateways(), java.util.List.of(movido), uno.arcos(), uno.mensajes(), uno.correlaciones());

        assertThat(comparar(uno, otro).sinDiferencias()).isTrue();
    }
}
