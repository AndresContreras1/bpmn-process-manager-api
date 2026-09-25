package com.facimus.procesos.ejecucion;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.ApplicationContext;

import com.facimus.procesos.gestion.model.EstadoProceso;
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
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * El proceso de la demo hasta donde llega la mensajeria, publicado y listo para correr: un pedido entra por
 * mensaje, ventas lo revisa, se pide la autorizacion del pago y el caso se queda esperando la respuesta de la
 * pasarela.
 *
 * <p>Lo comparten las pruebas de mensajeria y las de simulacion porque necesitan el mismo diagrama exacto: una
 * manda los mensajes a mano y la otra deja que los entregue el reloj, y las dos tienen que llegar al mismo sitio.
 */
public class TiendaConMensajeria {

    public static final String CLIENTE = "Customer";
    public static final String PASARELA = "Payment gateway";
    public static final String PEDIDO = "Order placed";
    public static final String AUTORIZACION = "Payment authorization request";
    public static final String RESULTADO = "Payment authorization result";
    public static final String REVISAR = "Receive order";
    public static final String ESPERAR_PAGO = "Payment result received";
    public static final String TRANSPORTISTA = "Carrier";
    public static final String CONFIRMACION = "Shipment confirmation";
    public static final String ENVIO = "Shipment request";
    public static final String AVISO = "Order status notification";
    public static final String DECIDIR = "Payment approved?";
    public static final String CANCELAR = "Cancel order";
    public static final String EMPACAR = "Pick and pack items";
    public static final String ESPERAR_ENVIO = "Shipment confirmed";
    public static final String RECHAZADO = "payment.status == DECLINED";

    private final AtomicInteger contador = new AtomicInteger();

    private final ProcesoService procesoService;
    private final RolProcesoService rolProcesoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final EventoService eventoService;
    private final GatewayService gatewayService;
    private final ActividadService actividadService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    public TiendaConMensajeria(ApplicationContext contexto) {
        this.procesoService = contexto.getBean(ProcesoService.class);
        this.rolProcesoService = contexto.getBean(RolProcesoService.class);
        this.poolService = contexto.getBean(PoolService.class);
        this.laneService = contexto.getBean(LaneService.class);
        this.eventoService = contexto.getBean(EventoService.class);
        this.gatewayService = contexto.getBean(GatewayService.class);
        this.actividadService = contexto.getBean(ActividadService.class);
        this.arcoService = contexto.getBean(ArcoService.class);
        this.mensajeService = contexto.getBean(MensajeService.class);
        this.correlacionService = contexto.getBean(CorrelacionService.class);
    }

    /**
     * El proceso de la demo entero: el pedido entra por mensaje, ventas lo revisa, se pide la autorizacion del
     * pago, el gateway decide con la respuesta, bodega empaca, se manda el envio y el transportista confirma.
     *
     * <p>El gateway se lee al reves de lo que uno diria: la condicion es la del rechazo y empacar es la salida por
     * defecto. Es a proposito y es lo que el negocio quiere decir, "solo si la pasarela dice que lo rechazo se
     * cancela"; y ademas es lo unico honesto mientras el socio sea un eco que contesta con el cuerpo vacio.
     */
    public Long publicarLaDemo(Long empresaId, Long autorId, String nombre) {
        int numero = contador.incrementAndGet();
        Long procesoId = procesoService.crear(empresaId, autorId, nombre + " " + numero,
                "De la compra a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(empresaId, autorId, procesoId, CLIENTE, TipoParticipante.CLIENTE, true,
                Integracion.CLIENTE).id();
        Long pasarela = poolService.crear(empresaId, autorId, procesoId, PASARELA,
                TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS).id();
        Long transportista = poolService.crear(empresaId, autorId, procesoId, TRANSPORTISTA,
                TipoParticipante.PROVEEDOR, true, Integracion.TRANSPORTE).id();
        Long ventas = rolProcesoService.crear(empresaId, autorId, "Sales " + numero, null).id();
        Long bodega = rolProcesoService.crear(empresaId, autorId, "Warehouse " + numero, null).id();
        Long laneVentas = laneService.crear(empresaId, autorId, tienda, "Sales", ventas).id();
        Long laneBodega = laneService.crear(empresaId, autorId, tienda, "Warehouse", bodega).id();

        Long inicio = eventoService.crear(empresaId, autorId, laneVentas, "Order received",
                TipoEvento.MENSAJE_INICIO, 20, 80).id();
        Long revisar = actividadService.crear(empresaId, autorId, laneVentas, REVISAR,
                "Validate the cart, the stock and the shipping address.", TipoActividad.USUARIO, 160, 80).id();
        Long pedirPago = actividadService.crear(empresaId, autorId, laneVentas, "Request payment authorization",
                "Ask the gateway to authorize the payment.", TipoActividad.ENVIO, 320, 80).id();
        Long esperarPago = eventoService.crear(empresaId, autorId, laneVentas, ESPERAR_PAGO,
                TipoEvento.MENSAJE_INTERMEDIO, 480, 80).id();
        Long decidir = gatewayService.crear(empresaId, autorId, laneVentas, DECIDIR, TipoGateway.EXCLUSIVO,
                640, 80).id();
        Long cancelar = actividadService.crear(empresaId, autorId, laneVentas, CANCELAR,
                "Release the reserved stock and notify the customer.", TipoActividad.SERVICIO, 800, 20).id();
        Long cancelado = eventoService.crear(empresaId, autorId, laneVentas, "Order cancelled", TipoEvento.FIN,
                960, 20).id();
        Long empacar = actividadService.crear(empresaId, autorId, laneBodega, EMPACAR,
                "Collect the items and prepare the package.", TipoActividad.USUARIO, 800, 200).id();
        Long enviar = actividadService.crear(empresaId, autorId, laneBodega, "Ship order",
                "Hand the package over to the carrier.", TipoActividad.ENVIO, 960, 200).id();
        Long esperarEnvio = eventoService.crear(empresaId, autorId, laneBodega, ESPERAR_ENVIO,
                TipoEvento.MENSAJE_INTERMEDIO, 1120, 200).id();
        Long enviado = eventoService.crear(empresaId, autorId, laneBodega, "Order shipped", TipoEvento.FIN,
                1280, 200).id();

        arcoService.crear(empresaId, autorId, DatosDeArco.entre(inicio, revisar));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(revisar, pedirPago));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(pedirPago, esperarPago));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(esperarPago, decidir));
        arcoService.crear(empresaId, autorId,
                new DatosDeArco(decidir, cancelar, "Declined", RECHAZADO, false, 1));
        arcoService.crear(empresaId, autorId, new DatosDeArco(decidir, empacar, "Otherwise", null, true, 2));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(cancelar, cancelado));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(empacar, enviar));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(enviar, esperarEnvio));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(esperarEnvio, enviado));

        Long pedido = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(PEDIDO,
                "What the customer bought.", cliente, tienda, null, inicio, null, AccionSiFalla.CONTINUAR, null,
                true, List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO)), null, "order", null)).id();
        Long respuestaPago = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(RESULTADO,
                "Whether the payment went through.", pasarela, tienda, null, esperarPago, null,
                AccionSiFalla.CONTINUAR, null, false,
                List.of(new CampoDeMensaje("status", TipoDeDato.TEXTO)), null, "payment", null)).id();
        Long peticionPago = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(AUTORIZACION,
                "Order total and tokenized card.", tienda, pasarela, pedirPago, null, TipoDestino.SERVICIO_WEB,
                AccionSiFalla.MANEJAR_ERROR, cancelar, false,
                List.of(new CampoDeMensaje("order.orderId", TipoDeDato.TEXTO)), null, null, respuestaPago)).id();
        Long confirmacion = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(CONFIRMACION,
                "The package is on its way.", transportista, tienda, null, esperarEnvio, null,
                AccionSiFalla.CONTINUAR, null, true,
                List.of(new CampoDeMensaje("trackingNumber", TipoDeDato.TEXTO)), null, "shipment", null)).id();
        // El envio no se contesta en el acto: el transportista recoge el paquete y confirma la entrega despues,
        // con el mensaje que ese participante manda por su cuenta (origen externo).
        Long peticionEnvio = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(ENVIO,
                "Address and package weight.", tienda, transportista, enviar, null, TipoDestino.COLA,
                AccionSiFalla.CONTINUAR, null, false,
                List.of(new CampoDeMensaje("order.orderId", TipoDeDato.TEXTO)), null, null, null)).id();
        Long aviso = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(AVISO,
                "Why the order could not go through.", tienda, cliente, cancelar, null, TipoDestino.CORREO,
                AccionSiFalla.CONTINUAR, null, false, List.of(), null, null, null)).id();

        for (Long mensaje : List.of(respuestaPago, peticionPago, confirmacion, peticionEnvio, aviso)) {
            correlacionService.definir(empresaId, autorId, mensaje, "orderId", "orderId",
                    PoliticaSinCaso.DESCARTAR, null);
        }
        correlacionService.definir(empresaId, autorId, pedido, "orderId", "orderId",
                PoliticaSinCaso.INICIAR_CASO, null);

        procesoService.cambiarEstado(empresaId, procesoId, autorId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
        return procesoId;
    }

    /** Publica una copia del proceso, con nombres propios para que dos pruebas no se pisen. */
    public Long publicar(Long empresaId, Long autorId, String nombre) {
        int numero = contador.incrementAndGet();
        Long procesoId = procesoService.crear(empresaId, autorId, nombre + " " + numero,
                "De la compra a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long cliente = poolService.crear(empresaId, autorId, procesoId, CLIENTE, TipoParticipante.CLIENTE, true,
                Integracion.CLIENTE).id();
        Long pasarela = poolService.crear(empresaId, autorId, procesoId, PASARELA,
                TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS).id();
        Long ventas = rolProcesoService.crear(empresaId, autorId, "Sales " + numero, null).id();
        Long lane = laneService.crear(empresaId, autorId, tienda, "Sales", ventas).id();

        Long inicio = eventoService.crear(empresaId, autorId, lane, "Order received", TipoEvento.MENSAJE_INICIO,
                20, 80).id();
        Long revisar = actividadService.crear(empresaId, autorId, lane, REVISAR,
                "Validate the cart and the address.", TipoActividad.USUARIO, 160, 80).id();
        Long pedirPago = actividadService.crear(empresaId, autorId, lane, "Request payment authorization",
                "Ask the gateway to authorize the payment.", TipoActividad.ENVIO, 320, 80).id();
        Long esperarPago = eventoService.crear(empresaId, autorId, lane, ESPERAR_PAGO,
                TipoEvento.MENSAJE_INTERMEDIO, 480, 80).id();
        Long fin = eventoService.crear(empresaId, autorId, lane, "Order handled", TipoEvento.FIN, 640, 80).id();

        arcoService.crear(empresaId, autorId, DatosDeArco.entre(inicio, revisar));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(revisar, pedirPago));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(pedirPago, esperarPago));
        arcoService.crear(empresaId, autorId, DatosDeArco.entre(esperarPago, fin));

        Long pedido = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(PEDIDO,
                "What the customer bought.", cliente, tienda, null, inicio, null, AccionSiFalla.CONTINUAR, null,
                true, List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO)), null, "order", null)).id();
        Long respuesta = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(RESULTADO,
                "Whether the payment went through.", pasarela, tienda, null, esperarPago, null,
                AccionSiFalla.CONTINUAR, null, false,
                List.of(new CampoDeMensaje("status", TipoDeDato.TEXTO)), null, "payment", null)).id();
        Long peticion = mensajeService.crear(empresaId, autorId, procesoId, new DatosDeMensaje(AUTORIZACION,
                "Order total and tokenized card.", tienda, pasarela, pedirPago, null, TipoDestino.SERVICIO_WEB,
                AccionSiFalla.CONTINUAR, null, false,
                List.of(new CampoDeMensaje("order.orderId", TipoDeDato.TEXTO)), null, null, respuesta)).id();

        correlacionService.definir(empresaId, autorId, pedido, "orderId", "orderId", PoliticaSinCaso.INICIAR_CASO,
                null);
        correlacionService.definir(empresaId, autorId, respuesta, "orderId", "orderId", PoliticaSinCaso.DESCARTAR,
                null);
        correlacionService.definir(empresaId, autorId, peticion, "orderId", "orderId", PoliticaSinCaso.DESCARTAR,
                null);

        procesoService.cambiarEstado(empresaId, procesoId, autorId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
        return procesoId;
    }
}
