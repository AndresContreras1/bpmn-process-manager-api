package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ParametrosSimulacionResponse;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * D16 y R-46: cada tienda decide si sus editores pueden dibujar la estructura (pools y lanes) o si eso se reserva a
 * los administradores. Lo de dentro de una lane lo siguen modelando los editores en las dos politicas.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfiguracionTiendaIntegracionTest {

    private static final String CLAVE = "clave12345";

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

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
    private ConfiguracionTiendaService configuracionTiendaService;

    @Autowired
    private HistorialCambioService historialCambioService;

    private Long empresaId;
    private Long adminId;
    private Long editoraId;

    @BeforeAll
    void registrarTienda() {
        empresaId = empresaService.registrar("Tienda con politica", "900383940-1", "contacto@politica.com",
                "Administradora", "admin@politica.com", CLAVE).id();
        adminId = usuarioRepository.findByEmail("admin@politica.com").orElseThrow().getId();
        editoraId = usuarioService.crearColaborador(empresaId, adminId, "Editora", "editora@politica.com", CLAVE,
                RolAcceso.EDITOR).id();
    }

    @Test
    @DisplayName("Una tienda nueva deja que los editores dibujen la estructura, como siempre")
    void tiendaNueva_dejaModelarALosEditores() {
        ConfiguracionTiendaResponse configuracion = configuracionTiendaService.obtener(empresaId);

        assertThat(configuracion.politicaEstructura()).isEqualTo(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
        assertThatCode(() -> crearPoolConLane("Order fulfillment", editoraId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("R-46: con la estructura reservada, la editora no crea pools ni lanes y el administrador si")
    void estructuraReservada_laEditoraNoDibuja() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Returns and refunds", "Return to refund",
                "After-sales").id();
        Long tiendaPool = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(empresaId, adminId, "Devoluciones", null).id();
        reservar(PoliticaEstructura.SOLO_ADMINISTRADOR);

        assertThatThrownBy(() -> poolService.crear(empresaId, editoraId, procesoId, "Carrier",
                TipoParticipante.PROVEEDOR, true, Integracion.TRANSPORTE))
                .isInstanceOf(SinPermisoException.class)
                .hasMessage("La tienda reserva la estructura de los diagramas a los administradores.");
        assertThatThrownBy(() -> laneService.crear(empresaId, editoraId, tiendaPool, "Recepcion", rolId))
                .isInstanceOf(SinPermisoException.class);

        assertThatCode(() -> laneService.crear(empresaId, adminId, tiendaPool, "Recepcion", rolId))
                .doesNotThrowAnyException();
        reservar(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
    }

    @Test
    @DisplayName("Cambiar la politica queda en el historial de la tienda y respeta la version leida")
    void cambiarLaPolitica_quedaEnElHistorial() {
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);

        ConfiguracionTiendaResponse despues = configuracionTiendaService.editar(empresaId, adminId,
                PoliticaEstructura.SOLO_ADMINISTRADOR, null, null, antes.version());

        assertThat(despues.politicaEstructura()).isEqualTo(PoliticaEstructura.SOLO_ADMINISTRADOR);
        assertThat(despues.version()).isEqualTo(antes.version() + 1);
        assertThat(historialCambioService.listarDeLaTienda(empresaId, Paginacion.de(0, 50)).content())
                .extracting(HistorialCambioResponse::descripcionCambio)
                .contains("La estructura de los diagramas queda reservada a los administradores.");

        assertThatThrownBy(() -> configuracionTiendaService.editar(empresaId, adminId,
                PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null, antes.version()))
                .isInstanceOf(ConflictoDeVersionException.class);
        reservar(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
    }

    @Test
    @DisplayName("Una tienda nueva estrena los parametros de fabrica de sus socios")
    void tiendaNueva_estrenaLosParametrosDeFabrica() {
        Long otraId = empresaService.registrar("Tienda con socios de fabrica", "900383940-7",
                "contacto@fabrica.com", "Otro", "admin@fabrica.com", CLAVE).id();

        ParametrosSimulacionResponse socios = configuracionTiendaService.obtener(otraId).simulacion();

        assertThat(socios.semilla()).isEqualTo(42);
        assertThat(socios.tasaRechazoPagos()).isEqualTo(10);
        assertThat(socios.ticksRespuestaPagos()).isEqualTo(1);
        assertThat(socios.reglaRechazoPagos()).isNull();
        assertThat(socios.ticksEntrega()).isEqualTo(3);
        assertThat(socios.tasaPerdidaEnvios()).isEqualTo(5);
        assertThat(socios.tasaFalloNotificaciones()).isEqualTo(2);
    }

    @Test
    @DisplayName("Los parametros se reemplazan enteros, y no mandarlos deja los que habia")
    void losParametros_seReemplazanEnteros() {
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);

        ConfiguracionTiendaResponse cambiada = configuracionTiendaService.editar(empresaId, adminId,
                antes.politicaEstructura(), null, parametros("total > 5000", 100), antes.version());
        ConfiguracionTiendaResponse sinParametros = configuracionTiendaService.editar(empresaId, adminId,
                antes.politicaEstructura(), null, null, cambiada.version());

        assertThat(cambiada.simulacion().reglaRechazoPagos()).isEqualTo("total > 5000");
        assertThat(cambiada.simulacion().tasaRechazoPagos()).isEqualTo(100);
        assertThat(sinParametros.simulacion().reglaRechazoPagos()).isEqualTo("total > 5000");
        assertThat(sinParametros.simulacion().tasaRechazoPagos()).isEqualTo(100);
        devolverLosParametros(sinParametros.version());
    }

    @Test
    @DisplayName("R-52: una regla de rechazo mal escrita no se guarda")
    void reglaMalEscrita_noSeGuarda() {
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);

        assertThatThrownBy(() -> configuracionTiendaService.editar(empresaId, adminId, antes.politicaEstructura(),
                null, parametros("total >", 10), antes.version()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no está bien escrita");
        assertThat(configuracionTiendaService.obtener(empresaId).simulacion().reglaRechazoPagos()).isNull();
    }

    @Test
    @DisplayName("Los parametros de una tienda no alcanzan a las demas")
    void losParametros_sonDeCadaTienda() {
        Long otraId = empresaService.registrar("Tienda con sus propios socios", "900383940-8",
                "contacto@propios.com", "Otro", "admin@propios.com", CLAVE).id();
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);

        ConfiguracionTiendaResponse cambiada = configuracionTiendaService.editar(empresaId, adminId,
                antes.politicaEstructura(), null, parametros(null, 77), antes.version());

        assertThat(cambiada.simulacion().tasaRechazoPagos()).isEqualTo(77);
        assertThat(configuracionTiendaService.obtener(otraId).simulacion().tasaRechazoPagos()).isEqualTo(10);
        devolverLosParametros(cambiada.version());
    }

    private void devolverLosParametros(Long version) {
        configuracionTiendaService.editar(empresaId, adminId,
                configuracionTiendaService.obtener(empresaId).politicaEstructura(), null,
                ParametrosSimulacion.builder().build(), version);
    }

    private static ParametrosSimulacion parametros(String regla, int tasaRechazo) {
        return ParametrosSimulacion.builder()
                .semilla(7L)
                .tasaRechazoPagos(tasaRechazo)
                .reglaRechazoPagos(regla)
                .build();
    }

    @Test
    @DisplayName("La politica de una tienda no alcanza a las demas")
    void laPolitica_esDeCadaTienda() {
        Long otraId = empresaService.registrar("Otra tienda con politica", "900383940-2", "contacto@otra2.com",
                "Otro", "admin@otra2.com", CLAVE).id();
        reservar(PoliticaEstructura.SOLO_ADMINISTRADOR);

        assertThat(configuracionTiendaService.obtener(otraId).politicaEstructura())
                .isEqualTo(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
        reservar(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
    }

    private void reservar(PoliticaEstructura politica) {
        configuracionTiendaService.editar(empresaId, adminId, politica, null, null,
                configuracionTiendaService.obtener(empresaId).version());
    }

    private void crearPoolConLane(String proceso, Long autorId) {
        Long procesoId = procesoService.crear(empresaId, adminId, proceso, "Proceso de la tienda", "Operations").id();
        Long poolId = poolService.crear(empresaId, autorId, procesoId, "Customer", TipoParticipante.CLIENTE, true,
                Integracion.CLIENTE).id();
        Long rolId = rolProcesoService.crear(empresaId, adminId, "Ventas de " + proceso, null).id();
        laneService.crear(empresaId, autorId, poolService.listarPorProceso(empresaId, procesoId).getFirst().id(),
                "Ventas", rolId);
        assertThat(poolId).isNotNull();
    }
}
