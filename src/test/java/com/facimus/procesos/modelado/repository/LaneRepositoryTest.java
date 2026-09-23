package com.facimus.procesos.modelado.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;

/**
 * Las consultas escritas a mano de las lanes: la posicion de la siguiente, el orden del diagrama y el uso de un rol
 * de proceso. Son las que ningun metodo derivado cubre, y las que sostienen HU-19 y el endpoint del diagrama.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class LaneRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private LaneRepository laneRepository;

    private DiagramaDePrueba tienda;
    private DiagramaDePrueba otraTienda;

    @BeforeEach
    void crearLasDosTiendas() {
        tienda = new DiagramaDePrueba(em, "Demo Store");
        otraTienda = new DiagramaDePrueba(em, "Partner 3PL");
    }

    @Test
    @DisplayName("La siguiente posicion va despues de la ultima lane del pool, y cada pool lleva la suya")
    void siguienteOrden_vaDespuesDeLaUltima() {
        Proceso proceso = tienda.proceso("Order fulfillment");
        Pool pool = tienda.pool(proceso, "Demo Store", 0);
        Pool otroPool = tienda.pool(proceso, "Carrier", 1);
        RolProceso rol = tienda.rol("Warehouse");

        assertThat(laneRepository.siguienteOrden(pool.getId(), empresaId())).isZero();

        Lane primera = tienda.lane(pool, rol, "Picking", 0);
        tienda.lane(pool, rol, "Packing", 1);

        assertThat(laneRepository.siguienteOrden(pool.getId(), empresaId())).isEqualTo(2);
        assertThat(laneRepository.siguienteOrden(otroPool.getId(), empresaId())).isZero();

        // Al borrar una lane del medio, la que sigue no reutiliza su posicion.
        laneRepository.delete(primera);
        em.flush();

        assertThat(laneRepository.siguienteOrden(pool.getId(), empresaId())).isEqualTo(2);
    }

    @Test
    @DisplayName("La siguiente posicion de un pool no mira las lanes de otra tienda")
    void siguienteOrden_noMiraOtraTienda() {
        Proceso ajeno = otraTienda.proceso("Carrier pickup");
        Pool poolAjeno = otraTienda.pool(ajeno, "Partner 3PL", 0);
        otraTienda.lane(poolAjeno, otraTienda.rol("Dispatch"), "Routing", 0);

        assertThat(laneRepository.siguienteOrden(poolAjeno.getId(), empresaId())).isZero();
    }

    @Test
    @DisplayName("El diagrama ordena las lanes por pool y por posicion, con su rol en la misma consulta")
    void delProcesoEnOrden_ordenaPorPoolYPosicion() {
        Proceso proceso = tienda.proceso("Order fulfillment");
        Pool segundo = tienda.pool(proceso, "Carrier", 1);
        Pool primero = tienda.pool(proceso, "Demo Store", 0);
        RolProceso almacen = tienda.rol("Warehouse");
        tienda.lane(segundo, almacen, "Routing", 0);
        tienda.lane(primero, almacen, "Packing", 1);
        tienda.lane(primero, almacen, "Picking", 0);
        em.clear();

        var lanes = laneRepository.delProcesoEnOrden(proceso.getId(), empresaId());

        assertThat(lanes).extracting(Lane::getNombre).containsExactly("Picking", "Packing", "Routing");
        // Sin el @EntityGraph, el nombre del rol de cada lane costaria una consulta aparte.
        assertThat(Hibernate.isInitialized(lanes.get(0).getRolProceso())).isTrue();
    }

    @Test
    @DisplayName("HU-19: un rol esta en uso una vez por proceso activo, aunque lo usen varias lanes")
    void contarProcesosActivosDelRol_cuentaCadaProcesoUnaVez() {
        RolProceso almacen = tienda.rol("Warehouse");
        Proceso despacho = tienda.proceso("Order fulfillment");
        Pool pool = tienda.pool(despacho, "Demo Store", 0);
        tienda.lane(pool, almacen, "Picking", 0);
        tienda.lane(pool, almacen, "Packing", 1);

        assertThat(laneRepository.contarProcesosActivosDelRol(empresaId(), almacen.getId())).isEqualTo(1);

        Proceso devoluciones = tienda.proceso("Returns");
        tienda.lane(tienda.pool(devoluciones, "Demo Store", 0), almacen, "Restock", 0);

        assertThat(laneRepository.contarProcesosActivosDelRol(empresaId(), almacen.getId())).isEqualTo(2);
        assertThat(laneRepository.nombresDeProcesosActivosDelRol(empresaId(), almacen.getId()))
                .containsExactly("Order fulfillment", "Returns");
    }

    @Test
    @DisplayName("HU-19: un proceso eliminado deja de usar el rol, y el rol queda libre")
    void contarProcesosActivosDelRol_ignoraLosProcesosEliminados() {
        RolProceso almacen = tienda.rol("Warehouse");
        Proceso eliminado = tienda.proceso("Old returns", false);
        tienda.lane(tienda.pool(eliminado, "Demo Store", 0), almacen, "Restock", 0);

        assertThat(laneRepository.contarProcesosActivosDelRol(empresaId(), almacen.getId())).isZero();
        assertThat(laneRepository.nombresDeProcesosActivosDelRol(empresaId(), almacen.getId())).isEmpty();
    }

    @Test
    @DisplayName("HU-20: el conteo de varios roles a la vez deja fuera al que no usa ningun proceso activo")
    void contarProcesosActivosPorRol_omiteElRolSinProcesos() {
        RolProceso almacen = tienda.rol("Warehouse");
        RolProceso auditor = tienda.rol("Auditor");
        RolProceso sinUso = tienda.rol("Legal");
        Proceso despacho = tienda.proceso("Order fulfillment");
        Pool pool = tienda.pool(despacho, "Demo Store", 0);
        // El almacen lleva dos lanes del mismo proceso: cuenta procesos, no lanes.
        tienda.lane(pool, almacen, "Picking", 0);
        tienda.lane(pool, almacen, "Packing", 1);
        tienda.lane(pool, auditor, "Review", 2);
        tienda.lane(tienda.pool(tienda.proceso("Returns"), "Demo Store", 0), almacen, "Restock", 0);

        var conteos = laneRepository.contarProcesosActivosPorRol(empresaId(),
                List.of(almacen.getId(), auditor.getId(), sinUso.getId()));

        assertThat(conteos).extracting(ProcesosDelRol::rolId, ProcesosDelRol::procesos)
                .containsExactlyInAnyOrder(tuple(almacen.getId(), 2L), tuple(auditor.getId(), 1L));
    }

    private Long empresaId() {
        return tienda.empresa().getId();
    }
}
