package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.dto.response.PanelDeSimulacionResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PendientesPorSocioResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.OrigenMensajeEntrante;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * Mover el reloj: un pedido de punta a punta sin que nadie mande la respuesta a mano. El caso pide la autorizacion,
 * el mensaje se queda en la bandeja de salida, el tick lo entrega, el socio contesta y el caso termina.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimulacionIntegracionTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;

    @Autowired
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    @Autowired
    private MensajeriaService mensajeriaService;

    @Autowired
    private SimulacionService simulacionService;

    @Autowired
    private ApplicationContext contexto;

    private TiendaConMensajeria tienda;
    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        tienda = new TiendaConMensajeria(contexto);
        empresaId = empresaService.registrar("Tienda que simula", "900888222-1", "contacto@simula.com",
                "Administradora", "admin@simula.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@simula.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un pedido entero sin que nadie conteste a mano: el tick entrega, el socio responde y termina")
    void unPedidoEntero_loMueveElReloj() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment moved by the clock");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-1000");

        PanelDeSimulacionResponse despues = simulacionService.tick(empresaId, 1);

        CasoDetalleResponse caso = casoService.obtener(empresaId, casoId);
        assertThat(caso.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(caso.pasos()).extracting(PasoDelCasoResponse::estado)
                .containsOnly(EstadoActividadCaso.COMPLETADA);
        assertThat(mensajeriaService.salientesDelCaso(empresaId, casoId)).singleElement()
                .returns(EstadoMensajeSaliente.ENTREGADO, MensajeSalienteResponse::estado)
                .returns(1, MensajeSalienteResponse::intentos);
        assertThat(mensajeriaService.entrantesDelCaso(empresaId, casoId))
                .extracting(MensajeEntranteResponse::nombre, MensajeEntranteResponse::origen)
                .contains(org.assertj.core.api.Assertions.tuple(TiendaConMensajeria.RESULTADO,
                        OrigenMensajeEntrante.SIMULADOR_PAGOS));
        assertThat(despues.salientesPendientes()).isZero();
    }

    @Test
    @DisplayName("Antes de su tick de entrega el mensaje no se entrega: sale en un tick y llega en el siguiente")
    void antesDeSuTick_noSeEntrega() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment delivered later");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-1100");
        MensajeSalienteResponse saliente = mensajeriaService.salientesDelCaso(empresaId, casoId).getFirst();

        assertThat(saliente.tickEntrega()).isEqualTo(saliente.tickCreacion() + 1);
        assertThat(saliente.estado()).isEqualTo(EstadoMensajeSaliente.PENDIENTE);
        assertThat(casoService.obtener(empresaId, casoId).caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
    }

    @Test
    @DisplayName("El panel dice en que tick va la tienda y que queda pendiente, por socio")
    void elPanel_cuentaLoQueQueda() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment on the dashboard");
        unPedidoEsperandoLaPasarela(procesoId, "ORD-1200");

        PanelDeSimulacionResponse panel = simulacionService.panel(empresaId);

        assertThat(panel.reloj()).isEqualTo(configuracionTiendaService.reloj(empresaId));
        assertThat(panel.modo()).isEqualTo(ModoSimulacion.MANUAL);
        assertThat(panel.salientesPendientes()).isPositive();
        assertThat(panel.porSocio()).extracting(PendientesPorSocioResponse::socio).contains(Integracion.PAGOS);
    }

    @Test
    @DisplayName("Una respuesta que llega antes de tiempo se queda esperando, y el tick siguiente la recoge")
    void respuestaAdelantada_laRecogeElTickSiguiente() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment answered too early");
        MensajeEntranteResponse pedido = mensajeriaService.recibir(empresaId, procesoId,
                DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-1300"), null));

        // Contesta la pasarela antes de que el caso llegue siquiera a pedirle nada.
        MensajeEntranteResponse adelantada = mensajeriaService.recibir(empresaId, procesoId,
                DatosDelEntrante.aMano(TiendaConMensajeria.RESULTADO, "ORD-1300", Map.of("status", "APPROVED"),
                        null));
        assertThat(adelantada.resultado()).isEqualTo(ResultadoCorrelacion.EN_ESPERA);

        completarLaTareaDe(procesoId, pedido.casoId());
        simulacionService.tick(empresaId, 1);

        assertThat(casoService.obtener(empresaId, pedido.casoId()).caso().estado())
                .isEqualTo(EstadoCaso.TERMINADO);
        assertThat(mensajeriaService.bandejaDeEntrada(empresaId, procesoId, ResultadoCorrelacion.EN_ESPERA,
                Paginacion.de(0, 10)).content()).isEmpty();
    }

    @Test
    @DisplayName("El tick de una tienda no entrega los mensajes de otra ni le mueve el reloj")
    void elTick_esDeCadaTienda() {
        Long procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment of the first store");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-1400");
        Long otraId = empresaService.registrar("Tienda vecina que simula", "900888222-2", "contacto@vecina2.com",
                "Otro", "admin@vecina2.com", "clave12345").id();
        Long otroAdmin = usuarioRepository.findByEmail("admin@vecina2.com").orElseThrow().getId();
        Long suProceso = tienda.publicar(otraId, otroAdmin, "Order fulfillment of the second store");
        mensajeriaService.recibir(otraId, suProceso,
                DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-1400"), null));
        int suReloj = configuracionTiendaService.reloj(otraId);

        simulacionService.tick(empresaId, 1);

        assertThat(configuracionTiendaService.reloj(otraId)).isEqualTo(suReloj);
        assertThat(casoService.obtener(empresaId, casoId).caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(simulacionService.panel(otraId).salientesPendientes()).isZero();
    }

    @Test
    @DisplayName("El reloj se mueve entre uno y cien ticks por vez: no vale quedarse quieto ni saltar un ano")
    void elTick_tieneLimites() {
        assertThatThrownBy(() -> simulacionService.tick(empresaId, 0))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("entre 1 y 100");
        assertThatThrownBy(() -> simulacionService.tick(empresaId, 101))
                .isInstanceOf(ReglaNegocioException.class);
    }

    /** Un pedido abierto por mensaje al que ventas ya lo reviso: esta esperando la respuesta de la pasarela. */
    private Long unPedidoEsperandoLaPasarela(Long procesoId, String referencia) {
        Long casoId = mensajeriaService.recibir(empresaId, procesoId,
                        DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO, null, Map.of("orderId", referencia), null))
                .casoId();
        completarLaTareaDe(procesoId, casoId);
        assertThat(casoService.obtener(empresaId, casoId).pasos())
                .filteredOn(paso -> paso.nodoNombre().equals(TiendaConMensajeria.ESPERAR_PAGO))
                .singleElement().returns(EstadoActividadCaso.EN_ESPERA, PasoDelCasoResponse::estado);
        return casoId;
    }

    private void completarLaTareaDe(Long procesoId, Long casoId) {
        TareaResponse tarea = tareaService.bandeja(empresaId, adminId, false, null, procesoId, null,
                        Paginacion.de(0, 10)).content().stream()
                .filter(pendiente -> pendiente.casoId().equals(casoId))
                .findFirst().orElseThrow();
        tareaService.completar(empresaId, adminId, tarea.id(), null);
    }
}
