package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.service.GatewayService;

/**
 * Un pedido de punta a punta sobre una version publicada: se abre, la tarea aparece en la bandeja de su rol, el
 * gateway decide con las variables del caso, la siguiente tarea aparece en la otra bandeja y el caso termina.
 *
 * <p>El proceso es el de la demo hasta donde este PR llega: las actividades que hablan con un socio son de servicio
 * y no de envio, porque mientras no haya mensajeria un envio no tendria a quien mandarle nada. El PR de la
 * mensajeria las convierte y anade la mitad que falta.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EjecucionIntegracionTest {

    private static final String APROBADO = "payment.status == APPROVED";
    private static final String RECHAZADO = "payment.status == DECLINED";

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
    private GatewayService gatewayService;

    @Autowired
    private ArcoService arcoService;

    @Autowired
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        empresaId = empresaService.registrar("Tienda que ejecuta", "900555444-1", "contacto@ejecuta.com",
                "Administradora", "admin@ejecuta.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@ejecuta.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un pedido aprobado pasa por las dos bandejas y termina enviado")
    void pedidoAprobado_terminaEnviado() {
        Proceso proceso = publicar("Order fulfillment");

        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-1",
                Map.of("payment", Map.of("status", "APPROVED")));

        assertThat(caso.estado()).isEqualTo(EstadoCaso.ABIERTO);
        assertThat(caso.versionNumero()).isEqualTo(1);
        assertThat(bandejaDe(proceso.ventas())).extracting(TareaResponse::nodoNombre)
                .containsExactly("Receive order");

        completarLaTareaDe(proceso.ventas());

        assertThat(bandejaDe(proceso.ventas())).isEmpty();
        assertThat(bandejaDe(proceso.bodega())).extracting(TareaResponse::nodoNombre)
                .containsExactly("Pick and pack items");

        completarLaTareaDe(proceso.bodega());

        CasoDetalleResponse terminado = casoService.obtener(empresaId, caso.id());
        assertThat(terminado.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(terminado.caso().fechaFin()).isNotNull();
        assertThat(terminado.pasos()).extracting(PasoDelCasoResponse::nodoNombre)
                .containsExactly("Order received", "Receive order", "Payment approved?", "Pick and pack items",
                        "Ship order", "Order shipped");
        assertThat(terminado.pasos()).extracting(PasoDelCasoResponse::estado)
                .containsOnly(EstadoActividadCaso.COMPLETADA);
    }

    @Test
    @DisplayName("Un pedido rechazado se va por la otra rama y termina cancelado")
    void pedidoRechazado_terminaCancelado() {
        Proceso proceso = publicar("Order fulfillment with a declined payment");

        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-2",
                Map.of("payment", Map.of("status", "DECLINED")));
        completarLaTareaDe(proceso.ventas());

        CasoDetalleResponse terminado = casoService.obtener(empresaId, caso.id());
        assertThat(terminado.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(terminado.pasos()).extracting(PasoDelCasoResponse::nodoNombre)
                .containsExactly("Order received", "Receive order", "Payment approved?", "Cancel order",
                        "Order cancelled");
        assertThat(bandejaDe(proceso.bodega())).isEmpty();
    }

    @Test
    @DisplayName("Sin la variable que el gateway pregunta, el caso queda en error, se corrige y se reintenta")
    void sinLaVariable_quedaEnErrorYSeRescata() {
        Proceso proceso = publicar("Order fulfillment without the payment result");

        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-3", null);
        completarLaTareaDe(proceso.ventas());

        CasoResponse enError = casoService.obtener(empresaId, caso.id()).caso();
        assertThat(enError.estado()).isEqualTo(EstadoCaso.ERROR);
        assertThat(tiposDeLaBitacora(caso.id()))
                .contains(TipoEventoCaso.VARIABLE_AUSENTE, TipoEventoCaso.SIN_CAMINO);
        assertThat(casoService.eventos(empresaId, caso.id())).extracting(EventoCasoResponse::detalle)
                .anyMatch(detalle -> detalle.contains("payment.status"));

        casoService.corregirVariables(empresaId, caso.id(), Map.of("payment", Map.of("status", "DECLINED")),
                enError.version());
        casoService.reintentar(empresaId, adminId, caso.id());

        CasoDetalleResponse rescatado = casoService.obtener(empresaId, caso.id());
        assertThat(rescatado.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(rescatado.pasos()).extracting(PasoDelCasoResponse::nodoNombre).contains("Cancel order");
    }

    @Test
    @DisplayName("Cancelar un caso apaga los tokens que seguian vivos")
    void cancelar_apagaLosTokensVivos() {
        Proceso proceso = publicar("Order fulfillment cancelled halfway");
        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-4", Map.of());

        casoService.cancelar(empresaId, adminId, caso.id());

        CasoDetalleResponse cancelado = casoService.obtener(empresaId, caso.id());
        assertThat(cancelado.caso().estado()).isEqualTo(EstadoCaso.CANCELADO);
        assertThat(cancelado.pasos()).filteredOn(paso -> paso.nodoNombre().equals("Receive order"))
                .extracting(PasoDelCasoResponse::estado).containsExactly(EstadoActividadCaso.OMITIDA);
        assertThat(tiposDeLaBitacora(caso.id())).contains(TipoEventoCaso.CASO_CANCELADO);
        assertThat(bandejaDe(proceso.ventas())).isEmpty();
    }

    @Test
    @DisplayName("Un caso abierto sigue corriendo su version aunque se publique otra encima")
    void casoAbierto_sigueConSuVersion() {
        Proceso proceso = publicar("Order fulfillment with a second version");
        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-5",
                Map.of("payment", Map.of("status", "APPROVED")));

        renombrar(proceso, "Ship order", "Hand the package to the carrier");
        ProcesoResponse segunda = procesoService.cambiarEstado(empresaId, proceso.id(), adminId,
                EstadoProceso.PUBLICADO, procesoService.obtener(empresaId, proceso.id(), false).version());
        assertThat(segunda.versionPublicada()).isEqualTo(2);

        completarLaTareaDe(proceso.ventas());
        completarLaTareaDe(proceso.bodega());

        CasoDetalleResponse terminado = casoService.obtener(empresaId, caso.id());
        assertThat(terminado.caso().versionNumero()).isEqualTo(1);
        assertThat(terminado.pasos()).extracting(PasoDelCasoResponse::nodoNombre)
                .contains("Ship order").doesNotContain("Hand the package to the carrier");
    }

    @Test
    @DisplayName("La linea de tiempo cuenta lo que paso, en el orden en que paso")
    void lineaDeTiempo_cuentaLoQuePaso() {
        Proceso proceso = publicar("Order fulfillment with a timeline");
        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-6",
                Map.of("payment", Map.of("status", "APPROVED")));
        completarLaTareaDe(proceso.ventas());

        assertThat(tiposDeLaBitacora(caso.id())).startsWith(TipoEventoCaso.CASO_ABIERTO,
                TipoEventoCaso.NODO_ACTIVADO, TipoEventoCaso.TAREA_CREADA, TipoEventoCaso.TAREA_COMPLETADA,
                TipoEventoCaso.GATEWAY_DECIDIO, TipoEventoCaso.TAREA_CREADA);
        assertThat(casoService.eventos(empresaId, caso.id()))
                .filteredOn(evento -> evento.tipo() == TipoEventoCaso.GATEWAY_DECIDIO)
                .singleElement()
                .satisfies(evento -> assertThat(evento.detalle()).contains("Payment approved?").contains(APROBADO));
    }

    @Test
    @DisplayName("R-47: un proceso que no se ha publicado no abre casos")
    void abrir_procesoSinPublicar_esUnaReglaDeNegocio() {
        Long procesoId = procesoService.crear(empresaId, adminId, "Returns " + contador.incrementAndGet(),
                "Sin publicar", "Operations").id();

        assertThatThrownBy(() -> casoService.abrir(empresaId, adminId, procesoId, "ORD-7", Map.of()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El proceso no tiene una versión publicada vigente.");
    }

    @Test
    @DisplayName("Completar una tarea guarda sus datos en las variables del caso")
    void completar_conDatos_losGuardaEnLasVariables() {
        Proceso proceso = publicar("Order fulfillment with task data");
        CasoResponse caso = casoService.abrir(empresaId, adminId, proceso.id(), "ORD-8",
                Map.of("payment", Map.of("status", "APPROVED")));

        TareaResponse tarea = bandejaDe(proceso.ventas()).getFirst();
        tareaService.completar(empresaId, adminId, tarea.id(), Map.of("checked", true, "notes", "Todo en orden"));

        Map<String, Object> variables = casoService.obtener(empresaId, caso.id()).variables();
        assertThat(variables).extractingByKey("tarea").asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsKey("receiveOrder");
        assertThat(tareaService.obtener(empresaId, tarea.id()).datosSalida()).containsEntry("checked", true);
    }

    /** Los ids que una prueba necesita del proceso publicado: el proceso y los dos roles de sus lanes. */
    private record Proceso(Long id, Long ventas, Long bodega, Long laneVentas, Long laneBodega) {
    }

    /**
     * El proceso de la demo hasta donde llega este PR: un inicio, una tarea de ventas, el gateway del pago, la
     * tarea de bodega y las dos salidas. Se publica, porque un caso solo corre sobre una version.
     */
    private Proceso publicar(String nombre) {
        int numero = contador.incrementAndGet();
        Long procesoId = procesoService.crear(empresaId, adminId, nombre + " " + numero,
                "De la compra a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long ventas = rolProcesoService.crear(empresaId, adminId, "Sales " + numero, null).id();
        Long bodega = rolProcesoService.crear(empresaId, adminId, "Warehouse " + numero, null).id();
        Long laneVentas = laneService.crear(empresaId, adminId, tienda, "Sales", ventas).id();
        Long laneBodega = laneService.crear(empresaId, adminId, tienda, "Warehouse", bodega).id();

        Long inicio = eventoService.crear(empresaId, adminId, laneVentas, "Order received", TipoEvento.INICIO,
                20, 80).id();
        Long recibir = actividadService.crear(empresaId, adminId, laneVentas, "Receive order",
                "Validate the cart, the stock and the shipping address.", TipoActividad.USUARIO, 160, 80).id();
        Long decidir = gatewayService.crear(empresaId, adminId, laneVentas, "Payment approved?",
                TipoGateway.EXCLUSIVO, 320, 80).id();
        Long cancelar = actividadService.crear(empresaId, adminId, laneVentas, "Cancel order",
                "Release the reserved stock and notify the customer.", TipoActividad.SERVICIO, 480, 20).id();
        Long cancelado = eventoService.crear(empresaId, adminId, laneVentas, "Order cancelled", TipoEvento.FIN,
                640, 20).id();
        Long empacar = actividadService.crear(empresaId, adminId, laneBodega, "Pick and pack items",
                "Collect the items and prepare the package.", TipoActividad.USUARIO, 480, 200).id();
        Long enviar = actividadService.crear(empresaId, adminId, laneBodega, "Ship order",
                "Hand the package over to the carrier.", TipoActividad.SERVICIO, 640, 200).id();
        Long enviado = eventoService.crear(empresaId, adminId, laneBodega, "Order shipped", TipoEvento.FIN,
                800, 200).id();

        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, decidir));
        arcoService.crear(empresaId, adminId,
                new DatosDeArco(decidir, empacar, "Approved", APROBADO, false, 1));
        arcoService.crear(empresaId, adminId,
                new DatosDeArco(decidir, cancelar, "Declined", RECHAZADO, false, 2));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(empacar, enviar));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(enviar, enviado));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(cancelar, cancelado));

        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
        return new Proceso(procesoId, ventas, bodega, laneVentas, laneBodega);
    }

    private void renombrar(Proceso proceso, String actual, String nuevo) {
        var actividad = actividadService.listarPorLane(empresaId, proceso.laneBodega()).stream()
                .filter(paso -> paso.nombre().equals(actual))
                .findFirst().orElseThrow();
        actividadService.editar(empresaId, adminId, actividad.id(), nuevo, actividad.descripcion(),
                actividad.tipoActividad(), actividad.laneId(), actividad.posicionX(), actividad.posicionY(),
                actividad.version());
    }

    private void completarLaTareaDe(Long rolProcesoId) {
        TareaResponse tarea = bandejaDe(rolProcesoId).getFirst();
        tareaService.completar(empresaId, adminId, tarea.id(), null);
    }

    private List<TareaResponse> bandejaDe(Long rolProcesoId) {
        return tareaService.bandeja(empresaId, adminId, false, rolProcesoId, null, null, Paginacion.de(0, 10))
                .content();
    }

    private List<TipoEventoCaso> tiposDeLaBitacora(Long casoId) {
        return casoService.eventos(empresaId, casoId).stream().map(EventoCasoResponse::tipo).toList();
    }
}
