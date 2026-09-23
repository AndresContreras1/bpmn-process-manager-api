package com.facimus.procesos.modelado.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Arco;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;

/**
 * La baja logica del modelado vista desde la base: borrar un elemento BPMN deja su fila con activo = false, ninguna
 * consulta vuelve a verlo y el nombre o el par de nodos que ocupaba quedan libres. Es lo que hacen @SQLDelete y
 * @SQLRestriction, mas el indice unico de arcos que solo cuenta los activos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class BajaLogicaRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PoolRepository poolRepository;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private NodoFlujoRepository nodoFlujoRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Autowired
    private ArcoRepository arcoRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private CorrelacionRepository correlacionRepository;

    private DiagramaDePrueba tienda;
    private Proceso proceso;
    private Pool pool;
    private Lane lane;

    @BeforeEach
    void armarUnDiagramaMinimo() {
        tienda = new DiagramaDePrueba(em, "Demo Store");
        proceso = tienda.proceso("Order fulfillment");
        pool = tienda.pool(proceso, "Demo Store", 0);
        lane = tienda.lane(pool, tienda.rol("Warehouse"), "Picking", 0);
    }

    @Test
    @DisplayName("Borrar un pool no borra su fila: la deja inactiva y fuera de toda consulta")
    void borrarPool_dejaLaFilaInactiva() {
        Pool transportadora = tienda.pool(proceso, "Carrier", 1);
        Long poolId = transportadora.getId();

        poolRepository.delete(transportadora);
        em.flush();
        em.clear();

        assertThat(filasEnLaTabla("pools", poolId)).isEqualTo(1);
        assertThat(sigueActiva("pools", poolId)).isFalse();
        assertThat(poolRepository.findByIdAndEmpresaId(poolId, empresaId())).isEmpty();
        assertThat(poolRepository.existsByIdAndEmpresaId(poolId, empresaId())).isFalse();
        assertThat(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(proceso.getId(), empresaId()))
                .extracting(Pool::getNombre).containsExactly("Demo Store");
    }

    @Test
    @DisplayName("Cada tipo de nodo declara su borrado: la actividad y el gateway tambien se dan de baja")
    void borrarNodos_actividadYGateway_sePonenInactivos() {
        Actividad actividad = tienda.actividad(lane, "Pick items");
        Gateway gateway = tienda.gateway(lane, "Stock available?", TipoGateway.EXCLUSIVO);
        Long actividadId = actividad.getId();
        Long gatewayId = gateway.getId();

        actividadRepository.delete(actividad);
        gatewayRepository.delete(gateway);
        em.flush();
        em.clear();

        assertThat(filasEnLaTabla("nodos_flujo", actividadId)).isEqualTo(1);
        assertThat(filasEnLaTabla("nodos_flujo", gatewayId)).isEqualTo(1);
        assertThat(actividadRepository.findByIdAndEmpresaId(actividadId, empresaId())).isEmpty();
        assertThat(gatewayRepository.findByIdAndEmpresaId(gatewayId, empresaId())).isEmpty();
        assertThat(nodoFlujoRepository.existsByLaneIdAndEmpresaId(lane.getId(), empresaId())).isFalse();
    }

    @Test
    @DisplayName("Un nodo eliminado deja libre su nombre dentro del proceso")
    void borrarNodo_liberaSuNombre() {
        Actividad actividad = tienda.actividad(lane, "Pick items");

        assertThat(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("PICK ITEMS",
                proceso.getId(), empresaId())).isTrue();

        actividadRepository.delete(actividad);
        em.flush();

        assertThat(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("PICK ITEMS",
                proceso.getId(), empresaId())).isFalse();
    }

    @Test
    @DisplayName("La base no deja dos arcos activos entre el mismo par de nodos")
    void arcoRepetido_laBaseLoRechaza() {
        Actividad origen = tienda.actividad(lane, "Pick items");
        Actividad destino = tienda.actividad(lane, "Pack order");
        tienda.arco(pool, origen, destino);

        assertThatThrownBy(() -> arcoRepository.saveAndFlush(tienda.arcoSinGuardar(pool, origen, destino)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("El indice unico de arcos solo cuenta los activos: el par vuelve a quedar libre al borrarlo")
    void borrarArco_liberaElParDeNodos() {
        Actividad origen = tienda.actividad(lane, "Pick items");
        Actividad destino = tienda.actividad(lane, "Pack order");
        Arco arco = tienda.arco(pool, origen, destino);

        arcoRepository.delete(arco);
        em.flush();

        Arco nuevo = tienda.arco(pool, origen, destino);

        assertThat(nuevo.getId()).isNotEqualTo(arco.getId());
        assertThat(arcoRepository.findAllByOrigenIdAndEmpresaId(origen.getId(), empresaId()))
                .extracting(Arco::getId).containsExactly(nuevo.getId());
    }

    @Test
    @DisplayName("Un mensaje y su clave de correlacion se dan de baja sin perder la fila")
    void borrarMensajeYCorrelacion_dejanSusFilasInactivas() {
        Pool transportadora = tienda.pool(proceso, "Carrier", 1);
        Mensaje mensaje = tienda.mensaje(proceso, pool, transportadora, "Shipment requested");
        Correlacion correlacion = tienda.correlacion(mensaje, "orderId");
        Long mensajeId = mensaje.getId();
        Long correlacionId = correlacion.getId();

        correlacionRepository.delete(correlacion);
        mensajeRepository.delete(mensaje);
        em.flush();
        em.clear();

        assertThat(filasEnLaTabla("mensajes", mensajeId)).isEqualTo(1);
        assertThat(filasEnLaTabla("correlaciones", correlacionId)).isEqualTo(1);
        assertThat(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(proceso.getId(), empresaId()))
                .isEmpty();
        assertThat(correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId())).isEmpty();
    }

    @Test
    @DisplayName("Una lane eliminada desaparece del pool, y el pool sigue con las demas")
    void borrarLane_desapareceDelPool() {
        Lane empaque = tienda.lane(pool, tienda.rol("Packing"), "Packing", 1);

        laneRepository.delete(empaque);
        em.flush();
        em.clear();

        assertThat(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(pool.getId(), empresaId()))
                .extracting(Lane::getNombre).containsExactly("Picking");
        assertThat(sigueActiva("lanes", empaque.getId())).isFalse();
    }

    private long filasEnLaTabla(String tabla, Long id) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("select count(*) from " + tabla + " where id = ?1")
                .setParameter(1, id)
                .getSingleResult()).longValue();
    }

    private boolean sigueActiva(String tabla, Long id) {
        return (Boolean) em.getEntityManager()
                .createNativeQuery("select activo from " + tabla + " where id = ?1")
                .setParameter(1, id)
                .getSingleResult();
    }

    private Long empresaId() {
        return tienda.empresa().getId();
    }
}
