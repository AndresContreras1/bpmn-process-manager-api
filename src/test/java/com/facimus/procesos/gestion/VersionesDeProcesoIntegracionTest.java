package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * D2: publicar deja de ser un estado y pasa a guardar una instantanea. Lo que se edita despues es el borrador de
 * trabajo, la version publicada no se mueve, y publicar otra vez crea la siguiente.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VersionesDeProcesoIntegracionTest {

    private final AtomicInteger contador = new AtomicInteger();

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private RolProcesoService rolProcesoService;

    @Autowired
    private PoolService poolService;

    @Autowired
    private LaneService laneService;

    @Autowired
    private EventoService eventoService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private ArcoService arcoService;

    @Autowired
    private VersionService versionService;

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarTienda() {
        empresaId = empresaService.registrar("Tienda de versiones publicadas", "900777888-1",
                "contacto@publicadas.com", "Administradora", "admin@publicadas.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@publicadas.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("R-44: un proceso con errores de diagnostico no se publica y no deja ninguna version")
    void publicar_conErroresDeDiagnostico_noGuardaVersion() {
        // Recien creado solo tiene su pool: sin lanes, sin inicio y sin fin.
        ProcesoResponse proceso = crearProceso("Sin modelar");

        assertThatThrownBy(() -> publicar(proceso))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proceso no se puede publicar: 1 error de diagnóstico.")
                .asInstanceOf(InstanceOfAssertFactories.type(ReglaNegocioException.class))
                .extracting(ReglaNegocioException::getErrores, InstanceOfAssertFactories.list(String.class))
                .singleElement(InstanceOfAssertFactories.STRING)
                .startsWith("E-01 ");

        assertThat(versionService.listar(empresaId, proceso.id())).isEmpty();
        ProcesoResponse despues = procesoService.obtener(empresaId, proceso.id(), false);
        assertThat(despues.estado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(despues.versionPublicada()).isNull();
    }

    @Test
    @DisplayName("Publicar guarda la version 1, la anota en el historial y deja el borrador al dia")
    void publicar_guardaLaPrimeraVersion() {
        Long procesoId = procesoPublicable("Order fulfillment");

        ProcesoResponse publicado = publicar(procesoService.obtener(empresaId, procesoId, false));

        assertThat(publicado.estado()).isEqualTo(EstadoProceso.PUBLICADO);
        assertThat(publicado.versionPublicada()).isEqualTo(1);
        assertThat(publicado.borradorPendiente()).isFalse();
        assertThat(versionService.listar(empresaId, procesoId)).singleElement()
                .satisfies(version -> {
                    assertThat(version.numero()).isEqualTo(1);
                    assertThat(version.publicadoPor()).isEqualTo(adminId);
                    assertThat(version.huella()).hasSize(64);
                });
        assertThat(procesoService.listarHistorial(empresaId, procesoId))
                .extracting(HistorialCambioResponse::descripcionCambio)
                .contains("Versión 1 publicada.");
    }

    @Test
    @DisplayName("R-45: publicar otra vez sin haber tocado el diagrama no crea otra version")
    void publicar_sinCambios_noCreaOtraVersion() {
        Long procesoId = procesoPublicable("Returns and refunds");
        ProcesoResponse publicado = publicar(procesoService.obtener(empresaId, procesoId, false));

        assertThatThrownBy(() -> publicar(publicado))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("No hay cambios desde la versión 1.");

        assertThat(versionService.listar(empresaId, procesoId)).hasSize(1);
    }

    @Test
    @DisplayName("Editar el modelo despues de publicar deja el borrador pendiente, y la version no se entera")
    void editarDespuesDePublicar_dejaElBorradorPendiente() {
        Long procesoId = procesoPublicable("Warehouse replenishment");
        publicar(procesoService.obtener(empresaId, procesoId, false));
        String publicada = versionService.definicion(empresaId, procesoId, 1);

        renombrarLaActividad(procesoId, "Receive the order and check it");

        assertThat(procesoService.obtener(empresaId, procesoId, false).borradorPendiente()).isTrue();
        assertThat(versionService.definicion(empresaId, procesoId, 1)).isEqualTo(publicada);
        assertThat(publicada).contains("Receive order").doesNotContain("Receive the order and check it");
    }

    @Test
    @DisplayName("Publicar con cambios crea la version 2 y la 1 sigue diciendo lo que decia")
    void publicarConCambios_creaLaVersionSiguiente() {
        Long procesoId = procesoPublicable("Supplier onboarding");
        publicar(procesoService.obtener(empresaId, procesoId, false));
        renombrarLaActividad(procesoId, "Review the supplier file");

        ProcesoResponse segunda = publicar(procesoService.obtener(empresaId, procesoId, false));

        assertThat(segunda.versionPublicada()).isEqualTo(2);
        assertThat(segunda.borradorPendiente()).isFalse();
        assertThat(versionService.definicion(empresaId, procesoId, 1)).contains("Receive order");
        assertThat(versionService.definicion(empresaId, procesoId, 2)).contains("Review the supplier file");
        assertThat(versionService.listar(empresaId, procesoId)).map(VersionResponse::numero)
                .containsExactly(2, 1);
    }

    @Test
    @DisplayName("Publicar con una version vieja del proceso responde conflicto, como cualquier otra edicion")
    void publicar_conUnaVersionVieja_esUnConflicto() {
        Long procesoId = procesoPublicable("Stock count");
        ProcesoResponse leido = procesoService.obtener(empresaId, procesoId, false);
        publicar(leido);
        renombrarLaActividad(procesoId, "Count the shelves");

        assertThatThrownBy(() -> publicar(leido)).isInstanceOf(ConflictoDeVersionException.class);
    }

    @Test
    @DisplayName("Retirar la unica version deja al proceso publicado pero sin ninguna vigente")
    void retirar_laUnicaVersion_dejaAlProcesoSinVigente() {
        Long procesoId = procesoPublicable("Gift wrapping");
        publicar(procesoService.obtener(empresaId, procesoId, false));

        VersionResponse retirada = versionService.retirar(empresaId, procesoId, 1, adminId);

        assertThat(retirada.estado()).isEqualTo(EstadoVersion.RETIRADA);
        ProcesoResponse proceso = procesoService.obtener(empresaId, procesoId, false);
        assertThat(proceso.estado()).isEqualTo(EstadoProceso.PUBLICADO);
        assertThat(proceso.versionPublicada()).isNull();
        assertThat(proceso.borradorPendiente()).isFalse();
        assertThat(procesoService.listarHistorial(empresaId, procesoId))
                .extracting(HistorialCambioResponse::descripcionCambio)
                .contains("Versión 1 retirada.");
    }

    @Test
    @DisplayName("Retirar la ultima version devuelve el proceso a la anterior que sigue en pie")
    void retirar_laUltima_devuelveElProcesoALaAnterior() {
        Long procesoId = procesoPublicable("Price updates");
        publicar(procesoService.obtener(empresaId, procesoId, false));
        renombrarLaActividad(procesoId, "Update the price list");
        publicar(procesoService.obtener(empresaId, procesoId, false));

        versionService.retirar(empresaId, procesoId, 2, adminId);

        ProcesoResponse proceso = procesoService.obtener(empresaId, procesoId, false);
        assertThat(proceso.versionPublicada()).isEqualTo(1);
        // El modelo vivo es el de la version 2, asi que frente a la 1 el borrador vuelve a tener cambios.
        assertThat(proceso.borradorPendiente()).isTrue();
    }

    @Test
    @DisplayName("Retirar dos veces la misma version responde conflicto")
    void retirar_dosVeces_esUnConflicto() {
        Long procesoId = procesoPublicable("Catalog cleanup");
        publicar(procesoService.obtener(empresaId, procesoId, false));
        versionService.retirar(empresaId, procesoId, 1, adminId);

        assertThatThrownBy(() -> versionService.retirar(empresaId, procesoId, 1, adminId))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La versión 1 ya está retirada.");
    }

    @Test
    @DisplayName("Publicar despues de retirarlo todo empieza por el numero siguiente: los numeros no se reusan")
    void publicar_trasRetirarLaUnica_siguePorElNumeroSiguiente() {
        Long procesoId = procesoPublicable("Seasonal campaign");
        publicar(procesoService.obtener(empresaId, procesoId, false));
        versionService.retirar(empresaId, procesoId, 1, adminId);

        // El diagrama es el mismo que el de la version 1, y aun asi se publica: ya no hay ninguna vigente.
        ProcesoResponse segunda = publicar(procesoService.obtener(empresaId, procesoId, false));

        assertThat(segunda.versionPublicada()).isEqualTo(2);
        assertThat(versionService.listar(empresaId, procesoId)).map(VersionResponse::numero).containsExactly(2, 1);
    }

    private ProcesoResponse publicar(ProcesoResponse proceso) {
        return procesoService.cambiarEstado(empresaId, proceso.id(), adminId, EstadoProceso.PUBLICADO,
                proceso.version());
    }

    private ProcesoResponse crearProceso(String nombre) {
        return procesoService.crear(empresaId, adminId, nombre, "Proceso de la tienda", "Operations");
    }

    /** Lo minimo que el diagnostico da por bueno: una lane, un inicio, un paso y un fin, enlazados. */
    private Long procesoPublicable(String nombre) {
        Long procesoId = crearProceso(nombre).id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(empresaId, "Ventas " + contador.incrementAndGet(), null).id();
        Long laneId = laneService.crear(empresaId, adminId, tienda, "Sales", rolId).id();
        Long inicio = eventoService.crear(empresaId, adminId, laneId, "Order received", TipoEvento.INICIO,
                40, 80).id();
        Long recibir = actividadService.crear(empresaId, adminId, laneId, "Receive order", "Check the cart",
                TipoActividad.USUARIO, 180, 80).id();
        Long fin = eventoService.crear(empresaId, adminId, laneId, "Order accepted", TipoEvento.FIN, 340, 80).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, fin));
        return procesoId;
    }

    private void renombrarLaActividad(Long procesoId, String nombre) {
        var actividad = actividadService.listarPorLane(empresaId, laneDelProceso(procesoId)).getFirst();
        actividadService.editar(empresaId, adminId, actividad.id(), nombre, actividad.descripcion(),
                actividad.tipoActividad(), actividad.laneId(), actividad.posicionX(), actividad.posicionY(),
                actividad.version());
    }

    private Long laneDelProceso(Long procesoId) {
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        return laneService.listarPorPool(empresaId, tienda).getFirst().id();
    }
}
