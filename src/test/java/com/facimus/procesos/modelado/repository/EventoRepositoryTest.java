package com.facimus.procesos.modelado.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import com.facimus.procesos.modelado.model.Evento;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;

/**
 * El tercer subtipo de NodoFlujo visto desde la base: comparte la tabla con actividades y gateways, se distingue por
 * el discriminador, declara su propio borrado logico y la base exige que cada subtipo llene su columna de tipo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class EventoRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Autowired
    private NodoFlujoRepository nodoFlujoRepository;

    private DiagramaDePrueba tienda;
    private Proceso proceso;
    private Lane lane;

    @BeforeEach
    void armarUnDiagramaMinimo() {
        tienda = new DiagramaDePrueba(em, "Tienda de eventos");
        proceso = tienda.proceso("Order fulfillment");
        Pool pool = tienda.pool(proceso, "Demo Store", 0);
        lane = tienda.lane(pool, tienda.rol("Sales"), "Sales", 0);
    }

    @Test
    @DisplayName("Los eventos de un proceso se leen sin traer sus actividades ni sus gateways")
    void eventos_seLeenPorSuDiscriminador() {
        tienda.evento(lane, "Order received", TipoEvento.MENSAJE_INICIO);
        tienda.evento(lane, "Order shipped", TipoEvento.FIN);
        tienda.actividad(lane, "Receive order");
        tienda.gateway(lane, "Payment approved?", TipoGateway.EXCLUSIVO);
        em.flush();
        em.clear();

        assertThat(eventoRepository.findAllByLane_Pool_ProcesoIdAndEmpresaIdOrderByIdAsc(proceso.getId(), empresaId()))
                .extracting(Evento::getNombre, Evento::getTipoEvento)
                .containsExactly(tuple("Order received", TipoEvento.MENSAJE_INICIO),
                        tuple("Order shipped", TipoEvento.FIN));
        assertThat(eventoRepository.findAllByLaneIdAndEmpresaId(lane.getId(), empresaId())).hasSize(2);
        assertThat(actividadRepository.findAllByLaneIdAndEmpresaId(lane.getId(), empresaId())).hasSize(1);
        assertThat(gatewayRepository.findAllByLaneIdAndEmpresaId(lane.getId(), empresaId())).hasSize(1);
        assertThat(nodoFlujoRepository.findAllByEmpresaId(empresaId())).extracting(NodoFlujo::getNombre)
                .containsExactlyInAnyOrder("Order received", "Order shipped", "Receive order",
                        "Payment approved?");
    }

    @Test
    @DisplayName("El evento declara su propio borrado: la fila se queda, inactiva, y ninguna consulta la ve")
    void borrarEvento_dejaLaFilaInactiva() {
        Evento evento = tienda.evento(lane, "Order cancelled", TipoEvento.FIN);
        Long eventoId = evento.getId();

        eventoRepository.delete(evento);
        em.flush();
        em.clear();

        assertThat(filasEnNodosFlujo(eventoId)).isEqualTo(1);
        assertThat(eventoRepository.findByIdAndEmpresaId(eventoId, empresaId())).isEmpty();
        assertThat(eventoRepository.existsByIdAndEmpresaId(eventoId, empresaId())).isFalse();
        assertThat(nodoFlujoRepository.existsByLaneIdAndEmpresaId(lane.getId(), empresaId())).isFalse();
        // El nombre queda libre para otro nodo del proceso.
        assertThat(nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId("ORDER CANCELLED",
                proceso.getId(), empresaId())).isFalse();
    }

    @Test
    @DisplayName("La base exige el tipo de un evento y el tipo de una actividad")
    void nodoSinSuTipo_laBaseLoRechaza() {
        Evento sinTipo = tienda.eventoSinGuardar(lane, "Order received", null);
        Actividad actividadSinTipo = Actividad.builder()
                .empresa(tienda.empresa())
                .lane(lane)
                .nombre("Receive order")
                .posicionX(10)
                .posicionY(20)
                .build();

        // Por el repositorio, para que Spring Data traduzca el fallo de la base como en el resto de la suite.
        assertThatThrownBy(() -> eventoRepository.saveAndFlush(sinTipo))
                .isInstanceOf(DataIntegrityViolationException.class);
        em.getEntityManager().clear();
        assertThatThrownBy(() -> actividadRepository.saveAndFlush(actividadSinTipo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long empresaId() {
        return tienda.empresa().getId();
    }

    private long filasEnNodosFlujo(Long id) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("select count(*) from nodos_flujo where id = ?1")
                .setParameter(1, id)
                .getSingleResult()).longValue();
    }
}
