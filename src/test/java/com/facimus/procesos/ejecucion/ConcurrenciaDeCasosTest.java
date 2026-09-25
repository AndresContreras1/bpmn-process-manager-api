package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

/**
 * D3: un caso avanza con su fila bloqueada. Dos personas que completan la misma tarea a la vez no la completan dos
 * veces: la segunda espera a la primera y, cuando entra, la tarea ya no esta esperando a nadie.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrenciaDeCasosTest {

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
    private CasoService casoService;

    @Autowired
    private TareaService tareaService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long empresaId;
    private Long adminId;
    private Long rolId;
    private Long procesoId;

    @BeforeAll
    void publicarUnProcesoConUnaTarea() {
        empresaId = empresaService.registrar("Tienda concurrente", "900222111-1", "contacto@concurrente.com",
                "Administradora", "admin@concurrentes.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@concurrentes.com").orElseThrow().getId();

        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Una sola tarea",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        rolId = rolProcesoService.crear(empresaId, adminId, "Sales", null).id();
        Long lane = laneService.crear(empresaId, adminId, tienda, "Sales", rolId).id();
        Long inicio = eventoService.crear(empresaId, adminId, lane, "Order received", TipoEvento.INICIO, 20, 80)
                .id();
        Long recibir = actividadService.crear(empresaId, adminId, lane, "Receive order", "Check the cart",
                TipoActividad.USUARIO, 160, 80).id();
        Long fin = eventoService.crear(empresaId, adminId, lane, "Order accepted", TipoEvento.FIN, 320, 80).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, fin));
        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
    }

    @Test
    @DisplayName("Dos personas completando la misma tarea: la segunda espera y recibe que ya fue completada")
    void completarDosVecesALaVez_laSegundaEspera() throws Exception {
        CasoResponse caso = casoService.abrir(empresaId, adminId, procesoId, "ORD-100", Map.of());
        Long tareaId = bandeja().getFirst().id();
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        CountDownLatch completada = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);

        try {
            // La primera completa la tarea y deja su transaccion abierta, con el caso bloqueado.
            Future<?> primera = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                tareaService.completar(empresaId, adminId, tareaId, Map.of("quien", "primera"));
                completada.countDown();
                esperar(terminar);
            }));
            assertThat(completada.await(10, TimeUnit.SECONDS)).isTrue();

            // La segunda intenta lo mismo: se queda esperando el bloqueo del caso, no completa nada.
            Future<TareaResponse> segunda = hilos.submit(
                    () -> tareaService.completar(empresaId, adminId, tareaId, Map.of("quien", "segunda")));
            assertThatThrownBy(() -> segunda.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            terminar.countDown();
            primera.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> segunda.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ReglaNegocioException.class)
                    .hasRootCauseMessage("La tarea ya fue completada.");
        } finally {
            terminar.countDown();
            hilos.shutdownNow();
        }

        // La tarea se completo una sola vez, con los datos de la primera, y el caso siguio su camino una sola vez.
        assertThat(tareaService.obtener(empresaId, tareaId).datosSalida()).containsEntry("quien", "primera");
        var detalle = casoService.obtener(empresaId, caso.id());
        assertThat(detalle.caso().estado()).isEqualTo(EstadoCaso.TERMINADO);
        assertThat(detalle.pasos()).extracting(PasoDelCasoResponse::nodoNombre)
                .containsExactly("Order received", "Receive order", "Order accepted");
        assertThat(detalle.pasos()).extracting(PasoDelCasoResponse::estado)
                .containsOnly(EstadoActividadCaso.COMPLETADA);
    }

    private List<TareaResponse> bandeja() {
        return tareaService.bandeja(empresaId, rolId, null, null, Paginacion.de(0, 10)).content();
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La otra transaccion no llego a tiempo.");
            }
        } catch (InterruptedException interrumpida) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrumpida);
        }
    }
}
