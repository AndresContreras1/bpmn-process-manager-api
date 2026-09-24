package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import tools.jackson.databind.json.JsonMapper;

/**
 * La huella es lo que decide si un borrador tiene algo sin publicar y si una publicacion nueva vale la pena. Tiene
 * que cambiar con cualquier cambio del dibujo y quedarse quieta con todo lo demas.
 */
class HuellaDelDiagramaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @DisplayName("El mismo diagrama armado dos veces da la misma huella")
    void mismoDiagrama_mismaHuella() {
        assertThat(huella(DiagramaArmado.demo().diagrama()))
                .isEqualTo(huella(DiagramaArmado.demo().diagrama()));
    }

    @Test
    @DisplayName("Renombrar una actividad cambia la huella")
    void otroNombreDeUnaActividad_otraHuella() {
        DiagramaResponse original = DiagramaArmado.demo().diagrama();
        ActividadResponse primera = original.actividades().getFirst();
        DiagramaResponse renombrada = conActividades(original, cambiar(original.actividades(), 0,
                new ActividadResponse(primera.id(), "Otro nombre", primera.descripcion(), primera.tipoActividad(),
                        primera.posicionX(), primera.posicionY(), primera.laneId(), primera.version(),
                        primera.creadoPor(), primera.fechaCreacion(), primera.modificadoPor(),
                        primera.fechaModificacion())));

        assertThat(huella(renombrada)).isNotEqualTo(huella(original));
    }

    @Test
    @DisplayName("Mover una actividad en el lienzo cambia la huella")
    void otraPosicionDeUnaActividad_otraHuella() {
        DiagramaResponse original = DiagramaArmado.demo().diagrama();
        ActividadResponse primera = original.actividades().getFirst();
        DiagramaResponse movida = conActividades(original, cambiar(original.actividades(), 0,
                new ActividadResponse(primera.id(), primera.nombre(), primera.descripcion(),
                        primera.tipoActividad(), primera.posicionX() + 40, primera.posicionY(), primera.laneId(),
                        primera.version(), primera.creadoPor(), primera.fechaCreacion(), primera.modificadoPor(),
                        primera.fechaModificacion())));

        assertThat(huella(movida)).isNotEqualTo(huella(original));
    }

    @Test
    @DisplayName("Cambiar la condicion de un arco cambia la huella")
    void otraCondicionDeUnArco_otraHuella() {
        DiagramaResponse original = DiagramaArmado.demo().diagrama();
        int conCondicion = indiceDelArcoConCondicion(original);
        ArcoResponse arco = original.arcos().get(conCondicion);
        DiagramaResponse otra = conArcos(original, cambiar(original.arcos(), conCondicion,
                new ArcoResponse(arco.id(), arco.etiqueta(), "pedido.total > 100", arco.porDefecto(), arco.orden(),
                        arco.origenId(), arco.destinoId(), arco.poolId(), arco.version(), arco.creadoPor(),
                        arco.fechaCreacion(), arco.modificadoPor(), arco.fechaModificacion())));

        assertThat(huella(otra)).isNotEqualTo(huella(original));
    }

    @Test
    @DisplayName("Un mensaje menos cambia la huella")
    void unMensajeMenos_otraHuella() {
        DiagramaArmado armado = DiagramaArmado.demo();
        String completa = huella(armado.diagrama());
        armado.quitarMensaje("Shipment request");

        assertThat(huella(armado.diagrama())).isNotEqualTo(completa);
    }

    @Test
    @DisplayName("Cambiar como viaja un mensaje cambia la huella, sin tocar nada mas")
    void otroTipoDeDestinoDeUnMensaje_otraHuella() {
        DiagramaArmado armado = DiagramaArmado.demo();
        String completa = huella(armado.diagrama());
        // Solo cambia un campo del mensaje: ni sus correlaciones ni ningun otro elemento se mueven.
        armado.sinTipoDestino("Shipment request");

        assertThat(huella(armado.diagrama())).isNotEqualTo(completa);
    }

    @Test
    @DisplayName("Una correlacion menos cambia la huella")
    void unaCorrelacionMenos_otraHuella() {
        DiagramaArmado armado = DiagramaArmado.demo();
        String completa = huella(armado.diagrama());
        armado.quitarCorrelacionDe("Shipment request");

        assertThat(huella(armado.diagrama())).isNotEqualTo(completa);
    }

    @Test
    @DisplayName("Una lane menos cambia la huella")
    void unaLaneMenos_otraHuella() {
        DiagramaArmado armado = DiagramaArmado.demo();
        String completa = huella(armado.diagrama());
        armado.quitarLane(DiagramaArmado.ALMACEN);

        assertThat(huella(armado.diagrama())).isNotEqualTo(completa);
    }

    @Test
    @DisplayName("Un pool que deja de ser caja negra cambia la huella")
    void otroParticipanteModelado_otraHuella() {
        DiagramaArmado armado = DiagramaArmado.demo();
        String completa = huella(armado.diagrama());
        armado.dejaDeSerCajaNegra("Customer");

        assertThat(huella(armado.diagrama())).isNotEqualTo(completa);
    }

    @Test
    @DisplayName("Que una consulta devuelva las filas en otro orden no es un cambio del diagrama")
    void otroOrdenDeLasListas_mismaHuella() {
        DiagramaResponse original = DiagramaArmado.demo().diagrama();
        DiagramaResponse alReves = new DiagramaResponse(original.proceso(), original.compartido(),
                invertida(original.pools()), invertida(original.lanes()), invertida(original.actividades()),
                invertida(original.gateways()), invertida(original.eventos()), invertida(original.arcos()),
                invertida(original.mensajes()), invertida(original.correlaciones()));

        assertThat(huella(alReves)).isEqualTo(huella(original));
    }

    @Test
    @DisplayName("Renombrar el proceso cambia la huella: la version publicada lleva su nombre")
    void otroNombreDelProceso_otraHuella() {
        DiagramaResponse original = DiagramaArmado.demo().diagrama();
        ProcesoResponse proceso = original.proceso();
        DiagramaResponse renombrado = original.conProceso(new ProcesoResponse(proceso.id(), "Otro proceso",
                proceso.descripcion(), proceso.categoria(), proceso.estado(), proceso.activo(),
                proceso.fechaCreacion(), proceso.fechaModificacion(), proceso.version(), proceso.creadoPor(),
                proceso.modificadoPor(), proceso.versionPublicada(), proceso.borradorPendiente()), false);

        assertThat(huella(renombrado)).isNotEqualTo(huella(original));
    }

    @Test
    @DisplayName("Guardar sin cambiar nada no cambia la huella, aunque suban la version y las fechas")
    void otraAuditoriaDeUnElemento_mismaHuella() {
        DiagramaResponse original = DiagramaArmado.demo().diagrama();
        ActividadResponse primera = original.actividades().getFirst();
        DiagramaResponse guardada = conActividades(original, cambiar(original.actividades(), 0,
                new ActividadResponse(primera.id(), primera.nombre(), primera.descripcion(),
                        primera.tipoActividad(), primera.posicionX(), primera.posicionY(), primera.laneId(),
                        99L, 7L, LocalDateTime.of(2026, 1, 1, 8, 0), 8L, LocalDateTime.of(2026, 2, 2, 9, 0))));

        assertThat(huella(guardada)).isEqualTo(huella(original));
    }

    @Test
    @DisplayName("Publicar no cambia la huella del diagrama que se acaba de publicar")
    void procesoPublicado_mismaHuella() {
        DiagramaResponse borrador = DiagramaArmado.demo().diagrama();

        assertThat(huella(borrador.conProceso(borrador.proceso().publicadoComo(1), false)))
                .isEqualTo(huella(borrador));
    }

    private static String huella(DiagramaResponse diagrama) {
        return Huella.de(DiagramaCanonico.de(diagrama, JSON));
    }

    private static int indiceDelArcoConCondicion(DiagramaResponse diagrama) {
        List<ArcoResponse> arcos = diagrama.arcos();
        for (int i = 0; i < arcos.size(); i++) {
            if (arcos.get(i).condicion() != null) {
                return i;
            }
        }
        throw new IllegalStateException("La demo tiene arcos con condicion.");
    }

    private static <T> List<T> cambiar(List<T> original, int posicion, T elemento) {
        List<T> copia = new ArrayList<>(original);
        copia.set(posicion, elemento);
        return List.copyOf(copia);
    }

    private static <T> List<T> invertida(List<T> original) {
        List<T> copia = new ArrayList<>(original);
        Collections.reverse(copia);
        return List.copyOf(copia);
    }

    private static DiagramaResponse conActividades(DiagramaResponse diagrama, List<ActividadResponse> actividades) {
        return new DiagramaResponse(diagrama.proceso(), diagrama.compartido(), diagrama.pools(), diagrama.lanes(),
                actividades, diagrama.gateways(), diagrama.eventos(), diagrama.arcos(), diagrama.mensajes(),
                diagrama.correlaciones());
    }

    private static DiagramaResponse conArcos(DiagramaResponse diagrama, List<ArcoResponse> arcos) {
        return new DiagramaResponse(diagrama.proceso(), diagrama.compartido(), diagrama.pools(), diagrama.lanes(),
                diagrama.actividades(), diagrama.gateways(), diagrama.eventos(), arcos, diagrama.mensajes(),
                diagrama.correlaciones());
    }
}
