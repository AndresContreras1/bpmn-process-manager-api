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
import com.facimus.procesos.modelado.model.Gateway;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;

/**
 * La salida por defecto vista desde la base: se guarda con el orden en que el gateway la evalua, y el check no la
 * deja llevar condicion, porque es justo la que se toma cuando ninguna se cumple.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class ArcoRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ArcoRepository arcoRepository;

    private DiagramaDePrueba tienda;
    private Pool pool;
    private Gateway decidir;
    private Actividad empacar;

    @BeforeEach
    void armarUnGatewayQueDecide() {
        tienda = new DiagramaDePrueba(em, "Tienda de arcos");
        Proceso proceso = tienda.proceso("Order fulfillment");
        pool = tienda.pool(proceso, "Demo Store", 0);
        Lane ventas = tienda.lane(pool, tienda.rol("Sales"), "Sales", 0);
        decidir = tienda.gateway(ventas, "Payment approved?", TipoGateway.EXCLUSIVO);
        empacar = tienda.actividad(ventas, "Pick and pack items");
    }

    @Test
    @DisplayName("El arco guarda si es la salida por defecto y en que orden lo evalua el gateway")
    void arco_guardaLaSalidaPorDefectoYSuOrden() {
        Arco porDefecto = tienda.arcoSinGuardar(pool, decidir, empacar);
        porDefecto.setPorDefecto(true);
        porDefecto.setOrden(3);
        Long id = arcoRepository.saveAndFlush(porDefecto).getId();
        em.clear();

        Arco leido = arcoRepository.findByIdAndEmpresaId(id, tienda.empresa().getId()).orElseThrow();
        assertThat(leido.isPorDefecto()).isTrue();
        assertThat(leido.getOrden()).isEqualTo(3);
    }

    @Test
    @DisplayName("La base no deja que la salida por defecto lleve condicion")
    void salidaPorDefectoConCondicion_laBaseLaRechaza() {
        Arco contradictorio = tienda.arcoSinGuardar(pool, decidir, empacar);
        contradictorio.setPorDefecto(true);
        contradictorio.setCondicion("payment.status == APPROVED");

        assertThatThrownBy(() -> arcoRepository.saveAndFlush(contradictorio))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Un arco corriente no es la salida por defecto y se evalua en el orden 0")
    void arco_sinDecirNada_noEsLaSalidaPorDefecto() {
        Arco arco = tienda.arco(pool, decidir, empacar);

        assertThat(arco.isPorDefecto()).isFalse();
        assertThat(arco.getOrden()).isZero();
    }
}
