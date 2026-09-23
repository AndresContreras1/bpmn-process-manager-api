package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.IntegracionNoConfiguradaException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ArcoResponse;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.HallazgoResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.dto.response.RevisionResponse;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.model.TipoGateway;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.service.impl.RevisionServiceImpl;

/** Lo que pasa alrededor de la llamada al modelo: la puerta de lectura, la funcion apagada y lo que se le manda. */
@ExtendWith(MockitoExtension.class)
class RevisionServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 100L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 9, 22, 15, 0);

    @Mock
    private DiagramaService diagramaService;
    @Mock
    private RevisorDeDiagramas revisor;

    private final Clock reloj = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private RevisionService servicio() {
        return new RevisionServiceImpl(diagramaService, revisor, reloj);
    }

    @Test
    @DisplayName("Devuelve los hallazgos del revisor con la fecha en que se pidieron")
    void revisar_conHallazgos_losDevuelveConSuFecha() {
        HallazgoResponse hallazgo = new HallazgoResponse(Severidad.MEDIA, "Actividad: Pick items",
                "No dice que pasa si no hay stock", "Agrega la rama de faltantes");
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(diagrama());
        when(revisor.estaConfigurado()).thenReturn(true);
        when(revisor.revisar(any())).thenReturn(new Dictamen("Falta el caso de error.", List.of(hallazgo)));

        RevisionResponse revision = servicio().revisar(EMPRESA, PROCESO);

        assertThat(revision.procesoId()).isEqualTo(PROCESO);
        assertThat(revision.resumen()).isEqualTo("Falta el caso de error.");
        assertThat(revision.hallazgos()).containsExactly(hallazgo);
        assertThat(revision.fecha()).isEqualTo(AHORA);
        assertThat(revision.reutilizada()).isFalse();
    }

    @Test
    @DisplayName("Un proceso que el usuario no puede leer responde 404 sin gastar una llamada al modelo")
    void revisar_procesoFueraDeLaPuerta_niLlamaAlRevisor() {
        when(diagramaService.obtener(EMPRESA, PROCESO))
                .thenThrow(new RecursoNoEncontradoException("Proceso no encontrado."));

        assertThatThrownBy(() -> servicio().revisar(EMPRESA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(revisor, never()).revisar(any());
    }

    @Test
    @DisplayName("Sin configurar, la funcion responde que no esta disponible en vez de fallar por dentro")
    void revisar_sinConfigurar_lanzaNoConfigurada() {
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(diagrama());
        when(revisor.estaConfigurado()).thenReturn(false);

        assertThatThrownBy(() -> servicio().revisar(EMPRESA, PROCESO))
                .isInstanceOf(IntegracionNoConfiguradaException.class)
                .hasMessageContaining("no está configurada");
        verify(revisor, never()).revisar(any());
    }

    @Test
    @DisplayName("Al revisor se le manda el diagrama contado en texto, no el JSON del endpoint")
    void revisar_mandaElDiagramaDescrito() {
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(diagrama());
        when(revisor.estaConfigurado()).thenReturn(true);
        when(revisor.revisar(any())).thenReturn(new Dictamen("Bien.", List.of()));

        servicio().revisar(EMPRESA, PROCESO);

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(revisor).revisar(enviado.capture());
        assertThat(enviado.getValue())
                .contains("Proceso: Order fulfillment (PUBLICADO)")
                .contains("- Demo Store (EMPRESA)")
                .contains("Lane Picking (rol Warehouse)")
                .contains("Actividad: Pick items - Recoger del estante")
                .contains("Gateway EXCLUSIVO: Stock available?")
                .contains("\"Pick items\" -> \"Stock available?\" [listo] si stock > 0")
                .contains("\"Shipment requested\": Demo Store -> Carrier (correlacion por orderId)")
                // Los ids internos no viajan al modelo: solo nombres.
                .doesNotContain("procesoId");
    }

    @Test
    @DisplayName("Un mensaje sin clave de correlacion se cuenta como tal, que es justo lo que hay que revisar")
    void revisar_mensajeSinCorrelacion_seDice() {
        DiagramaResponse sinCorrelacion = new DiagramaResponse(diagrama().proceso(), false, diagrama().pools(),
                diagrama().lanes(), diagrama().actividades(), diagrama().gateways(), diagrama().arcos(),
                diagrama().mensajes(), List.of());
        when(diagramaService.obtener(EMPRESA, PROCESO)).thenReturn(sinCorrelacion);
        when(revisor.estaConfigurado()).thenReturn(true);
        when(revisor.revisar(any())).thenReturn(new Dictamen("Bien.", List.of()));

        servicio().revisar(EMPRESA, PROCESO);

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(revisor).revisar(enviado.capture());
        assertThat(enviado.getValue()).contains("(sin clave de correlacion)");
    }

    private static DiagramaResponse diagrama() {
        ProcesoResponse proceso = new ProcesoResponse(PROCESO, "Order fulfillment", "De la compra a la entrega",
                "Fulfillment", EstadoProceso.PUBLICADO, true, AHORA, AHORA, 0L, null, null);
        PoolResponse tienda = new PoolResponse(5L, "Demo Store", TipoParticipante.EMPRESA, false, 0, PROCESO, 0L,
                null, AHORA, null, AHORA);
        PoolResponse transportadora = new PoolResponse(6L, "Carrier", TipoParticipante.PROVEEDOR, true, 1, PROCESO,
                0L, null, AHORA, null, AHORA);
        LaneResponse lane = new LaneResponse(7L, "Picking", 0, 5L, 20L, "Warehouse", 0L, null, AHORA, null, AHORA);
        ActividadResponse actividad = new ActividadResponse(30L, "Pick items", "Recoger del estante", 10, 20, 7L,
                0L, null, AHORA, null, AHORA);
        GatewayResponse gateway = new GatewayResponse(31L, "Stock available?", TipoGateway.EXCLUSIVO, 30, 40, 7L,
                0L, null, AHORA, null, AHORA);
        ArcoResponse arco = new ArcoResponse(50L, "listo", "stock > 0", 30L, 31L, 5L, 0L, null, AHORA, null, AHORA);
        MensajeResponse mensaje = new MensajeResponse(40L, "Shipment requested", "Pedido listo", 5L, 6L, PROCESO,
                0L, null, AHORA, null, AHORA);
        CorrelacionResponse correlacion = new CorrelacionResponse(60L, "orderId", 40L, 0L, null, AHORA, null, AHORA);
        return new DiagramaResponse(proceso, false, List.of(tienda, transportadora), List.of(lane),
                List.of(actividad), List.of(gateway), List.of(arco), List.of(mensaje), List.of(correlacion));
    }
}
