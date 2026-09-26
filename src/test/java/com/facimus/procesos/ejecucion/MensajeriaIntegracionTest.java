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
import org.springframework.context.ApplicationContext;
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
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.model.PoliticaSinCaso;
import com.facimus.procesos.modelado.service.CorrelacionService;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.modelado.model.Integracion;

/**
 * Los cuatro finales de la correlacion sobre un proceso de verdad: un mensaje abre un pedido, otro se entrega al
 * caso que lo esperaba, otro se queda en la bandeja porque todavia nadie lo espera, y otro se descarta. Y lo que
 * pasa cuando el mismo mensaje llega dos veces.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MensajeriaIntegracionTest {

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
    private ProcesoService procesoService;

    @Autowired
    private MensajeService mensajeService;

    @Autowired
    private CorrelacionService correlacionService;

    @Autowired
    private ApplicationContext contexto;

    private TiendaConMensajeria tienda;
    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        tienda = new TiendaConMensajeria(contexto);
        empresaId = empresaService.registrar("Tienda con mensajeria", "900666111-1", "contacto@mensajeria.com",
                "Administradora", "admin@mensajeria.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@mensajeria.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Un pedido entra por mensaje: abre el caso, le pone su referencia y deja su cuerpo en variables")
    void mensajeDeInicio_abreElPedido() {
        Long procesoId = publicar("Order fulfillment by message");

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.PEDIDO, null,
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

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.RESULTADO, null,
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

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.RESULTADO, null, Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        assertThat(entrante.casoId()).isNull();
        assertThat(casoService.obtener(empresaId, casoId).caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
    }

    @Test
    @DisplayName("Una clave que no es de ningun caso de este proceso se descarta, escrita en la bandeja")
    void claveDesconocida_seDescarta() {
        Long procesoId = publicar("Order fulfillment with an unknown key");
        unPedidoEsperandoLaPasarela(procesoId, "ORD-400");

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.RESULTADO, "ORD-999",
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
        MensajeEntranteResponse pedido = recibir(procesoId, TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-500"), null);

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.RESULTADO, "ORD-500",
                Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.EN_ESPERA);
        assertThat(entrante.casoId()).isEqualTo(pedido.casoId());
        assertThat(casoService.obtener(empresaId, pedido.casoId()).caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
    }

    @Test
    @DisplayName("El mismo mensaje mandado dos veces con la misma clave externa no se procesa dos veces")
    void claveExternaRepetida_noSeProcesaDosVeces() {
        Long procesoId = publicar("Order fulfillment sent twice");

        MensajeEntranteResponse primera = recibir(procesoId, TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-600"),
                "webhook-600");
        MensajeEntranteResponse segunda = recibir(procesoId, TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-600"),
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
        recibir(procesoId, TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-650"), "webhook-650");

        Long otraId = empresaService.registrar("Tienda vecina con mensajeria", "900666111-2",
                "contacto@vecina.com", "Otro", "admin@vecina.com", "clave12345").id();
        Long otroAdmin = usuarioRepository.findByEmail("admin@vecina.com").orElseThrow().getId();
        Long suProceso = tienda.publicar(otraId, otroAdmin, "Order fulfillment next door");

        MensajeEntranteResponse suyo = mensajeriaService.recibir(otraId, suProceso,
                DatosDelEntrante.aMano(TiendaConMensajeria.PEDIDO, null, Map.of("orderId", "ORD-650"), "webhook-650"));

        assertThat(suyo.repetido()).isFalse();
        assertThat(suyo.resultado()).isEqualTo(ResultadoCorrelacion.CASO_NUEVO);
    }

    @Test
    @DisplayName("Un mensaje que la tienda manda no se puede recibir, aunque exista con ese nombre")
    void mensajeQueLaTiendaManda_noSeRecibe() {
        Long procesoId = publicar("Order fulfillment with an outgoing name");

        assertThatThrownBy(() -> recibir(procesoId, TiendaConMensajeria.AUTORIZACION, "ORD-700", Map.of(), null))
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
    @DisplayName("Dos procesos de la tienda con un pedido de la misma referencia no se confunden")
    void mismaReferenciaEnDosProcesos_cadaMensajeVaAlSuyo() {
        Long unProceso = publicar("Order fulfillment with a shared reference");
        Long otroProceso = publicar("Another fulfillment with the same reference");
        Long casoDeUno = unPedidoEsperandoLaPasarela(unProceso, "ORD-DOBLE");
        Long casoDelOtro = unPedidoEsperandoLaPasarela(otroProceso, "ORD-DOBLE");

        MensajeEntranteResponse entrante = recibir(otroProceso, TiendaConMensajeria.RESULTADO, "ORD-DOBLE",
                Map.of("status", "APPROVED"), null);

        assertThat(entrante.casoId()).isEqualTo(casoDelOtro);
        assertThat(casoService.obtener(empresaId, casoDelOtro).caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(casoService.obtener(empresaId, casoDeUno).caso().estado()).isEqualTo(EstadoCaso.ABIERTO);
    }

    @Test
    @DisplayName("Un mensaje de inicio cuya correlacion dice descartar no abre ningun caso")
    void mensajeDeInicioQueDiceDescartar_noAbreCaso() {
        Long procesoId = publicar("Order fulfillment that stopped opening cases");
        // Esta anclado a un evento que empieza el proceso, que es la otra mitad de la condicion: lo unico que
        // cambia es lo que dice su correlacion, y con eso deja de abrir casos.
        republicarConPolitica(procesoId, TiendaConMensajeria.PEDIDO, PoliticaSinCaso.DESCARTAR);

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.PEDIDO, null,
                Map.of("orderId", "ORD-970"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        assertThat(entrante.casoId()).isNull();
        assertThat(casoService.listar(empresaId, procesoId, null, "ORD-970", Paginacion.de(0, 10)).content())
                .isEmpty();
    }

    @Test
    @DisplayName("Un mensaje para un caso que ya se cerro se descarta: un caso cerrado no recibe nada")
    void casoCerrado_noRecibeNada() {
        Long procesoId = publicar("Order fulfillment already closed");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-950");
        casoService.cancelar(empresaId, adminId, casoId);

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.RESULTADO, "ORD-950",
                Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        assertThat(entrante.casoId()).isNull();
        assertThat(casoService.obtener(empresaId, casoId).caso().estado()).isEqualTo(EstadoCaso.CANCELADO);
    }

    @Test
    @DisplayName("Una correlacion que dice abrir caso en mitad del flujo no abre nada: se descarta")
    void iniciarCasoEnMitadDelFlujo_noAbreNada() {
        Long procesoId = publicar("Order fulfillment with a misplaced policy");
        // La respuesta del pago dice INICIAR_CASO, pero esta anclada a un evento intermedio y no a un inicio.
        republicarConPolitica(procesoId, TiendaConMensajeria.RESULTADO, PoliticaSinCaso.INICIAR_CASO);

        MensajeEntranteResponse entrante = recibir(procesoId, TiendaConMensajeria.RESULTADO, "ORD-960",
                Map.of("status", "APPROVED"), null);

        assertThat(entrante.resultado()).isEqualTo(ResultadoCorrelacion.DESCARTADO);
        assertThat(casoService.listar(empresaId, procesoId, null, "ORD-960", Paginacion.de(0, 10)).content())
                .isEmpty();
    }

    @Test
    @DisplayName("El envio del caso queda en la bandeja de salida con su clave y su cuerpo")
    void elEnvio_quedaEnLaBandejaDeSalida() {
        Long procesoId = publicar("Order fulfillment with an outbox");
        Long casoId = unPedidoEsperandoLaPasarela(procesoId, "ORD-900");

        List<MensajeSalienteResponse> salida = mensajeriaService.salientesDelCaso(empresaId, casoId);

        assertThat(salida).singleElement()
                .returns(TiendaConMensajeria.AUTORIZACION, MensajeSalienteResponse::nombre)
                .returns(TiendaConMensajeria.PASARELA, MensajeSalienteResponse::poolDestinoNombre)
                .returns(Integracion.PAGOS, MensajeSalienteResponse::integracion)
                .returns("ORD-900", MensajeSalienteResponse::clave)
                .returns(EstadoMensajeSaliente.PENDIENTE, MensajeSalienteResponse::estado);
        assertThat(salida.getFirst().cuerpo()).containsEntry("orderId", "ORD-900");
        assertThat(mensajeriaService.bandejaDeSalida(empresaId, procesoId, EstadoMensajeSaliente.PENDIENTE,
                Paginacion.de(0, 50)).content()).extracting(MensajeSalienteResponse::nombre).contains(TiendaConMensajeria.AUTORIZACION);
    }

    /** Un pedido abierto por mensaje al que se le completa la tarea de ventas: se queda esperando a la pasarela. */
    private Long unPedidoEsperandoLaPasarela(Long procesoId, String referencia) {
        Long casoId = recibir(procesoId, TiendaConMensajeria.PEDIDO, null, Map.of("orderId", referencia), null).casoId();
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

    /** Cambia lo que hace un mensaje cuando no encuentra caso y vuelve a publicar: la version vigente es la nueva. */
    private void republicarConPolitica(Long procesoId, String mensaje, PoliticaSinCaso politica) {
        Long mensajeId = mensajeService.listarPorProceso(empresaId, procesoId).stream()
                .filter(candidato -> candidato.nombre().equals(mensaje))
                .findFirst().orElseThrow().id();
        correlacionService.definir(empresaId, adminId, mensajeId, "orderId", "orderId", politica,
                correlacionService.obtener(empresaId, mensajeId).version());
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
    }

    private MensajeEntranteResponse recibir(Long procesoId, String nombre, String clave,
            Map<String, Object> cuerpo, String claveExterna) {
        return mensajeriaService.recibir(empresaId, procesoId,
                DatosDelEntrante.aMano(nombre, clave, cuerpo, claveExterna));
    }

    private Long publicar(String nombre) {
        return tienda.publicar(empresaId, adminId, nombre);
    }

    private List<TipoEventoCaso> tiposDeLaBitacora(Long casoId) {
        return casoService.eventos(empresaId, casoId).stream().map(EventoCasoResponse::tipo).toList();
    }
}
