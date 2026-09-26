package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;

/**
 * El pedido de la demo de punta a punta, sobre el proceso completo: entra por mensaje, ventas lo revisa, se pide
 * la autorizacion del pago, el reloj la entrega y el socio contesta, el gateway decide, bodega empaca, se manda el
 * envio, el reloj lo entrega, llega la confirmacion y el pedido termina enviado.
 *
 * <p>Nadie contesta a mano por los socios: lo unico que se manda de fuera es el pedido del cliente. El resto lo
 * mueven el reloj y las dos personas que tienen algo que hacer, que es exactamente lo que este PR venia a cerrar.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoDePuntaAPuntaTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    @Autowired
    private MensajeriaService mensajeriaService;

    @Autowired
    private SimulacionService simulacionService;

    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;

    @Autowired
    private ApplicationContext contexto;

    private TiendaConMensajeria demo;
    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        demo = new TiendaConMensajeria(contexto);
        empresaId = empresaService.registrar("Tienda de la demo", "900999444-1", "contacto@demo.com",
                "Administradora", "admin@demo.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@demo.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un pedido entero: entra por mensaje, lo mueven dos personas y dos ticks, y sale enviado")
    void unPedido_dePuntaAPunta() {
        conRechazoDePagos(0);
        Long procesoId = demo.publicarLaDemo(empresaId, adminId, "Order fulfillment end to end");

        Long casoId = mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(
                TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-2001"), "webhook-2001")).casoId();
        assertThat(pasoActual(casoId)).isEqualTo(TiendaConMensajeria.REVISAR);

        completarLaTarea(procesoId, casoId);
        assertThat(pasoActual(casoId)).isEqualTo(TiendaConMensajeria.ESPERAR_PAGO);
        assertThat(mensajeriaService.salientesDelCaso(empresaId, casoId))
                .extracting(MensajeSalienteResponse::nombre)
                .containsExactly(TiendaConMensajeria.AUTORIZACION);

        simulacionService.tick(empresaId, 1);
        assertThat(pasoActual(casoId)).isEqualTo(TiendaConMensajeria.EMPACAR);

        completarLaTarea(procesoId, casoId);
        assertThat(pasoActual(casoId)).isEqualTo(TiendaConMensajeria.ESPERAR_ENVIO);

        // El transportista recoge el paquete en el primer tick y confirma la entrega tres ticks despues: un
        // pedido esperando al transportista es lo que hay que poder ver, y solo se ve si el tiempo pasa.
        simulacionService.tick(empresaId, 1);
        assertThat(pasoActual(casoId)).isEqualTo(TiendaConMensajeria.ESPERAR_ENVIO);
        simulacionService.tick(empresaId, 3);

        CasoDetalleResponse terminado = casoService.obtener(empresaId, casoId);
        assertThat(terminado.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(terminado.pasos()).extracting(PasoDelCasoResponse::nodoNombre).containsExactly(
                "Order received", TiendaConMensajeria.REVISAR, "Request payment authorization",
                TiendaConMensajeria.ESPERAR_PAGO, TiendaConMensajeria.DECIDIR, TiendaConMensajeria.EMPACAR,
                "Ship order", TiendaConMensajeria.ESPERAR_ENVIO, "Order shipped");
        assertThat(mensajeriaService.salientesDelCaso(empresaId, casoId))
                .extracting(MensajeSalienteResponse::nombre, MensajeSalienteResponse::estado)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(TiendaConMensajeria.AUTORIZACION,
                                EstadoMensajeSaliente.ENTREGADO),
                        org.assertj.core.api.Assertions.tuple(TiendaConMensajeria.ENVIO,
                                EstadoMensajeSaliente.ENTREGADO));
        assertThat(mensajeriaService.entrantesDelCaso(empresaId, casoId))
                .extracting(MensajeEntranteResponse::nombre)
                .containsExactly(TiendaConMensajeria.PEDIDO, TiendaConMensajeria.RESULTADO,
                        TiendaConMensajeria.CONFIRMACION);
        assertThat(tiposDeLaBitacora(casoId)).contains(TipoEventoCaso.MENSAJE_ENVIADO,
                TipoEventoCaso.MENSAJE_RECIBIDO, TipoEventoCaso.GATEWAY_DECIDIO, TipoEventoCaso.CASO_TERMINADO);
        assertThat(simulacionService.panel(empresaId).salientesPendientes()).isZero();
    }

    @Test
    @DisplayName("Un pago rechazado se va por la otra rama, avisa al cliente y el pedido termina cancelado")
    void pagoRechazado_terminaCancelado() {
        Long procesoId = demo.publicarLaDemo(empresaId, adminId, "Order fulfillment with a declined payment");
        Long casoId = mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(
                TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-2002"), null)).casoId();
        completarLaTarea(procesoId, casoId);

        // Esta vez contesta la pasarela de verdad, con lo que un socio simulado contestara en el PR que viene.
        mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(TiendaConMensajeria.RESULTADO,
                "ORD-2002", Map.of("status", "DECLINED"), null));

        CatalogoDelCaso caso = new CatalogoDelCaso(casoService.obtener(empresaId, casoId));
        assertThat(caso.detalle().caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(caso.nombres()).contains(TiendaConMensajeria.CANCELAR, "Order cancelled")
                .doesNotContain(TiendaConMensajeria.EMPACAR);
        assertThat(caso.detalle().variables()).containsKey("payment");
        // Cancelar es una actividad de servicio con un correo anclado: avisar al cliente es parte de cancelar.
        assertThat(mensajeriaService.salientesDelCaso(empresaId, casoId))
                .extracting(MensajeSalienteResponse::nombre).containsExactly(TiendaConMensajeria.AUTORIZACION,
                        TiendaConMensajeria.AVISO);
    }

    @Test
    @DisplayName("Con la tasa de rechazo al cien, la pasarela rechaza y el pedido acaba cancelado sin tocar nada")
    void pasarelaQueRechaza_elPedidoSeCancela() {
        conRechazoDePagos(100);
        Long procesoId = demo.publicarLaDemo(empresaId, adminId, "Order fulfillment declined by the gateway");
        Long casoId = mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(
                TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-2004"), null)).casoId();
        completarLaTarea(procesoId, casoId);

        simulacionService.tick(empresaId, 1);

        CasoDetalleResponse caso = casoService.obtener(empresaId, casoId);
        assertThat(caso.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(caso.pasos()).extracting(PasoDelCasoResponse::nodoNombre)
                .contains(TiendaConMensajeria.CANCELAR).doesNotContain(TiendaConMensajeria.EMPACAR);
        assertThat(caso.variables()).extractingByKey("payment").asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsEntry("status", "DECLINED")
                .containsEntry("transactionId", "SIM-PAY-" + casoId);
        conRechazoDePagos(0);
    }

    @Test
    @DisplayName("El mismo pedido mandado dos veces no abre dos casos, y el segundo lo dice")
    void elMismoPedidoDosVeces_abreUnSoloCaso() {
        Long procesoId = demo.publicarLaDemo(empresaId, adminId, "Order fulfillment sent twice by the customer");

        MensajeEntranteResponse primera = mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(
                TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-2003"), "webhook-2003"));
        MensajeEntranteResponse segunda = mensajeriaService.recibir(empresaId, procesoId, DatosDelEntrante.aMano(
                TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-2003"), "webhook-2003"));

        assertThat(segunda.repetido()).isTrue();
        assertThat(segunda.casoId()).isEqualTo(primera.casoId());
        assertThat(casoService.listar(empresaId, procesoId, null, "ORD-2003", Paginacion.de(0, 10)).content())
                .hasSize(1);
        assertThat(mensajeriaService.bandejaDeEntrada(empresaId, procesoId, ResultadoCorrelacion.CASO_NUEVO,
                Paginacion.de(0, 10)).content()).hasSize(1);
    }

    /** Fija cuantos pagos de cada cien rechaza la pasarela; cero y cien son las dos formas de forzar el final. */
    private void conRechazoDePagos(int tasa) {
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(empresaId);
        configuracionTiendaService.editar(empresaId, adminId, antes.politicaEstructura(), null,
                ParametrosSimulacion.builder().tasaRechazoPagos(tasa).build(), antes.version());
    }

    /** Lo que el caso tiene delante: el unico paso que sigue vivo, que es donde esta parado. */
    private String pasoActual(Long casoId) {
        return casoService.obtener(empresaId, casoId).pasos().stream()
                .filter(paso -> paso.estado().estaVivo())
                .map(PasoDelCasoResponse::nodoNombre)
                .findFirst().orElseThrow(() -> new AssertionError("El caso no tiene ningun paso vivo"));
    }

    private void completarLaTarea(Long procesoId, Long casoId) {
        TareaResponse tarea = tareaService.bandeja(empresaId, adminId, false, null, procesoId, null,
                        Paginacion.de(0, 10)).content().stream()
                .filter(pendiente -> pendiente.casoId().equals(casoId))
                .findFirst().orElseThrow();
        tareaService.completar(empresaId, adminId, tarea.id(), null);
    }

    private List<TipoEventoCaso> tiposDeLaBitacora(Long casoId) {
        return casoService.eventos(empresaId, casoId).stream().map(EventoCasoResponse::tipo).toList();
    }

    /** El detalle de un caso y los nombres por los que paso, que es lo que casi todas las afirmaciones miran. */
    private record CatalogoDelCaso(CasoDetalleResponse detalle) {

        List<String> nombres() {
            return detalle.pasos().stream().map(PasoDelCasoResponse::nodoNombre).toList();
        }
    }
}
