package com.facimus.procesos.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.CorrelacionService;
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
    private final ArcoService arcoService;
    private final MensajeService mensajeService;
    private final CorrelacionService correlacionService;

    @Override
    public void run(String... args) {
        if (empresaRepository.count() > 0) {
            return;
        }

        Empresa tienda = empresaService.registrar("Demo Store", NIT_DEMO, "contacto@demo.com",
                "Administrador Demo", EMAIL_DEMO, PASSWORD_DEMO);
        Long empresaId = tienda.getId();
        Long adminId = usuarioRepository.findByEmpresaIdAndEmail(empresaId, EMAIL_DEMO).orElseThrow().getId();

        sembrarDespachoDePedidos(empresaId, adminId);
        procesoService.crear(empresaId, adminId, "Returns and refunds",
                "Return request, item inspection and refund to the original payment method.", "After-sales");
    }

    /** Proceso publicado que usa todos los elementos BPMN: pools, lanes, actividades, gateway, arcos y mensajes. */
    private void sembrarDespachoDePedidos(Long empresaId, Long adminId) {
        Long procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment",
                "From checkout to delivery: payment authorization, picking, packing and shipment.", "Fulfillment")
                .getId();

        // El proceso nace con el pool de la tienda; los demas participantes se modelan como cajas negras.
        Pool tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst();
        Pool cliente = poolService.crear(empresaId, procesoId, "Customer", TipoParticipante.CLIENTE, true);
        Pool pasarela = poolService.crear(empresaId, procesoId, "Payment gateway", TipoParticipante.SISTEMA_EXTERNO,
                true);
        Pool transportadora = poolService.crear(empresaId, procesoId, "Carrier", TipoParticipante.PROVEEDOR, true);

        Long ventas = laneService.crear(empresaId, tienda.getId(), "Sales", rolProcesoService
                .crear(empresaId, "Sales", "Receives orders and coordinates the payment.").getId()).getId();
        Long bodega = laneService.crear(empresaId, tienda.getId(), "Warehouse", rolProcesoService
                .crear(empresaId, "Warehouse", "Picks, packs and ships the orders.").getId()).getId();

        Long recibir = actividadService.crear(empresaId, ventas, "Receive order",
                "Validate the cart, the stock and the shipping address.", 100, 80).getId();
        Long autorizar = actividadService.crear(empresaId, ventas, "Request payment authorization",
                "Send the order total to the payment gateway.", 260, 80).getId();
        Long pagoAprobado = gatewayService.crear(empresaId, ventas, "Payment approved?", TipoGateway.EXCLUSIVO,
                420, 80).getId();
        Long cancelar = actividadService.crear(empresaId, ventas, "Cancel order",
                "Release the reserved stock and notify the customer.", 580, 40).getId();
        Long empacar = actividadService.crear(empresaId, bodega, "Pick and pack items",
                "Collect the items and prepare the package.", 580, 200).getId();
        Long enviar = actividadService.crear(empresaId, bodega, "Ship order",
                "Hand the package over to the carrier.", 740, 200).getId();

        arcoService.crear(empresaId, recibir, autorizar, null, null);
        // Todo arco que entra a un gateway exclusivo lleva condicion (regla de ArcoService).
        arcoService.crear(empresaId, autorizar, pagoAprobado, null, "Authorization response received");
        arcoService.crear(empresaId, pagoAprobado, empacar, "Approved", "payment.status == APPROVED");
        arcoService.crear(empresaId, pagoAprobado, cancelar, "Declined", "payment.status == DECLINED");
        arcoService.crear(empresaId, empacar, enviar, null, null);

        correlacionar(empresaId, mensajeService.crear(empresaId, procesoId, "Order placed",
                "Cart items, shipping address and payment method.", cliente.getId(), tienda.getId()));
        correlacionar(empresaId, mensajeService.crear(empresaId, procesoId, "Payment authorization request",
                "Order total and tokenized card.", tienda.getId(), pasarela.getId()));
        correlacionar(empresaId, mensajeService.crear(empresaId, procesoId, "Payment authorization result",
                "Approved or declined, with the transaction id.", pasarela.getId(), tienda.getId()));
        correlacionar(empresaId, mensajeService.crear(empresaId, procesoId, "Shipment request",
                "Package size, weight and delivery address.", tienda.getId(), transportadora.getId()));
        correlacionar(empresaId, mensajeService.crear(empresaId, procesoId, "Order status notification",
                "Confirmation with the tracking number, or the cancellation notice.", tienda.getId(),
                cliente.getId()));

        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO);
    }

    /** Todos los mensajes del pedido se correlacionan por su numero de orden. */
    private void correlacionar(Long empresaId, Mensaje mensaje) {
        correlacionService.definir(empresaId, mensaje.getId(), CLAVE_DE_CORRELACION);
    }
}
