package com.facimus.procesos.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
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
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GatewayService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.service.PoolService;

import lombok.RequiredArgsConstructor;

/**
 * Siembra una tienda en linea de demostracion en el primer arranque, unicamente en el perfil dev y si la base
 * de datos esta vacia: la empresa, su administrador y dos procesos de e-commerce.
 * Todo pasa por los services, asi los datos demo cumplen las mismas reglas de negocio que la API.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DatosDemoInitializer implements CommandLineRunner {

    private static final String NIT_DEMO = "900123456-1";
    private static final String EMAIL_DEMO = "admin@demo.com";
    private static final String PASSWORD_DEMO = "admin123";
    private static final String CLAVE_DE_CORRELACION = "orderId";

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;
    private final ProcesoService procesoService;
    private final RolProcesoService rolProcesoService;
    private final PoolService poolService;
    private final LaneService laneService;
    private final ActividadService actividadService;
    private final GatewayService gatewayService;
    private final EventoService eventoService;
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }

        Long empresaId = empresaService.registrar("Demo Store", NIT_DEMO, "contacto@demo.com",
                "Administrador Demo", EMAIL_DEMO, PASSWORD_DEMO).id();
        Long adminId = usuarioRepository.findByEmpresaIdAndEmail(empresaId, EMAIL_DEMO).orElseThrow().getId();

        sembrarDespachoDePedidos(empresaId, adminId);
        procesoService.crear(empresaId, adminId, "Returns and refunds",
                "Return request, item inspection and refund to the original payment method.", "After-sales");
    }

    /**
     * Proceso publicado que usa todos los elementos BPMN: pools, lanes, eventos, actividades de cada tipo,
     * gateway, arcos y mensajes.
     */
    private void sembrarDespachoDePedidos(Long empresaId, Long adminId) {
        ProcesoResponse proceso = procesoService.crear(empresaId, adminId, "Order fulfillment",
                "From checkout to delivery: payment authorization, picking, packing and shipment.", "Fulfillment");
        Long procesoId = proceso.id();

        // El proceso nace con el pool de la tienda; los demas participantes se modelan como cajas negras.
        PoolResponse tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst();
        PoolResponse cliente = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE,
                true, Integracion.CLIENTE);
        PoolResponse pasarela = poolService.crear(empresaId, adminId, procesoId, "Payment gateway",
                TipoParticipante.SISTEMA_EXTERNO, true, Integracion.PAGOS);
        PoolResponse transportadora = poolService.crear(empresaId, adminId, procesoId, "Carrier",
                TipoParticipante.PROVEEDOR, true, Integracion.TRANSPORTE);

        Long ventas = laneService.crear(empresaId, adminId, tienda.id(), "Sales", rolProcesoService
                .crear(empresaId, "Sales", "Receives orders and coordinates the payment.").id()).id();
        Long bodega = laneService.crear(empresaId, adminId, tienda.id(), "Warehouse", rolProcesoService
                .crear(empresaId, "Warehouse", "Picks, packs and ships the orders.").id()).id();

        // El pedido entra por un evento de mensaje y cada camino termina en un evento de fin.
        Long pedidoRecibido = eventoService.crear(empresaId, adminId, ventas, "Order received",
                TipoEvento.MENSAJE_INICIO, 20, 80).id();
        Long recibir = actividadService.crear(empresaId, adminId, ventas, "Receive order",
                "Validate the cart, the stock and the shipping address.", TipoActividad.USUARIO, 120, 80).id();
        Long autorizar = actividadService.crear(empresaId, adminId, ventas, "Request payment authorization",
                "Send the order total to the payment gateway.", TipoActividad.ENVIO, 280, 80).id();
        Long respuestaDelPago = eventoService.crear(empresaId, adminId, ventas, "Payment result received",
                TipoEvento.MENSAJE_INTERMEDIO, 440, 80).id();
        Long pagoAprobado = gatewayService.crear(empresaId, adminId, ventas, "Payment approved?", TipoGateway.EXCLUSIVO,
                580, 80).id();
        Long cancelar = actividadService.crear(empresaId, adminId, ventas, "Cancel order",
                "Release the reserved stock and notify the customer.", TipoActividad.SERVICIO, 740, 20).id();
        Long pedidoCancelado = eventoService.crear(empresaId, adminId, ventas, "Order cancelled", TipoEvento.FIN,
                900, 20).id();
        Long empacar = actividadService.crear(empresaId, adminId, bodega, "Pick and pack items",
                "Collect the items and prepare the package.", TipoActividad.USUARIO, 740, 200).id();
        Long enviar = actividadService.crear(empresaId, adminId, bodega, "Ship order",
                "Hand the package over to the carrier.", TipoActividad.ENVIO, 900, 200).id();
        Long envioConfirmado = eventoService.crear(empresaId, adminId, bodega, "Shipment confirmed",
                TipoEvento.MENSAJE_INTERMEDIO, 1040, 200).id();
        Long pedidoEnviado = eventoService.crear(empresaId, adminId, bodega, "Order shipped", TipoEvento.FIN,
                1180, 200).id();

        arcoService.crear(empresaId, adminId, pedidoRecibido, recibir, null, null, false, 0);
        arcoService.crear(empresaId, adminId, recibir, autorizar, null, null, false, 0);
        arcoService.crear(empresaId, adminId, autorizar, respuestaDelPago, null, null, false, 0);
        arcoService.crear(empresaId, adminId, respuestaDelPago, pagoAprobado, null, null, false, 0);
        // Los arcos que salen de un gateway exclusivo llevan condicion (regla de ArcoService).
        arcoService.crear(empresaId, adminId, pagoAprobado, empacar, "Approved",
                "payment.status == APPROVED", false, 0);
        arcoService.crear(empresaId, adminId, pagoAprobado, cancelar, "Declined",
                "payment.status == DECLINED", false, 0);
        arcoService.crear(empresaId, adminId, cancelar, pedidoCancelado, null, null, false, 0);
        arcoService.crear(empresaId, adminId, empacar, enviar, null, null, false, 0);
        arcoService.crear(empresaId, adminId, enviar, envioConfirmado, null, null, false, 0);
        arcoService.crear(empresaId, adminId, envioConfirmado, pedidoEnviado, null, null, false, 0);

        // Cada mensaje se ancla al nodo que lo manda o lo espera. Las respuestas se crean antes que su peticion,
        // porque la peticion las nombra.
        Long pedidoPuesto = mensajeService.crear(empresaId, adminId, procesoId,
                new DatosDeMensaje("Order placed", "Cart items, shipping address and payment method.", cliente.id(),
                        tienda.id(), null, pedidoRecibido, null, null, null, false,
                        List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                                new CampoDeMensaje("items", TipoDeDato.TEXTO),
                                new CampoDeMensaje("total", TipoDeDato.NUMERO),
                                new CampoDeMensaje("shippingAddress", TipoDeDato.TEXTO)),
                        "Opens the case and feeds the picking list.", "order", null)).id();
        Long resultadoDelPago = mensajeService.crear(empresaId, adminId, procesoId,
                new DatosDeMensaje("Payment authorization result", "Approved or declined, with the transaction id.",
                        pasarela.id(), tienda.id(), null, respuestaDelPago, null, null, null, false,
                        List.of(new CampoDeMensaje("status", TipoDeDato.TEXTO),
                                new CampoDeMensaje("transactionId", TipoDeDato.TEXTO)),
                        "The gateway decides whether the order is picked or cancelled.", "payment", null)).id();
        Long peticionDePago = mensajeService.crear(empresaId, adminId, procesoId,
                new DatosDeMensaje("Payment authorization request", "Order total and tokenized card.", tienda.id(),
                        pasarela.id(), autorizar, null, TipoDestino.SERVICIO_WEB, AccionSiFalla.MANEJAR_ERROR,
                        cancelar, false,
                        List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                                new CampoDeMensaje("total", TipoDeDato.NUMERO)),
                        "If the gateway does not answer, the order is cancelled.", "paymentRequest",
                        resultadoDelPago)).id();
        Long envioConfirmadoMensaje = mensajeService.crear(empresaId, adminId, procesoId,
                new DatosDeMensaje("Shipment confirmation", "Tracking number and shipment status.",
                        transportadora.id(), tienda.id(), null, envioConfirmado, null, null, null, true,
                        List.of(new CampoDeMensaje("trackingNumber", TipoDeDato.TEXTO),
                                new CampoDeMensaje("status", TipoDeDato.TEXTO)),
                        "Closes the order with its tracking number.", "shipment", null)).id();
        Long peticionDeEnvio = mensajeService.crear(empresaId, adminId, procesoId,
                new DatosDeMensaje("Shipment request", "Package size, weight and delivery address.", tienda.id(),
                        transportadora.id(), enviar, null, TipoDestino.COLA, AccionSiFalla.CONTINUAR, null, false,
                        List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                                new CampoDeMensaje("weight", TipoDeDato.NUMERO)),
                        "The carrier picks the package up.", "shipmentRequest", envioConfirmadoMensaje)).id();
        Long avisoAlCliente = mensajeService.crear(empresaId, adminId, procesoId,
                new DatosDeMensaje("Order status notification",
                        "Confirmation with the tracking number, or the cancellation notice.", tienda.id(),
                        cliente.id(), cancelar, null, TipoDestino.CORREO, AccionSiFalla.CONTINUAR, null, false,
                        List.of(new CampoDeMensaje("orderId", TipoDeDato.TEXTO),
                                new CampoDeMensaje("status", TipoDeDato.TEXTO)),
                        "Tells the customer how the order ended.", "notification", null)).id();

        // El pedido del cliente abre el caso; los demas mensajes se cuelgan de uno ya abierto.
        correlacionar(empresaId, adminId, pedidoPuesto, PoliticaSinCaso.INICIAR_CASO);
        for (Long mensajeId : List.of(peticionDePago, resultadoDelPago, peticionDeEnvio, envioConfirmadoMensaje,
                avisoAlCliente)) {
            correlacionar(empresaId, adminId, mensajeId, PoliticaSinCaso.DESCARTAR);
        }

        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO, proceso.version());
    }

    /** Todos los mensajes del pedido se correlacionan por su numero de orden, que viaja en el campo orderId. */
    private void correlacionar(Long empresaId, Long adminId, Long mensajeId, PoliticaSinCaso sinCaso) {
        correlacionService.definir(empresaId, adminId, mensajeId, CLAVE_DE_CORRELACION, CLAVE_DE_CORRELACION,
                sinCaso, null);
    }
}
