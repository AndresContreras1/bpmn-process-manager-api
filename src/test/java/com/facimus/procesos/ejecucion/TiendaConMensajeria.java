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
 * El proceso de la demo hasta donde llega la mensajeria, publicado y listo para correr: un pedido entra por
 * mensaje, ventas lo revisa, se pide la autorizacion del pago y el caso se queda esperando la respuesta de la
 * pasarela.
 *
 * <p>Lo comparten las pruebas de mensajeria y las de simulacion porque necesitan el mismo diagrama exacto: una
 * manda los mensajes a mano y la otra deja que los entregue el reloj, y las dos tienen que llegar al mismo sitio.
 */
class TiendaConMensajeria {

    static final String CLIENTE = "Customer";
    static final String PASARELA = "Payment gateway";
    static final String PEDIDO = "Order placed";
    static final String AUTORIZACION = "Payment authorization request";
    static final String RESULTADO = "Payment authorization result";
    static final String REVISAR = "Receive order";
    static final String ESPERAR_PAGO = "Payment result received";

    private final AtomicInteger contador = new AtomicInteger();

    private final ProcesoService procesoService;
    private final RolProcesoService rolProcesoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final EventoService eventoService;
    private final ActividadService actividadService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    TiendaConMensajeria(ApplicationContext contexto) {
        this.procesoService = contexto.getBean(ProcesoService.class);
        this.rolProcesoService = contexto.getBean(RolProcesoService.class);
        this.poolService = contexto.getBean(PoolService.class);
        this.laneService = contexto.getBean(LaneService.class);
        this.eventoService = contexto.getBean(EventoService.class);
        this.actividadService = contexto.getBean(ActividadService.class);
        this.arcoService = contexto.getBean(ArcoService.class);
        this.mensajeService = contexto.getBean(MensajeService.class);
        this.correlacionService = contexto.getBean(CorrelacionService.class);
    }

    /** Publica una copia del proceso, con nombres propios para que dos pruebas no se pisen. */
    Long publicar(Long empresaId, Long autorId, String nombre) {
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
