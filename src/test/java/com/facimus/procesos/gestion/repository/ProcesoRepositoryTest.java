package com.facimus.procesos.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;

/**
 * Las consultas de procesos contra la base, sin services ni controllers: la puerta de lectura de HU-23, el listado
 * de lo que otras tiendas comparten y los filtros de busqueda.
 * <p>
 * El slice conserva la base del perfil test (replace = NONE), que ya es H2 en memoria y trae el esquema de Flyway:
 * la que @DataJpaTest pone en su lugar no logra evaluar las restricciones check de las migraciones. La auditoria se
 * importa aparte porque las fechas de creacion y de modificacion no aceptan nulos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class ProcesoRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProcesoRepository procesoRepository;

    private Empresa tienda;
    private Empresa otraTienda;

    @BeforeEach
    void crearLasDosTiendas() {
        tienda = empresa("Demo Store");
        otraTienda = empresa("Partner 3PL");
    }

    @Test
    @DisplayName("HU-23: la puerta de lectura abre el proceso propio y el que otra tienda comparte")
    void paraLectura_propioYCompartido_losEncuentra() {
        Proceso propio = guardar(proceso(tienda, "Order fulfillment", EstadoProceso.PUBLICADO, "Ventas", true));
        Proceso ajeno = guardar(proceso(otraTienda, "Carrier pickup", EstadoProceso.PUBLICADO, "Logistica", true));
        compartir(ajeno, tienda);

        assertThat(procesoRepository.paraLectura(propio.getId(), tienda.getId())).contains(propio);
        assertThat(procesoRepository.paraLectura(ajeno.getId(), tienda.getId())).contains(ajeno);
    }

    @Test
    @DisplayName("HU-23: un proceso compartido se lee, pero la puerta de escritura no lo encuentra")
    void paraLectura_compartido_noSirveParaEscribir() {
        Proceso ajeno = guardar(proceso(otraTienda, "Carrier pickup", EstadoProceso.PUBLICADO, "Logistica", true));
        compartir(ajeno, tienda);

        assertThat(procesoRepository.paraLectura(ajeno.getId(), tienda.getId())).isPresent();
        assertThat(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(ajeno.getId(), tienda.getId())).isEmpty();
        assertThat(procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(ajeno.getId(), tienda.getId())).isFalse();
    }

    @Test
    @DisplayName("La puerta de lectura no abre el proceso de otra tienda que no lo comparte, ni uno eliminado")
    void paraLectura_ajenoSinCompartirOEliminado_noLoEncuentra() {
        Proceso ajeno = guardar(proceso(otraTienda, "Carrier pickup", EstadoProceso.PUBLICADO, "Logistica", true));
        Proceso eliminado = guardar(proceso(tienda, "Returns", EstadoProceso.BORRADOR, "Ventas", false));
        compartir(eliminado, otraTienda);

        assertThat(procesoRepository.paraLectura(ajeno.getId(), tienda.getId())).isEmpty();
        assertThat(procesoRepository.paraLectura(eliminado.getId(), tienda.getId())).isEmpty();
        assertThat(procesoRepository.paraLectura(eliminado.getId(), otraTienda.getId())).isEmpty();
    }

    @Test
    @DisplayName("El listado de compartidos trae solo los ajenos activos, con su tienda duena ya cargada")
    void compartidosCon_soloLosAjenosActivos_conLaDuenaCargada() {
        Proceso compartido = guardar(proceso(otraTienda, "Carrier pickup", EstadoProceso.PUBLICADO, "Logistica", true));
        Proceso yaEliminado = guardar(proceso(otraTienda, "Old route", EstadoProceso.BORRADOR, "Logistica", false));
        guardar(proceso(otraTienda, "Internal audit", EstadoProceso.BORRADOR, "Control", true));
        Proceso propio = guardar(proceso(tienda, "Order fulfillment", EstadoProceso.PUBLICADO, "Ventas", true));
        compartir(compartido, tienda);
        compartir(yaEliminado, tienda);
        compartir(propio, otraTienda);
        em.clear();

        var pagina = procesoRepository.compartidosCon(tienda.getId(), PageRequest.of(0, 10, Sort.by("id")));

        assertThat(pagina.getContent()).extracting(Proceso::getNombre).containsExactly("Carrier pickup");
        assertThat(pagina.getTotalElements()).isEqualTo(1);
        // Sin el @EntityGraph, mostrar el nombre de la duena costaria una consulta por fila.
        assertThat(Hibernate.isInitialized(pagina.getContent().get(0).getEmpresa())).isTrue();
    }

    @Test
    @DisplayName("HU-07: los filtros combinan nombre, estado y categoria dentro de la tienda")
    void conFiltros_combinaNombreEstadoYCategoria() {
        guardar(proceso(tienda, "Order fulfillment", EstadoProceso.PUBLICADO, "Ventas", true));
        guardar(proceso(tienda, "Order returns", EstadoProceso.BORRADOR, "Ventas", true));
        guardar(proceso(tienda, "Supplier onboarding", EstadoProceso.PUBLICADO, "Compras", true));
        guardar(proceso(otraTienda, "Order fulfillment", EstadoProceso.PUBLICADO, "Ventas", true));

        assertThat(buscar(null, null, null)).containsExactlyInAnyOrder("Order fulfillment", "Order returns",
                "Supplier onboarding");
        assertThat(buscar("ORDER", null, null)).containsExactlyInAnyOrder("Order fulfillment", "Order returns");
        assertThat(buscar("order", EstadoProceso.PUBLICADO, null)).containsExactly("Order fulfillment");
        assertThat(buscar(null, null, "Compras")).containsExactly("Supplier onboarding");
        assertThat(buscar("  ", EstadoProceso.BORRADOR, null)).containsExactly("Order returns");
    }

    @Test
    @DisplayName("HU-06: un proceso eliminado desaparece de los filtros y libera su nombre")
    void conFiltros_procesoEliminado_niSaleNiOcupaSuNombre() {
        guardar(proceso(tienda, "Returns", EstadoProceso.BORRADOR, "Ventas", false));

        assertThat(buscar(null, null, null)).isEmpty();
        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(tienda.getId(), "RETURNS"))
                .isFalse();

        guardar(proceso(tienda, "Returns", EstadoProceso.BORRADOR, "Ventas", true));

        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(tienda.getId(), "RETURNS"))
                .isTrue();
        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(otraTienda.getId(), "Returns"))
                .isFalse();
    }

    @Test
    @DisplayName("HU-06.3: los procesos eliminados no salen por defecto, y con el filtro si")
    void conFiltros_incluirInactivos_traeLosEliminados() {
        Proceso retirado = em.persistFlushFind(Proceso.builder()
                .empresa(tienda)
                .nombre("Gift cards")
                .descripcion("Retirado")
                .categoria("After-sales")
                .estado(EstadoProceso.BORRADOR)
                .activo(false)
                .build());

        assertThat(buscar(null, null, null)).doesNotContain(retirado.getNombre());
        assertThat(buscar(null, null, null, true)).contains(retirado.getNombre());
        assertThat(buscar("gift", null, null, true)).containsExactly("Gift cards");
    }

    private List<String> buscar(String nombre, EstadoProceso estado, String categoria) {
        return buscar(nombre, estado, categoria, false);
    }

    private List<String> buscar(String nombre, EstadoProceso estado, String categoria, boolean incluirInactivos) {
        return procesoRepository.findAll(ProcesoSpecifications.conFiltros(tienda.getId(), nombre, estado, categoria,
                        incluirInactivos))
                .stream()
                .map(Proceso::getNombre)
                .toList();
    }

    private Proceso guardar(Proceso proceso) {
        return em.persistFlushFind(proceso);
    }

    private void compartir(Proceso proceso, Empresa invitada) {
        em.persistAndFlush(ProcesoCompartido.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .empresaInvitada(invitada)
                .fechaCompartido(LocalDateTime.now())
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

    private static Proceso proceso(Empresa empresa, String nombre, EstadoProceso estado, String categoria,
            boolean activo) {
        return Proceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .descripcion("Proceso de prueba")
                .categoria(categoria)
                .estado(estado)
                .activo(activo)
                .build();
    }
}
