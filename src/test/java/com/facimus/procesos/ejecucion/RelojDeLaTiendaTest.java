package com.facimus.procesos.ejecucion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.ejecucion.dto.response.CasoDetalleResponse;
import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
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
 * D8: un caso no se fecha con la hora del servidor sino con el reloj de su tienda. Aqui se comprueba que el tick de
 * un caso, de sus pasos y de cada linea de su bitacora sale de ahi, que todo lo de una misma operacion lleva el
 * mismo tick, y que el reloj de una tienda no mueve el de ninguna otra.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelojDeLaTiendaTest {

    private final AtomicInteger contador = new AtomicInteger();

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ConfiguracionTiendaService configuracionTiendaService;

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

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        empresaId = empresaService.registrar("Tienda con reloj", "900777333-1", "contacto@reloj.com",
                "Administradora", "admin@reloj.com", "clave12345").id();
        adminId = usuarioRepository.findByEmail("admin@reloj.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("Una tienda nace en el tick cero y moviendo el reloj a mano")
    void tiendaNueva_empiezaEnCeroYEnManual() {
        Long otraId = empresaService.registrar("Tienda recien nacida", "900777333-2", "contacto@nueva.com",
                "Otro", "admin@nueva.com", "clave12345").id();

        assertThat(configuracionTiendaService.obtener(otraId).reloj()).isZero();
        assertThat(configuracionTiendaService.obtener(otraId).modoSimulacion()).isEqualTo(ModoSimulacion.MANUAL);
    }

    @Test
    @DisplayName("El caso se fecha con el reloj de su tienda, y todo lo de una operacion lleva el mismo tick")
    void elCaso_seFechaConElRelojDeSuTienda() {
        Long procesoId = publicar("Order fulfillment with a clock");
        int antes = configuracionTiendaService.reloj(empresaId);
        int alAbrir = configuracionTiendaService.avanzarReloj(empresaId, 5);

        CasoResponse caso = casoService.abrir(empresaId, adminId, procesoId, "ORD-RELOJ-1", null);

        assertThat(alAbrir).isEqualTo(antes + 5);
        assertThat(caso.tickInicio()).isEqualTo(alAbrir);
        assertThat(casoService.eventos(empresaId, caso.id())).extracting(EventoCasoResponse::tick)
                .containsOnly(alAbrir);
        assertThat(casoService.obtener(empresaId, caso.id()).pasos()).extracting(PasoDelCasoResponse::tickInicio)
                .containsOnly(alAbrir);
    }

    @Test
    @DisplayName("Completar una tarea mas tarde deja el caso cerrado en el tick de ese momento, no en el de antes")
    void completarMasTarde_cierraElCasoEnSuTick() {
        Long procesoId = publicar("Order fulfillment completed later");
        configuracionTiendaService.avanzarReloj(empresaId, 2);
        CasoResponse caso = casoService.abrir(empresaId, adminId, procesoId, "ORD-RELOJ-2", null);
        int alAbrir = caso.tickInicio();

        configuracionTiendaService.avanzarReloj(empresaId, 7);
        TareaResponse tarea = bandeja(procesoId).getFirst();
        tareaService.completar(empresaId, adminId, tarea.id(), null);

        CasoDetalleResponse terminado = casoService.obtener(empresaId, caso.id());
        assertThat(terminado.caso().tickInicio()).isEqualTo(alAbrir);
        assertThat(terminado.caso().tickFin()).isEqualTo(alAbrir + 7);
        assertThat(terminado.pasos()).filteredOn(paso -> paso.nodoNombre().equals("Receive order"))
                .singleElement()
                .returns(alAbrir, PasoDelCasoResponse::tickInicio)
                .returns(alAbrir + 7, PasoDelCasoResponse::tickFin);
        assertThat(casoService.eventos(empresaId, caso.id())).extracting(EventoCasoResponse::tick)
                .containsSequence(alAbrir, alAbrir + 7);
    }

    @Test
    @DisplayName("Mover el reloj de una tienda no mueve el de otra")
    void elReloj_esDeCadaTienda() {
        Long otraId = empresaService.registrar("Tienda con su propio reloj", "900777333-3", "contacto@propia.com",
                "Otro", "admin@propia.com", "clave12345").id();
        int antes = configuracionTiendaService.reloj(otraId);

        configuracionTiendaService.avanzarReloj(empresaId, 3);

        assertThat(configuracionTiendaService.reloj(otraId)).isEqualTo(antes);
    }

    @Test
    @DisplayName("El modo de simulacion se cambia sin tocar la politica de estructura, y se puede no mandarlo")
    void elModo_seCambiaSolo() {
        var antes = configuracionTiendaService.obtener(empresaId);

        var conAutomatico = configuracionTiendaService.editar(empresaId, adminId, antes.politicaEstructura(),
                ModoSimulacion.AUTOMATICO, null, antes.version());
        var sinModo = configuracionTiendaService.editar(empresaId, adminId, antes.politicaEstructura(), null, null,
                conAutomatico.version());

        assertThat(conAutomatico.modoSimulacion()).isEqualTo(ModoSimulacion.AUTOMATICO);
        assertThat(sinModo.modoSimulacion()).isEqualTo(ModoSimulacion.AUTOMATICO);
        assertThat(sinModo.politicaEstructura()).isEqualTo(antes.politicaEstructura());
        configuracionTiendaService.editar(empresaId, adminId, antes.politicaEstructura(), ModoSimulacion.MANUAL,
                null, sinModo.version());
    }

    /** Lo mas corto que se puede publicar y ejecutar: un inicio, una tarea de ventas y un fin. */
    private Long publicar(String nombre) {
        int numero = contador.incrementAndGet();
        Long procesoId = procesoService.crear(empresaId, adminId, nombre + " " + numero,
                "De la compra a la entrega", "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long ventas = rolProcesoService.crear(empresaId, adminId, "Sales " + numero, null).id();
        Long lane = laneService.crear(empresaId, adminId, tienda, "Sales", ventas).id();

        Long inicio = eventoService.crear(empresaId, adminId, lane, "Order received", TipoEvento.INICIO, 20, 80).id();
        Long recibir = actividadService.crear(empresaId, adminId, lane, "Receive order",
                "Validate the cart and the address.", TipoActividad.USUARIO, 160, 80).id();
        Long fin = eventoService.crear(empresaId, adminId, lane, "Order handled", TipoEvento.FIN, 320, 80).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(recibir, fin));

        procesoService.cambiarEstado(empresaId, procesoId, adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, procesoId, false).version());
        return procesoId;
    }

    private List<TareaResponse> bandeja(Long procesoId) {
        return tareaService.bandeja(empresaId, adminId, false, null, procesoId, null, Paginacion.de(0, 10))
                .content();
    }
}
