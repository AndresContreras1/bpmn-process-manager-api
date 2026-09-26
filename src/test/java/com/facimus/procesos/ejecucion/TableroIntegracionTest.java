package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasosPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.LoQueSalioMalResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajesPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajesPorResultadoResponse;
import com.facimus.procesos.ejecucion.dto.response.TableroResponse;
import com.facimus.procesos.ejecucion.dto.response.TareasPorRolResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.MedidoresDeLaOperacion;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.ejecucion.service.TableroService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.modelado.model.AccionSiFalla;

import jakarta.persistence.EntityManagerFactory;

/**
 * El tablero sobre datos que se cuentan a mano: cinco pedidos, tres atendidos, uno cancelado y uno esperando en
 * la bandeja. Las cifras son exactas a proposito, porque un tablero que se aproxima no sirve para decidir nada.
 *
 * <p>Y el numero que importa de su implementacion: seis consultas, crezcan los pedidos lo que crezcan. Un tablero
 * que hiciera una consulta por caso dejaria de poder mirarse justo el dia que hiciera falta mirarlo.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TableroIntegracionTest {

    private static final int PEDIDOS = 5;
    private static final int ATENDIDOS = 3;
    /** Pedidos de otro proceso de la misma tienda, que el tablero de este no tiene que contar. */
    private static final int DE_OTRO_PROCESO = 2;
    /** Uno de los que quedaban se cancela: se cierra igual que los atendidos, pero no es un pedido rapido. */
    private static final int CANCELADOS = 1;
    /** Los que siguen esperando en la bandeja cuando el tablero se abre. */
    private static final int ESPERANDO = PEDIDOS - ATENDIDOS - CANCELADOS;
    /** Avisos al cliente que no llegan nunca, en una tienda aparte. */
    private static final int AVISOS_PERDIDOS = 2;
    /** El reloj no arranca en cero a proposito: asi se ve que el ciclo es una resta y no el tick del final. */
    private static final int RELOJ_INICIAL = 2;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TareaService tareaService;

    @Autowired
    private SimulacionService simulacionService;

    @Autowired
    private TableroService tableroService;

    @Autowired
    private CasoService casoService;

    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;

    @Autowired
    private MedidoresDeLaOperacion medidores;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ApplicationContext contexto;

    private Statistics estadisticas;
    private TiendaConMensajeria tienda;
    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private Long otroProcesoId;

    @BeforeAll
    void cincoPedidosDeLosCualesTresSeAtiendenYUnoSeCancela() {
        estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        empresaId = empresaService.registrar("Tienda del tablero", "900606060-1", "contacto@tablero.com",
                "Administradora", "admin@tablero.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@tablero.com").orElseThrow().getId();
        tienda = new TiendaConMensajeria(contexto);
        procesoId = tienda.publicar(empresaId, adminId, "Order fulfillment on a board");
        otroProcesoId = tienda.publicar(empresaId, adminId, "Returns on the same board");

        simulacionService.tick(empresaId, RELOJ_INICIAL);
        simulacionService.pedidos(empresaId, procesoId, PEDIDOS, null);
        simulacionService.pedidos(empresaId, otroProcesoId, DE_OTRO_PROCESO, null);
        bandeja().stream().limit(ATENDIDOS)
                .forEach(tarea -> tareaService.completar(empresaId, adminId, tarea.id(), null));
        simulacionService.tick(empresaId, 1);
        // Y un tick mas tarde se cancela uno de los que quedaban: se cierra en el cuatro y no en el tres, asi
        // que si el ciclo lo contara se notaria en los tres numeros a la vez.
        simulacionService.tick(empresaId, 1);
        casoService.cancelar(empresaId, adminId, bandeja().getFirst().casoId());
    }

    @Test
    @DisplayName("El tablero de un proceso cuenta los pedidos por estado y lo que cada uno tardo")
    void elTablero_cuentaLosPedidosYLoQueTardaron() {
        TableroResponse tablero = tableroService.de(empresaId, procesoId);

        assertThat(tablero.procesoId()).isEqualTo(procesoId);
        assertThat(tablero.casos()).isEqualTo(PEDIDOS);
        assertThat(tablero.casosPorEstado())
                .extracting(CasosPorEstadoResponse::estado, CasosPorEstadoResponse::cantidad)
                .containsExactlyInAnyOrder(tuple(EstadoCaso.ABIERTO, (long) ESPERANDO),
                        tuple(EstadoCaso.CANCELADO, (long) CANCELADOS),
                        tuple(EstadoCaso.TERMINADO, (long) ATENDIDOS));
        // Cada pedido atendido se abrio en el tick dos y termino en el tres: un tick de punta a punta, no tres.
        assertThat(tablero.ciclo().terminados()).isEqualTo(ATENDIDOS);
        assertThat(tablero.ciclo().medio()).isEqualTo(1.0);
        assertThat(tablero.ciclo().p95()).isEqualTo(1);
    }

    @Test
    @DisplayName("El tablero dice quien tiene trabajo esperando, con el nombre del rol y no con su id")
    void elTablero_diceQuienTieneTrabajo() {
        TableroResponse tablero = tableroService.de(empresaId, procesoId);

        assertThat(tablero.tareasPorRol()).singleElement()
                .returns((long) ESPERANDO, TareasPorRolResponse::tareas)
                .satisfies(fila -> assertThat(fila.rolNombre()).startsWith("Sales"));
    }

    @Test
    @DisplayName("El tablero cuenta lo que se mando y lo que llego, por como acabo cada cosa")
    void elTablero_cuentaLosMensajes() {
        TableroResponse tablero = tableroService.de(empresaId, procesoId);

        assertThat(tablero.salientes())
                .extracting(MensajesPorEstadoResponse::estado, MensajesPorEstadoResponse::cantidad)
                .containsExactly(tuple(EstadoMensajeSaliente.ENTREGADO, (long) ATENDIDOS));
        assertThat(tablero.entrantes())
                .extracting(MensajesPorResultadoResponse::resultado, MensajesPorResultadoResponse::cantidad)
                .containsExactlyInAnyOrder(tuple(ResultadoCorrelacion.ENTREGADO_A_CASO, (long) ATENDIDOS),
                        tuple(ResultadoCorrelacion.CASO_NUEVO, (long) PEDIDOS));
        assertThat(tablero.loQueSalioMal())
                .isEqualTo(new LoQueSalioMalResponse(0, 0, 0));
    }

    @Test
    @DisplayName("El tablero de la tienda suma los dos procesos; el de uno solo no cuenta los del otro")
    void elTableroDeLaTienda_sumaLosDosProcesos() {
        TableroResponse tablero = tableroService.de(empresaId, null);

        assertThat(tablero.procesoId()).isNull();
        assertThat(tablero.casos()).isEqualTo(PEDIDOS + DE_OTRO_PROCESO);
        assertThat(tableroService.de(empresaId, procesoId).casos()).isEqualTo(PEDIDOS);
        assertThat(tableroService.de(empresaId, otroProcesoId).casos()).isEqualTo(DE_OTRO_PROCESO);
        assertThat(tableroService.de(empresaId, otroProcesoId).salientes()).isEmpty();
    }

    @Test
    @DisplayName("Los medidores de Actuator cuentan lo que esta vivo: ni lo terminado ni lo cancelado")
    void losMedidores_cuentanLoQueEstaVivo() {
        assertThat(medidores.casosAbiertos()).isEqualTo(ESPERANDO + DE_OTRO_PROCESO);
        assertThat(medidores.tareasPendientes()).isEqualTo(ESPERANDO + DE_OTRO_PROCESO);
        assertThat(medidores.salientesPendientes()).isZero();
        assertThat(medidores.entrantesPendientes()).isZero();
    }

    @Test
    @DisplayName("El tablero cuesta seis consultas, tenga la tienda cinco pedidos o cinco mil")
    void elTablero_cuestaSeisConsultas() {
        estadisticas.clear();

        tableroService.de(empresaId, procesoId);

        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("El tablero de otra tienda no ve nada de esta")
    void elTablero_esDeCadaTienda() {
        Long otraId = empresaService.registrar("Tienda sin pedidos", "900606060-2", "contacto@vacia.com",
                "Otro", "admin@vacia.com", "clave12345").id();

        TableroResponse tablero = tableroService.de(otraId, null);

        assertThat(tablero.casos()).isZero();
        assertThat(tablero.casosPorEstado()).isEmpty();
        assertThat(tablero.tareasPorRol()).isEmpty();
        // Sin pedidos terminados no hay tiempo de ciclo: cero no seria rapido, seria mentira.
        assertThat(tablero.ciclo().terminados()).isZero();
        assertThat(tablero.ciclo().medio()).isZero();
    }

    @Test
    @DisplayName("Un pedido cancelado se cerro, pero no es un pedido rapido: no entra en el tiempo de ciclo")
    void unPedidoCancelado_noEntraEnElCiclo() {
        TableroResponse tablero = tableroService.de(empresaId, procesoId);

        assertThat(tablero.casosPorEstado())
                .extracting(CasosPorEstadoResponse::estado, CasosPorEstadoResponse::cantidad)
                .contains(tuple(EstadoCaso.CANCELADO, (long) CANCELADOS));
        // El cancelado tambien tiene tick de cierre: se abrio en el dos y se cerro en el cuatro. Si entrara,
        // serian cuatro pedidos, el promedio 1.25 y el p95 dos. Son tres, uno y uno.
        assertThat(tablero.ciclo().terminados()).isEqualTo(ATENDIDOS);
        assertThat(tablero.ciclo().medio()).isEqualTo(1.0);
        assertThat(tablero.ciclo().p95()).isEqualTo(1);
    }

    @Test
    @DisplayName("Lo que salio mal cuenta los avisos que no llegaron, y no otra cosa de la bitacora")
    void loQueSalioMal_cuentaLosAvisosQueNoLlegaron() {
        Long avisosId = empresaService.registrar("Tienda de los avisos perdidos", "900606060-3",
                "contacto@avisos.com", "Otra", "admin@avisos.com", "clave12345").id();
        Long suAdmin = usuarioRepository.findByEmail("admin@avisos.com").orElseThrow().getId();
        ConfiguracionTiendaResponse antes = configuracionTiendaService.obtener(avisosId);
        configuracionTiendaService.editar(avisosId, suAdmin, antes.politicaEstructura(), null,
                ParametrosSimulacion.builder().tasaFalloNotificaciones(100).build(), antes.version());
        Long suProceso = tienda.publicarConAviso(avisosId, suAdmin, "Order fulfillment with a lost notice",
                AccionSiFalla.FINALIZAR);
        simulacionService.pedidos(avisosId, suProceso, AVISOS_PERDIDOS, null);
        tareaService.bandeja(avisosId, suAdmin, false, null, suProceso, null, Paginacion.de(0, 50)).content()
                .forEach(tarea -> tareaService.completar(avisosId, suAdmin, tarea.id(), null));

        simulacionService.tick(avisosId, 1);

        // Dos avisos perdidos y nada mas: la bitacora de esos dos pedidos tiene muchas otras lineas.
        assertThat(tableroService.de(avisosId, suProceso).loQueSalioMal())
                .isEqualTo(new LoQueSalioMalResponse(AVISOS_PERDIDOS, 0, 0));
    }

    private List<TareaResponse> bandeja() {
        return tareaService.bandeja(empresaId, adminId, false, null, procesoId, null, Paginacion.de(0, 50))
                .content();
    }
}
