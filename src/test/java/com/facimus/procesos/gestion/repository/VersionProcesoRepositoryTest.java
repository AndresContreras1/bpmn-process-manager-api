package com.facimus.procesos.gestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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

import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;

/**
 * Las versiones contra la base: los numeros no se repiten dentro de un proceso, la vigente es la ultima que sigue en
 * pie, y el diagrama entero cabe en la columna, que para eso es un clob.
 * <p>
 * El slice conserva la base del perfil test (replace = NONE), que ya es H2 en memoria y trae el esquema de Flyway.
 * La auditoria se importa aparte porque las fechas de creacion y de modificacion no aceptan nulos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class VersionProcesoRepositoryTest {

    private static final String HUELLA = "9f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VersionProcesoRepository versionProcesoRepository;

    private Empresa tienda;
    private Proceso proceso;

    @BeforeEach
    void crearLaTiendaYSuProceso() {
        tienda = empresa("Tienda de versiones");
        proceso = em.persistFlushFind(Proceso.builder()
                .empresa(tienda)
                .nombre("Order fulfillment")
                .descripcion("Checkout to delivery")
                .categoria("Fulfillment")
                .estado(EstadoProceso.PUBLICADO)
                .activo(true)
                .build());
    }

    @Test
    @DisplayName("El numero de version no se repite dentro de un proceso")
    void versiones_mismoNumero_laBaseLasRechaza() {
        versionProcesoRepository.saveAndFlush(version(1, EstadoVersion.VIGENTE, HUELLA));

        assertThatThrownBy(() -> versionProcesoRepository
                .saveAndFlush(version(1, EstadoVersion.VIGENTE, "a" + HUELLA.substring(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La vigente es la ultima que no se retiro, y sin ninguna en pie no hay vigente")
    void vigente_esLaUltimaQueSigueEnPie() {
        em.persistFlushFind(version(1, EstadoVersion.VIGENTE, HUELLA));
        VersionProceso segunda = em.persistFlushFind(version(2, EstadoVersion.VIGENTE, "2" + HUELLA.substring(1)));

        assertThat(vigente()).contains(segunda);
        assertThat(versionProcesoRepository.ultimoNumero(proceso.getId(), tienda.getId())).contains(2);

        segunda.setEstado(EstadoVersion.RETIRADA);
        em.persistAndFlush(segunda);
        assertThat(vigente()).map(VersionProceso::getNumero).contains(1);

        VersionProceso primera = versionProcesoRepository
                .findByProcesoIdAndNumeroAndEmpresaId(proceso.getId(), 1, tienda.getId()).orElseThrow();
        primera.setEstado(EstadoVersion.RETIRADA);
        em.persistAndFlush(primera);
        assertThat(vigente()).isEmpty();
        // Los numeros no se reusan: la siguiente publicacion sera la 3, aunque no quede ninguna vigente.
        assertThat(versionProcesoRepository.ultimoNumero(proceso.getId(), tienda.getId())).contains(2);
    }

    @Test
    @DisplayName("Las versiones salen de la mas reciente a la mas vieja, y las de otra tienda no salen")
    void listar_deLaMasRecienteALaMasVieja() {
        Empresa otra = empresa("Otra tienda");
        Proceso ajeno = em.persistFlushFind(Proceso.builder()
                .empresa(otra)
                .nombre("Order fulfillment")
                .descripcion("De otra tienda")
                .categoria("Fulfillment")
                .estado(EstadoProceso.PUBLICADO)
                .activo(true)
                .build());
        em.persistFlushFind(version(1, EstadoVersion.VIGENTE, HUELLA));
        em.persistFlushFind(version(2, EstadoVersion.VIGENTE, "2" + HUELLA.substring(1)));
        em.persistFlushFind(VersionProceso.builder()
                .empresa(otra)
                .proceso(ajeno)
                .numero(1)
                .estado(EstadoVersion.VIGENTE)
                .fechaPublicacion(LocalDateTime.now())
                .huella(HUELLA)
                .definicion("{}")
                .build());

        assertThat(versionProcesoRepository
                .findAllByProcesoIdAndEmpresaIdOrderByNumeroDesc(proceso.getId(), tienda.getId()))
                .map(VersionProceso::getNumero)
                .containsExactly(2, 1);
        assertThat(versionProcesoRepository
                .findByProcesoIdAndNumeroAndEmpresaId(ajeno.getId(), 1, tienda.getId())).isEmpty();
    }

    @Test
    @DisplayName("El diagrama publicado no cabe en un varchar corriente y la columna lo guarda entero")
    void definicion_guardaUnDocumentoLargo() {
        String definicion = "{\"pools\":[" + "{\"nombre\":\"Demo Store\"},".repeat(2000) + "{}]}";

        em.persistFlushFind(version(1, EstadoVersion.VIGENTE, HUELLA, definicion));
        em.clear();

        assertThat(versionProcesoRepository.findByProcesoIdAndNumeroAndEmpresaId(proceso.getId(), 1, tienda.getId()))
                .get()
                .extracting(VersionProceso::getDefinicion)
                .isEqualTo(definicion);
    }

    private Optional<VersionProceso> vigente() {
        return versionProcesoRepository.findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(
                proceso.getId(), tienda.getId(), EstadoVersion.VIGENTE);
    }

    private VersionProceso version(int numero, EstadoVersion estado, String huella) {
        return version(numero, estado, huella, "{\"pools\":[]}");
    }

    private VersionProceso version(int numero, EstadoVersion estado, String huella, String definicion) {
        return VersionProceso.builder()
                .empresa(tienda)
                .proceso(proceso)
                .numero(numero)
                .estado(estado)
                .fechaPublicacion(LocalDateTime.now())
                .publicadoPor(null)
                .huella(huella)
                .definicion(definicion)
                .build();
    }

    private Empresa empresa(String nombre) {
        return em.persistFlushFind(Empresa.builder()
                .nombre(nombre)
                .nit(Math.abs(nombre.hashCode()) + "-1")
                .correoContacto("contacto@" + nombre.replace(" ", "").toLowerCase() + ".com")
                .fechaRegistro(LocalDate.now())
                .build());
    }
}
