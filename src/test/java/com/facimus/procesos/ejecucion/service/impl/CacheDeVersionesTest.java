package com.facimus.procesos.ejecucion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Objects;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.config.CacheConfig;
import com.facimus.procesos.ejecucion.TiendaConMensajeria;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.VersionProcesoRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.DiagramaService;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;

/**
 * La cache de lo publicado (D19). Las pruebas que importan no son "guarda y devuelve lo guardado", sino las tres
 * maneras en que una cache se convierte en un error: servir un diagrama viejo, servirselo a quien no puede verlo y
 * dejar de acertar.
 *
 * <p>Es la unica prueba que la enciende: en el perfil test esta apagada a proposito, porque una prueba que publica,
 * lee, cambia y vuelve a leer tiene que estar viendo la base.
 */
@SpringBootTest(properties = {"cache.versiones.activa=true",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CacheDeVersionesTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private ProcesoCompartidoService procesoCompartidoService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private VersionProcesoRepository versionProcesoRepository;

    @Autowired
    private DiagramaService diagramaService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private GrafosDeVersion grafos;

    @Autowired
    private CacheManager caches;

    @Autowired
    private MeterRegistry registro;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private ApplicationContext contexto;

    private Statistics estadisticas;
    private TransactionTemplate enTransaccion;
    private Long empresaId;
    private Long adminId;
    private Long invitadaId;
    private Long otraId;
    private Long procesoId;
    private Long versionId;

    @BeforeAll
    void unaTiendaConUnProcesoPublicadoYCompartido() {
        estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        enTransaccion = new TransactionTemplate(transacciones);

        empresaId = empresaService.registrar("Tienda de la cache", "900707070-1", "contacto@cache.com",
                "Administradora", "admin@cache.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@cache.com").orElseThrow().getId();
        invitadaId = empresaService.registrar("Tienda invitada a la cache", "900707071-1", "contacto@invitada.com",
                "Invitada", "admin@invitada.com", "clave12345").id();
        otraId = empresaService.registrar("Tienda de al lado", "900707072-1", "contacto@allado.com",
                "Vecina", "admin@allado.com", "clave12345").id();

        procesoId = new TiendaConMensajeria(contexto).publicar(empresaId, adminId, "Order fulfillment cacheado");
        versionId = versionProcesoRepository.idDeLaVigente(procesoId, empresaId, EstadoVersion.VIGENTE)
                .orElseThrow();
        procesoCompartidoService.compartir(empresaId, procesoId, adminId, "900707071-1");
    }

    @Test
    @DisplayName("El grafo de una version se arma una sola vez: la segunda lectura no consulta nada")
    void elGrafo_seArmaUnaSolaVez() {
        vaciarLasCaches();

        estadisticas.clear();
        GrafoDeVersion primero = enTransaccion.execute(sinNombre -> grafos.del(empresaId, comoLlegaEjecutando()));
        long consultasDeLaPrimera = estadisticas.getPrepareStatementCount();

        estadisticas.clear();
        GrafoDeVersion segundo = enTransaccion.execute(sinNombre -> grafos.del(empresaId, comoLlegaEjecutando()));

        // La primera lee la fila de la version, que es donde esta el diagrama entero; la segunda, ni eso: el proxy
        // sabe su id sin despertarse, y con la clave basta.
        assertThat(consultasDeLaPrimera).isEqualTo(1);
        assertThat(estadisticas.getPrepareStatementCount()).isZero();
        assertThat(segundo).isSameAs(primero);
    }

    @Test
    @DisplayName("La clave lleva la tienda: la misma version preguntada por otra no es la misma entrada")
    void laClave_llevaLaTienda() {
        GrafoDeVersion deLaDuena = enTransaccion.execute(sinNombre -> grafos.del(empresaId, comoLlegaEjecutando()));

        GrafoDeVersion deOtra = enTransaccion.execute(sinNombre -> grafos.del(otraId, comoLlegaEjecutando()));

        assertThat(deOtra).isNotSameAs(deLaDuena);
    }

    @Test
    @DisplayName("Con la cache caliente, el diagrama publicado sigue sin salir para quien no puede leerlo")
    void laCacheCaliente_noSeSaltaElPermiso() {
        String deLaDuena = versionService.definicionVigente(empresaId, procesoId).orElseThrow();
        // La invitada lee lo publicado por la duena: misma entrada, porque la clave es la tienda propietaria.
        assertThat(diagramaService.obtener(invitadaId, procesoId).actividades()).isNotEmpty();

        assertThatThrownBy(() -> diagramaService.obtener(otraId, procesoId))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> versionService.definicion(otraId, procesoId, 1))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(versionService.definicionVigente(otraId, procesoId)).isEmpty();
        assertThat(deLaDuena).contains(TiendaConMensajeria.REVISAR);
    }

    @Test
    @DisplayName("Publicar otra version y retirarla se ven en la siguiente lectura")
    void publicarYRetirar_seVenEnseguida() {
        assertThat(versionService.definicionVigente(empresaId, procesoId).orElseThrow())
                .contains(TiendaConMensajeria.REVISAR);

        renombrarLaRevision("Check the order twice");
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());

        assertThat(versionService.definicionVigente(empresaId, procesoId).orElseThrow())
                .contains("Check the order twice")
                .doesNotContain(TiendaConMensajeria.REVISAR);

        versionService.retirar(empresaId, procesoId, 2, adminId);

        // Lo que se guarda es lo que dice cada version, que no cambia; cual es la vigente se pregunta siempre.
        assertThat(versionService.definicionVigente(empresaId, procesoId).orElseThrow())
                .contains(TiendaConMensajeria.REVISAR);
    }

    @Test
    @DisplayName("Actuator cuenta los aciertos de la cache")
    void actuator_cuentaLosAciertos() {
        enTransaccion.execute(sinNombre -> grafos.del(empresaId, comoLlegaEjecutando()));
        enTransaccion.execute(sinNombre -> grafos.del(empresaId, comoLlegaEjecutando()));

        assertThat(registro.get("cache.gets").tags("cache", CacheConfig.GRAFOS, "result", "hit")
                .functionCounter().count()).isPositive();
    }

    /** La version tal como llega ejecutando un caso: un proxy del que solo se ha leido el id. */
    private VersionProceso comoLlegaEjecutando() {
        return versionProcesoRepository.getReferenceById(versionId);
    }

    private void vaciarLasCaches() {
        caches.getCacheNames().stream().map(caches::getCache).filter(Objects::nonNull).forEach(Cache::clear);
    }

    private void renombrarLaRevision(String nombre) {
        ActividadResponse revisar = diagramaService.obtener(empresaId, procesoId).actividades().stream()
                .filter(actividad -> TiendaConMensajeria.REVISAR.equals(actividad.nombre()))
                .findFirst()
                .orElseThrow();
        actividadService.editar(empresaId, adminId, revisar.id(), nombre, revisar.descripcion(),
                revisar.tipoActividad(), revisar.laneId(), revisar.posicionX(), revisar.posicionY(),
                revisar.version());
    }
}
