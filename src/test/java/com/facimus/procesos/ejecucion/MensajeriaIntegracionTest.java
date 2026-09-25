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
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoDeDato;
import com.facimus.procesos.modelado.model.TipoDestino;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * Los cuatro finales de la correlacion sobre un proceso de verdad: un mensaje abre un pedido, otro se entrega al
 * caso que lo esperaba, otro se queda en la bandeja porque todavia nadie lo espera, y otro se descarta. Y lo que
 * pasa cuando el mismo mensaje llega dos veces.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MensajeriaIntegracionTest {

    private static final String CLIENTE = "Customer";
    private static final String PASARELA = "Payment gateway";
    private static final String PEDIDO = "Order placed";
    private static final String AUTORIZACION = "Payment authorization request";
    private static final String RESULTADO = "Payment authorization result";

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
    private MensajeService mensajeService;

    @Autowired
    private CorrelacionService correlacionService;

    @Autowired
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    @Autowired
    private MensajeriaService mensajeriaService;

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        empresaId = empresaService.registrar("Tienda con mensajeria", "900666111-1", "contacto@mensajeria.com",
                "Administradora", "admin@mensajeria.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@mensajeria.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un pedido entra por mensaje: abre el caso, le pone su referencia y deja su cuerpo en variables")
    void mensajeDeInicio_abreElPedido() {
        Long procesoId = publicar("Order fulfillment by message");

        MensajeEntranteResponse entrante = recibir(procesoId, PEDIDO, null,
                Map.of("orderId", "ORD-100", "total", 150), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.CASO_NUEVO);
        assertThat(entrante.clave()).isEqualTo("ORD-100");
        assertThat(entrante.repetido()).isFalse();
        CasoDetalleResponse caso = casoService.obtener(empresaId, entrante.casoId());
        assertThat(caso.caso().referencia()).isEqualTo("ORD-100");
        assertThat(caso.caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
        assertThat(caso.variables()).containsKey("order");
        assertThat(caso.pasos()).extracting(PasoDelCasoResponse::nodoNombre).containsExactly("Order received",
                "Receive order");
        assertThat(tiposDeLaBitacora(entrante.casoId())).startsWith(TipoEventoCaso.MENSAJE_RECIBIDO,
                TipoEventoCaso.CASO_ABIERTO);
    }

    @Test
    @DisplayName("La respuesta del socio se entrega al caso que la esperaba y el caso sigue")
    void respuesta_seEntregaAlCasoQueLaEsperaba() {
        Long procesoId = publicar("Order fulfillment waiting for the gateway");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-200");

        MensajeEntranteResponse entrante = recibir(procesoId, RESULTADO, null,
                Map.of("orderId", "ORD-200", "status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.ENTREGADO_A_CASO);
        assertThat(entrante.casoId()).isEqualTo(casoId);
        CasoDetalleResponse caso = casoService.obtener(empresaId, casoId);
        assertThat(caso.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(caso.variables()).extractingByKey("payment").asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry("status", "APPROVED");
        assertThat(tiposDeLaBitacora(casoId)).contains(TipoEventoCaso.MENSAJE_RECIBIDO);
    }

    @Test
    @DisplayName("Un mensaje sin clave no se entrega a ningun caso, por mucho que se llame igual")
    void sinClave_noSeEntregaANingunCaso() {
        Long procesoId = publicar("Order fulfillment with a keyless answer");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-300");

        MensajeEntranteResponse entrante = recibir(procesoId, RESULTADO, null, Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        assertThat(entrante.casoId()).isNull();
        assertThat(casoService.obtener(empresaId, casoId).caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
    }

    @Test
    @DisplayName("Una clave que no es de ningun caso de este proceso se descarta, escrita en la bandeja")
    void claveDesconocida_seDescarta() {
        Long procesoId = publicar("Order fulfillment with an unknown key");
        unPedidoEsperandoLaPasarela(procesoId, "ORD-400");

        MensajeEntranteResponse entrante = recibir(procesoId, RESULTADO, "ORD-999",
                Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        assertThat(mensajeriaService.bandejaDeEntrada(empresaId, procesoId, ResultadoCorrelacion.DESCARTADO,
                Paginacion.de(0, 10)).content()).extracting(MensajeEntranteResponse::clave).contains("ORD-999");
    }

    @Test
    @DisplayName("Un mensaje que llega antes de que nadie lo espere se queda en la bandeja en espera")
    void mensajeAdelantado_seQuedaEnEspera() {
        Long procesoId = publicar("Order fulfillment with an early answer");
        // El caso acaba de abrirse: esta en la tarea de ventas, todavia lejos de esperar a la pasarela.
        MensajeEntranteResponse pedido = recibir(procesoId, PEDIDO, null, Map.of("orderId", "ORD-500"), null);

        MensajeEntranteResponse entrante = recibir(procesoId, RESULTADO, "ORD-500",
                Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.EN_ESPERA);
        assertThat(entrante.casoId()).isEqualTo(pedido.casoId());
        assertThat(casoService.obtener(empresaId, pedido.casoId()).caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
    }

    @Test
    @DisplayName("El mismo mensaje mandado dos veces con la misma clave externa no se procesa dos veces")
    void claveExternaRepetida_noSeProcesaDosVeces() {
        Long procesoId = publicar("Order fulfillment sent twice");

        MensajeEntranteResponse primera = recibir(procesoId, PEDIDO, null, Map.of("orderId", "ORD-600"),
                "webhook-600");
        MensajeEntranteResponse segunda = recibir(procesoId, PEDIDO, null, Map.of("orderId", "ORD-600"),
                "webhook-600");

        assertThat(primera.repetido()).isFalse();
        assertThat(segunda.repetido()).isTrue();
        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(segunda.casoId()).isEqualTo(primera.casoId());
        assertThat(casoService.listar(empresaId, procesoId, null, "ORD-600", Paginacion.de(0, 10)).content())
                .hasSize(1);
    }

    @Test
    @DisplayName("La misma clave externa en otra tienda entra normalmente: es unica dentro de cada una")
    void claveExterna_deOtraTienda_noSeConfunde() {
        Long procesoId = publicar("Order fulfillment with a shared external key");
        recibir(procesoId, PEDIDO, null, Map.of("orderId", "ORD-650"), "webhook-650");

        Long otraId = empresaService.registrar("Tienda vecina con mensajeria", "900666111-2",
                "contacto@vecina.com", "Otro", "admin@vecina.com", "clave12345").id();
        Long otroAdmin = usuarioRepository.findByEmail("admin@vecina.com").orElseThrow().getId();
        Long suProceso = publicarEn(otraId, otroAdmin, "Order fulfillment next door");

        MensajeEntranteResponse suyo = mensajeriaService.recibir(otraId, suProceso,
                DatosDelEntrante.aMano(PEDIDO, null, Map.of("orderId", "ORD-650"), "webhook-650"));

        assertThat(suyo.repetido()).isFalse();
        assertThat(suyo.resultado()).isEqualTo(ResultadoCorrelacion.CASO_NUEVO);
    }

    @Test
    @DisplayName("Un mensaje que la tienda manda no se puede recibir, aunque exista con ese nombre")
    void mensajeQueLaTiendaManda_noSeRecibe() {
        Long procesoId = publicar("Order fulfillment with an outgoing name");

        assertThatThrownBy(() -> recibir(procesoId, AUTORIZACION, "ORD-700", Map.of(), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no recibe ningún mensaje llamado");
    }

    @Test
    @DisplayName("Un nombre que no es de ningun mensaje de la version publicada no entra")
    void mensajeDesconocido_esUnaReglaDeNegocio() {
        Long procesoId = publicar("Order fulfillment with an unknown message");

        assertThatThrownBy(() -> recibir(procesoId, "Refund requested", "ORD-800", Map.of(), null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Refund requested");
    }

    @Test
    @DisplayName("El envio del caso queda en la bandeja de salida con su clave y su cuerpo")
    void elEnvio_quedaEnLaBandejaDeSalida() {
        Long procesoId = publicar("Order fulfillment with an outbox");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-900");

        List<MensajeSalienteResponse> salida = mensajeriaService.salientesDelCaso(empresaId, casoId);

        assertThat(salida).singleElement()
                .returns(AUTORIZACION, MensajeSalienteResponse::nombre)
                .returns(PASARELA, MensajeSalienteResponse::poolDestinoNombre)
                .returns(Integracion.PAGOS, MensajeSalienteResponse::integracion)
                .returns("ORD-900", MensajeSalienteResponse::clave)
                .returns(EstadoMensajeSaliente.PENDIENTE, MensajeSalienteResponse::estado);
        assertThat(salida.getFirst().cuerpo()).containsEntry("orderId", "ORD-900");
        assertThat(mensajeriaService.bandejaDeSalida(empresaId, procesoId, EstadoMensajeSaliente.PENDIENTE,
                Paginacion.de(0, 50)).content()).extracting(MensajeSalienteResponse::nombre).contains(AUTORIZACION);
    }

    /** Un pedido abierto por mensaje al que se le completa la tarea de ventas: se queda esperando a la pasarela. */
    private Long unPedidoEsperandoLaPasarela(Long procesoId, String referencia) {
        Long casoId = recibir(procesoId, PEDIDO, null, Map.of("orderId", referencia), null).casoId();
        TareaResponse tarea = tareaService.bandeja(empresaId, adminId, false, null, procesoId, null,
                Paginacion.de(0, 10)).content().stream()
                .filter(pendiente -> pendiente.casoId().equals(casoId))
                .findFirst().orElseThrow();
        tareaService.completar(empresaId, adminId, tarea.id(), null);
        assertThat(casoService.obtener(empresaId, casoId).pasos())
                .filteredOn(paso -> paso.nodoNombre().equals("Payment result received"))
                .singleElement().returns(EstadoActividadCaso.EN_ESPERA, PasoDelCasoResponse::estado);
        return casoId;
    }

    private MensajeEntranteResponse recibir(Long procesoId, String nombre, String clave,
            Map<String, Object> cuerpo, String claveExterna) {
        return mensajeriaService.recibir(empresaId, procesoId,
                DatosDelEntrante.aMano(nombre, clave, cuerpo, claveExterna));
    }

    private Long publicar(String nombre) {
        return publicarEn(empresaId, adminId, nombre);
    }

    /**
     * El pedido de la demo hasta donde llega la mensajeria: entra por mensaje, ventas lo revisa, se pide la
     * autorizacion del pago y el caso espera la respuesta.
     */
    private Long publicarEn(Long tiendaId, Long autorId, String nombre) {
        int numero = contador.incrementAndGet();
        Long procesoId = procesoService.crear(tiendaId, autorId, nombre + " " + numero,
                "De la compra a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(tiendaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(tiendaId, autorId, procesoId, CLIENTE, TipoParticipante.CLIENTE, true,
                Integracion.CLIENTE).id();
        Long pasarela = poolService.crear(tiendaId, autorId, procesoId, PASARELA,
                TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS).id();
        Long ventas = rolProcesoService.crear(tiendaId, autorId, "Sales " + numero, null).id();
        Long lane = laneService.crear(tiendaId, autorId, tienda, "Sales", ventas).id();

        Long inicio = eventoService.crear(tiendaId, autorId, lane, "Order received", TipoEvento.MENSAJE_INICIO,
                20, 80).id();
        Long recibir = actividadService.crear(tiendaId, autorId, lane, "Receive order",
                "Validate the cart and the address.", TipoActividad.USUARIO, 160, 80).id();
        Long pedirPago = actividadService.crear(tiendaId, autorId, lane, "Request payment authorization",
                "Ask the gateway to authorize the payment.", TipoActividad.ENVIO, 320, 80).id();
        Long esperarPago = eventoService.crear(tiendaId, autorId, lane, "Payment result received",
                TipoEvento.MENSAJE_INTERMEDIO, 480, 80).id();
        Long fin = eventoService.crear(tiendaId, autorId, lane, "Order handled", TipoEvento.FIN, 640, 80).id();

        arcoService.crear(tiendaId, autorId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(tiendaId, autorId, DatosDeArco.entre(recibir, pedirPago));
        arcoService.crear(tiendaId, autorId, DatosDeArco.entre(pedirPago, esperarPago));
        arcoService.crear(tiendaId, autorId, DatosDeArco.entre(esperarPago, fin));

        Long pedido = mensajeService.crear(tiendaId, autorId, procesoId, new DatosDeMensaje(PEDIDO,
                "What the customer bought.", cliente, tienda, null, inicio, null, AccionSiFalla.CONTINUAR, null,
                true, List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO)), null, "order", null)).id();
        Long respuesta = mensajeService.crear(tiendaId, autorId, procesoId, new DatosDeMensaje(RESULTADO,
                "Whether the payment went through.", pasarela, tienda, null, esperarPago, null,
                AccionSiFalla.CONTINUAR, null, false,
                List.of(new CampoDeMensaje("status", TipoDeDato.TEXTO)), null, "payment", null)).id();
        Long peticion = mensajeService.crear(tiendaId, autorId, procesoId, new DatosDeMensaje(AUTORIZACION,
                "Order total and tokenized card.", tienda, pasarela, pedirPago, null, TipoDestino.SERVICIO_WEB,
                AccionSiFalla.CONTINUAR, null, false,
                List.of(new CampoDeMensaje("order.orderId", TipoDeDato.TEXTO)), null, null, respuesta)).id();

        correlacionService.definir(tiendaId, autorId, pedido, "orderId", "orderId", PoliticaSinCaso.INICIAR_CASO,
                null);
        correlacionService.definir(tiendaId, autorId, respuesta, "orderId", "orderId", PoliticaSinCaso.DESCARTAR,
                null);
        correlacionService.definir(tiendaId, autorId, peticion, "orderId", "orderId", PoliticaSinCaso.DESCARTAR,
                null);

        procesoService.cambiarEstado(tiendaId, procesoId, autorId, EstadoProceso.PUBLICADO,
                procesoService.obtener(tiendaId, procesoId, false).version());
        return procesoId;
    }

    private List<TipoEventoCaso> tiposDeLaBitacora(Long casoId) {
        return casoService.eventos(empresaId, casoId).stream().map(EventoCasoResponse::tipo).toList();
    }
}
