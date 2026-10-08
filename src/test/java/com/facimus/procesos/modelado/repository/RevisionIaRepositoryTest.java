package com.facimus.procesos.modelado.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.RevisionIa;

/**
 * Las revisiones con IA en la base (D34): cual es la ultima de un proceso, cuantas cuentan para el limite de la tienda
 * y que se lleva la purga. Cada consulta filtra por tienda, como cualquier otra.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class RevisionIaRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RevisionIaRepository revisiones;

    private Empresa tienda;
    private Proceso proceso;
    private Proceso otroProceso;

    @BeforeEach
    void crearLaTiendaYSusProcesos() {
        tienda = empresa("Tienda revisada");
        proceso = proceso(tienda, "Order fulfillment");
        otroProceso = proceso(tienda, "Returns");
    }

    @Test
    @DisplayName("La ultima revision de un proceso es la mas reciente, y solo la encuentra su tienda")
    void ultima_esLaMasRecienteYSoloDeSuTienda() {
        revision(proceso, "vieja", AHORA.minusMinutes(5));
        revision(proceso, "nueva", AHORA);
        Empresa otraTienda = empresa("Otra tienda");

        assertThat(revisiones.findFirstByProcesoIdAndEmpresaIdOrderByFechaDescIdDesc(proceso.getId(), tienda.getId()))
                .map(RevisionIa::getHuella)
                .contains("nueva");
        assertThat(revisiones.findFirstByProcesoIdAndEmpresaIdOrderByFechaDescIdDesc(proceso.getId(),
                otraTienda.getId())).isEmpty();
    }

    @Test
    @DisplayName("El limite cuenta las de la tienda dentro de la ventana, y la mas vieja dice cuando se libera")
    void ventana_cuentaLasDeLaTiendaYLaMasViejaDiceCuandoSeLibera() {
        revision(proceso, "fuera", AHORA.minusHours(2));
        revision(proceso, "dentro", AHORA.minusMinutes(30));
        revision(otroProceso, "tambien", AHORA.minusMinutes(10));
        LocalDateTime desde = AHORA.minusHours(1);

        assertThat(revisiones.countByEmpresaIdAndFechaAfter(tienda.getId(), desde)).isEqualTo(2);
        assertThat(revisiones.findFirstByEmpresaIdAndFechaAfterOrderByFechaAscIdAsc(tienda.getId(), desde))
                .map(RevisionIa::getHuella)
                .contains("dentro");
    }

    @Test
    @DisplayName("La purga se lleva las superadas que salieron de la ventana y deja la ultima de cada proceso")
    void purga_dejaLaUltimaDeCadaProceso() {
        revision(proceso, "vieja", AHORA.minusHours(3));
        revision(proceso, "intermedia", AHORA.minusHours(2));
        revision(proceso, "ultima", AHORA.minusMinutes(90));
        revision(otroProceso, "sola", AHORA.minusHours(5));

        int borradas = revisiones.borrarSuperadasAntesDe(AHORA.minusHours(1));
        em.clear();

        assertThat(borradas).isEqualTo(2);
        assertThat(revisiones.findAllByEmpresaId(tienda.getId())).extracting(RevisionIa::getHuella)
                .containsExactlyInAnyOrder("ultima", "sola");
    }

    private void revision(Proceso deQuien, String huella, LocalDateTime fecha) {
        em.persistAndFlush(RevisionIa.builder()
                .empresa(deQuien.getEmpresa())
                .procesoId(deQuien.getId())
                .huella(huella)
                .resumen("Resumen de " + huella)
                .hallazgos("[]")
                .fecha(fecha)
                .build());
    }

    private Empresa empresa(String nombre) {
        return em.persistFlushFind(Empresa.builder()
                .nombre(nombre)
                .nit(Math.abs(nombre.hashCode()) + "-1")
                .correoContacto("contacto@" + nombre.replace(" ", "").toLowerCase() + ".com")
                .fechaRegistro(LocalDate.now())
                .build());
    }

    private Proceso proceso(Empresa empresa, String nombre) {
        return em.persistFlushFind(Proceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .descripcion("Proceso de prueba")
                .categoria("Ventas")
                .estado(EstadoProceso.BORRADOR)
                .activo(true)
                .build());
    }
}
