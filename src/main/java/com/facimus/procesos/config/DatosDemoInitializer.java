package com.facimus.procesos.config;

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
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
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

        Long empresaId = empresaService.registrar("Demo Store", NIT_DEMO, "contacto@demo.com",
                "Administrador Demo", EMAIL_DEMO, PASSWORD_DEMO).id();
        Long adminId = usuarioRepository.findByEmpresaIdAndEmail(empresaId, EMAIL_DEMO).orElseThrow().getId();

        sembrarDespachoDePedidos(empresaId, adminId);
        procesoService.crear(empresaId, adminId, "Returns and refunds",
                "Return request, item inspection and refund to the original payment method.", "After-sales");
    }

    /** Proceso publicado que usa todos los elementos BPMN: pools, lanes, actividades, gateway, arcos y mensajes. */
    private void sembrarDespachoDePedidos(Long empresaId, Long adminId) {
        ProcesoResponse proceso = procesoService.crear(empresaId, adminId, "Order fulfillment",
                "From checkout to delivery: payment authorization, picking, packing and shipment.", "Fulfillment");
        Long procesoId = proceso.id();

        // El proceso nace con el pool de la tienda; los demas participantes se modelan como cajas negras.
        PoolResponse tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst();
        PoolResponse cliente = poolService.crear(empresaId, adminId, procesoId, "Customer", TipoParticipante.CLIENTE,
                true);
        PoolResponse pasarela = poolService.crear(empresaId, adminId, procesoId, "Payment gateway",
                TipoParticipante.SISTEMA_EXTERNO, true);
        PoolResponse transportadora = poolService.crear(empresaId, adminId, procesoId, "Carrier",
                TipoParticipante.PROVEEDOR, true);

        Long ventas = laneService.crear(empresaId, adminId, tienda.id(), "Sales", rolProcesoService
                .crear(empresaId, "Sales", "Receives orders and coordinates the payment.").id()).id();
        Long bodega = laneService.crear(empresaId, adminId, tienda.id(), "Warehouse", rolProcesoService
                .crear(empresaId, "Warehouse", "Picks, packs and ships the orders.").id()).id();

        Long recibir = actividadService.crear(empresaId, adminId, ventas, "Receive order",
                "Validate the cart, the stock and the shipping address.", 100, 80).id();
        Long autorizar = actividadService.crear(empresaId, adminId, ventas, "Request payment authorization",
                "Send the order total to the payment gateway.", 260, 80).id();
        Long pagoAprobado = gatewayService.crear(empresaId, adminId, ventas, "Payment approved?", TipoGateway.EXCLUSIVO,
                420, 80).id();
        Long cancelar = actividadService.crear(empresaId, adminId, ventas, "Cancel order",
                "Release the reserved stock and notify the customer.", 580, 40).id();
        Long empacar = actividadService.crear(empresaId, adminId, bodega, "Pick and pack items",
                "Collect the items and prepare the package.", 580, 200).id();
        Long enviar = actividadService.crear(empresaId, adminId, bodega, "Ship order",
                "Hand the package over to the carrier.", 740, 200).id();

        arcoService.crear(empresaId, adminId, recibir, autorizar, null, null);
        // Todo arco que entra a un gateway exclusivo lleva condicion (regla de ArcoService).
        arcoService.crear(empresaId, adminId, autorizar, pagoAprobado, null, "Authorization response received");
        arcoService.crear(empresaId, adminId, pagoAprobado, empacar, "Approved", "payment.status == APPROVED");
        arcoService.crear(empresaId, adminId, pagoAprobado, cancelar, "Declined", "payment.status == DECLINED");
        arcoService.crear(empresaId, adminId, empacar, enviar, null, null);

        correlacionar(empresaId, adminId, mensajeService.crear(empresaId, adminId, procesoId, "Order placed",
                "Cart items, shipping address and payment method.", cliente.id(), tienda.id()));
        correlacionar(empresaId, adminId, mensajeService.crear(empresaId, adminId, procesoId,
                "Payment authorization request", "Order total and tokenized card.", tienda.id(), pasarela.id()));
        correlacionar(empresaId, adminId, mensajeService.crear(empresaId, adminId, procesoId,
                "Payment authorization result",
                "Approved or declined, with the transaction id.", pasarela.id(), tienda.id()));
        correlacionar(empresaId, adminId, mensajeService.crear(empresaId, adminId, procesoId, "Shipment request",
                "Package size, weight and delivery address.", tienda.id(), transportadora.id()));
        correlacionar(empresaId, adminId, mensajeService.crear(empresaId, adminId, procesoId,
                "Order status notification",
                "Confirmation with the tracking number, or the cancellation notice.", tienda.id(),
                cliente.id()));

        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO, proceso.version());
    }

    /** Todos los mensajes del pedido se correlacionan por su numero de orden. */
    private void correlacionar(Long empresaId, Long adminId, MensajeResponse mensaje) {
        correlacionService.definir(empresaId, adminId, mensaje.id(), CLAVE_DE_CORRELACION, null);
    }
}
