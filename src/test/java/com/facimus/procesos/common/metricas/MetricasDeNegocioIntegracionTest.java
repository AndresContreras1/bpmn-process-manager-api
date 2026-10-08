package com.facimus.procesos.common.metricas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.service.CasoService;
import com.facimus.procesos.ejecucion.service.TareaService;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
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
import com.facimus.procesos.security.SesionEnCookies;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Los contadores del negocio con la aplicacion entera: un login que falla, una version que se publica y un caso que
 * se abre, avanza y termina suben cada uno su serie. Otras pruebas comparten el contexto y cuentan lo suyo, asi que
 * cada prueba mira cuanto subio su contador, no cuanto vale.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricasDeNegocioIntegracionTest {

    private static final String ADMIN = "admin@metricas.com";
    private static final String CLAVE = "clave12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry registro;

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

    private Long empresaId;
    private Long adminId;

    @BeforeAll
    void registrarLaTienda() {
        empresaId = empresaService.registrar("Tienda que se mide", "900414243-1", "contacto@metricas.com",
                "Administradora", ADMIN, CLAVE).id();
        adminId = usuarioRepository.findByEmail(ADMIN).orElseThrow().getId();
    }

    @Test
    @DisplayName("Un login con la clave equivocada cuenta como fallo por credenciales, y el bloqueado como bloqueo")
    void loginFallido_cuentaPorMotivo() throws Exception {
        double credenciales = cuenta("login.fallidos", "motivo", "credenciales");
        double bloqueados = cuenta("login.fallidos", "motivo", "bloqueado");

        for (int intento = 0; intento < 5; intento++) {
            entrarCon("clave-equivocada").andExpect(status().isUnauthorized());
        }
        entrarCon("clave-equivocada").andExpect(status().isTooManyRequests());

        assertThat(cuenta("login.fallidos", "motivo", "credenciales") - credenciales).isEqualTo(5);
        assertThat(cuenta("login.fallidos", "motivo", "bloqueado") - bloqueados).isEqualTo(1);
    }

    @Test
    @DisplayName("Publicar cuenta una version, y un caso que se abre, completa su tarea y termina cuenta cada paso")
    void publicarYCorrerUnCaso_cuentaCadaEvento() {
        double publicadas = cuenta("versiones.publicadas");
        double abiertos = cuenta("casos.eventos", "tipo", "caso_abierto");
        double completadas = cuenta("casos.eventos", "tipo", "tarea_completada");
        double terminados = cuenta("casos.eventos", "tipo", "caso_terminado");

        Long procesoId = publicarUnProcesoDeUnaTarea();
        casoService.abrir(empresaId, adminId, procesoId, "ORD-M1", Map.of());
        List<TareaResponse> bandeja = tareaService.bandeja(empresaId, adminId, false, null, procesoId, null,
                PageRequest.of(0, 10)).content();
        tareaService.completar(empresaId, adminId, bandeja.getFirst().id(), Map.of());

        assertThat(cuenta("versiones.publicadas") - publicadas).isEqualTo(1);
        assertThat(cuenta("casos.eventos", "tipo", "caso_abierto") - abiertos).isEqualTo(1);
        assertThat(cuenta("casos.eventos", "tipo", "tarea_completada") - completadas).isEqualTo(1);
        assertThat(cuenta("casos.eventos", "tipo", "caso_terminado") - terminados).isEqualTo(1);
        assertThat(registro.find("procesos.motor.avanzar").tag("estado", "terminado").timer()).isNotNull();
        assertThat(registro.find("procesos.diagnostico").tag("uso", "publicacion").timer()).isNotNull();
    }

    @Test
    @DisplayName("Lo que no se guarda no se cuenta: publicar un proceso que no pasa el diagnostico no suma")
    void publicacionRechazada_noCuenta() {
        double publicadas = cuenta("versiones.publicadas");
        ProcesoResponse vacio = procesoService.crear(empresaId, adminId, "Sin diagrama", "Nada que correr", "Ops");

        try {
            procesoService.cambiarEstado(empresaId, vacio.id(), adminId, EstadoProceso.PUBLICADO, vacio.version());
        } catch (ReglaNegocioException esperada) {
            // El diagnostico lo rechaza: no hay nada que publicar.
        }

        assertThat(cuenta("versiones.publicadas") - publicadas).isZero();
    }

    /** Un inicio, una tarea de usuario y un fin, en la lane de un rol: lo minimo que se publica y se corre. */
    private Long publicarUnProcesoDeUnaTarea() {
        ProcesoResponse proceso = procesoService.crear(empresaId, adminId, "Medido", "Una tarea", "Ops");
        Long pool = poolService.listarPorProceso(empresaId, proceso.id()).getFirst().id();
        Long rol = rolProcesoService.crear(empresaId, adminId, "Operaciones", null).id();
        Long lane = laneService.crear(empresaId, adminId, pool, "Operaciones", rol).id();
        Long inicio = eventoService.crear(empresaId, adminId, lane, "Start", TipoEvento.INICIO, 20, 80).id();
        Long tarea = actividadService.crear(empresaId, adminId, lane, "Review", "Look at it.",
                TipoActividad.USUARIO, 160, 80).id();
        Long fin = eventoService.crear(empresaId, adminId, lane, "End", TipoEvento.FIN, 320, 80).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, tarea));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(tarea, fin));
        procesoService.cambiarEstado(empresaId, proceso.id(), adminId, EstadoProceso.PUBLICADO,
                procesoService.obtener(empresaId, proceso.id(), false).version());
        return proceso.id();
    }

    private ResultActions entrarCon(String clave) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN + "\",\"password\":\"" + clave + "\"}"));
    }

    /** Cuanto vale el contador ahora; si todavia nadie lo toco, cero. */
    private double cuenta(String nombre, String... etiquetas) {
        Counter contador = registro.find(nombre).tags(etiquetas).counter();
        return contador == null ? 0 : contador.count();
    }
}
