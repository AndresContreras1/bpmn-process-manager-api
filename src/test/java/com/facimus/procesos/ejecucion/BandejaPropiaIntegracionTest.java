package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.RolDeUsuarioResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * D13: "mi bandeja". Un proceso con dos lanes deja una tarea en cada rol, y cada persona ve solo las de los roles a
 * los que pertenece. Quien no pertenece a ninguno no ve nada, y eso no le impide completar la tarea de otro: la
 * membresia filtra, no da permisos.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BandejaPropiaIntegracionTest {

    private static final String CLAVE = "clave12345";

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MembresiaRolService membresiaRolService;

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
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    private Long empresaId;
    private Long adminId;
    private Long vendedora;
    private Long bodeguero;
    private Long recienLlegada;
    private Long rolVentas;
    private Long rolBodega;

    @BeforeAll
    void unaTiendaConDosRolesYTresPersonas() {
        empresaId = empresaService.registrar("Tienda de bandejas", "900444333-1", "contacto@bandejas.com",
                "Administradora", "admin@bandejas.com", CLAVE).id();
        adminId = usuarioRepository.findByEmail("admin@bandejas.com").orElseThrow().getId();
        vendedora = usuarioService.crearColaborador(empresaId, adminId, "Vendedora", "ventas@bandejas.com", CLAVE,
                RolAcceso.EDITOR).id();
        bodeguero = usuarioService.crearColaborador(empresaId, adminId, "Bodeguero", "bodega@bandejas.com", CLAVE,
                RolAcceso.EDITOR).id();
        recienLlegada = usuarioService.crearColaborador(empresaId, adminId, "Recien llegada", "nueva@bandejas.com",
                CLAVE, RolAcceso.EDITOR).id();

        rolVentas = rolProcesoService.crear(empresaId, adminId, "Sales", null).id();
        rolBodega = rolProcesoService.crear(empresaId, adminId, "Warehouse", null).id();
        membresiaRolService.reemplazar(empresaId, adminId, vendedora, List.of(rolVentas));
        membresiaRolService.reemplazar(empresaId, adminId, bodeguero, List.of(rolBodega));

        Long procesoId = publicarUnProcesoConDosLanes();
        casoService.abrir(empresaId, adminId, procesoId, "ORD-1", Map.of());
    }

    @Test
    @DisplayName("Cada persona ve en su bandeja solo las tareas de sus roles")
    void bandejaPropia_soloLoDeSusRoles() {
        assertThat(mias(vendedora)).extracting(TareaResponse::nodoNombre).containsExactly("Receive order");
        assertThat(mias(bodeguero)).extracting(TareaResponse::nodoNombre).containsExactly("Pick and pack items");
    }

    @Test
    @DisplayName("Quien no pertenece a ningun rol no tiene bandeja propia")
    void bandejaPropia_sinRoles_vacia() {
        assertThat(mias(recienLlegada)).isEmpty();
    }

    @Test
    @DisplayName("La bandeja de la tienda sigue trayendolo todo: la membresia filtra, no esconde")
    void bandejaDeLaTienda_lasTraeTodas() {
        assertThat(tareaService.bandeja(empresaId, recienLlegada, false, null, null, null, Paginacion.de(0, 10))
                .content())
                .extracting(TareaResponse::nodoNombre)
                .containsExactly("Receive order", "Pick and pack items");
    }

    @Test
    @DisplayName("Pertenecer a un rol no da permisos: quien no pertenece completa la tarea igual")
    void membresia_noDaPermisos() {
        TareaResponse deVentas = mias(vendedora).getFirst();

        TareaResponse completada = tareaService.completar(empresaId, bodeguero, deVentas.id(), Map.of());

        assertThat(completada.estado().name()).isEqualTo("COMPLETADA");
    }

    @Test
    @DisplayName("Reemplazar los roles cambia lo que esa persona ve al momento")
    void reemplazar_cambiaLaBandeja() {
        membresiaRolService.reemplazar(empresaId, adminId, recienLlegada, List.of(rolBodega));

        assertThat(mias(recienLlegada)).extracting(TareaResponse::nodoNombre)
                .containsExactly("Pick and pack items");

        membresiaRolService.reemplazar(empresaId, adminId, recienLlegada, List.of());
        assertThat(mias(recienLlegada)).isEmpty();
    }

    @Test
    @DisplayName("Los roles de una persona se leen y se reemplazan enteros")
    void rolesDe_seLeenYSeReemplazan() {
        assertThat(membresiaRolService.rolesDe(empresaId, vendedora)).extracting(RolDeUsuarioResponse::nombre)
                .containsExactly("Sales");

        membresiaRolService.reemplazar(empresaId, adminId, vendedora, List.of(rolVentas, rolBodega));

        assertThat(membresiaRolService.rolesDe(empresaId, vendedora)).extracting(RolDeUsuarioResponse::nombre)
                .containsExactly("Sales", "Warehouse");
        membresiaRolService.reemplazar(empresaId, adminId, vendedora, List.of(rolVentas));
    }

    @Test
    @DisplayName("Un rol retirado no se reparte")
    void reemplazar_conRolRetirado_noExiste() {
        Long retirado = rolProcesoService.crear(empresaId, adminId, "Temporal", null).id();
        rolProcesoService.eliminar(empresaId, adminId, retirado);

        assertThatThrownBy(() -> membresiaRolService.reemplazar(empresaId, adminId, recienLlegada,
                List.of(retirado)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Rol de proceso no encontrado.");
    }

    private List<TareaResponse> mias(Long usuarioId) {
        return tareaService.bandeja(empresaId, usuarioId, true, null, null, null, Paginacion.de(0, 10)).content();
    }

    /** Un inicio que abre las dos ramas a la vez, para que haya una tarea esperando en cada rol. */
    private Long publicarUnProcesoConDosLanes() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Dos bandejas",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long laneVentas = laneService.crear(empresaId, adminId, tienda, "Sales", rolVentas).id();
        Long laneBodega = laneService.crear(empresaId, adminId, tienda, "Warehouse", rolBodega).id();

        Long inicio = eventoService.crear(empresaId, adminId, laneVentas, "Order received", TipoEvento.INICIO,
                20, 80).id();
        Long recibir = actividadService.crear(empresaId, adminId, laneVentas, "Receive order", "Check the cart",
                TipoActividad.USUARIO, 160, 80).id();
        Long empacar = actividadService.crear(empresaId, adminId, laneBodega, "Pick and pack items",
                "Prepare the package", TipoActividad.USUARIO, 160, 200).id();
        Long ventasFin = eventoService.crear(empresaId, adminId, laneVentas, "Order accepted", TipoEvento.FIN,
                320, 80).id();
        Long bodegaFin = eventoService.crear(empresaId, adminId, laneBodega, "Order shipped", TipoEvento.FIN,
                320, 200).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, empacar));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, ventasFin));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(empacar, bodegaFin));

        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
        return procesoId;
    }
}
