package com.facimus.procesos.modelado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.PrecondicionFallidaException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.model.MotivoDeRevision;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;
import com.facimus.procesos.modelado.service.PoolService;
import com.facimus.procesos.security.IdempotencyFilter;
import com.facimus.procesos.security.SesionEnCookies;

import tools.jackson.databind.json.JsonMapper;

/** PR 40: guardar desde un editor: el conflicto, lo que no escribe, las revisiones y las cabeceras de HTTP. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {"modelado.revisiones.intervalo=PT0S", "modelado.revisiones.maximo=3"})
class GuardadoDelModeloIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String BPMN = "application/bpmn+xml";

    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
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
    private ActividadService actividadService;
    @Autowired
    private EventoService eventoService;
    @Autowired
    private ArcoService arcoService;
    @Autowired
    private ModeloBpmnService modeloBpmnService;
    @Autowired
    private GuardadoDelModeloService guardado;
    @Autowired
    private JdbcTemplate jdbc;

    private Long empresaId;
    private Long adminId;
    private Long procesoId;
    private String correo;
    private ActividadResponse tarea;

    @BeforeEach
    void modelar() {
        int numero = ++tiendas;
        correo = "admin" + numero + "@guardado.test";
        empresaId = empresaService.registrar("Tienda que guarda " + numero, "9479" + numero + "000-1", correo,
                "Gina Guarda", correo, CLAVE).id();
        adminId = usuarioRepository.findByEmail(correo).orElseThrow().getId();
        procesoId = procesoService.crear(empresaId, adminId, "Order fulfillment", "Checkout to delivery",
                "Fulfillment").id();
        Long tienda = poolService.listarPorProceso(empresaId, procesoId).getFirst().id();
        Long ventas = laneService.crear(empresaId, adminId, tienda, "Sales",
                rolProcesoService.crear(empresaId, adminId, "Sales", null).id()).id();
        Long inicio = eventoService.crear(empresaId, adminId, ventas, "Start", TipoEvento.INICIO, 0, 0).id();
        tarea = actividadService.crear(empresaId, adminId, ventas, "Review", null, TipoActividad.USUARIO, 180, 0);
        Long fin = eventoService.crear(empresaId, adminId, ventas, "End", TipoEvento.FIN, 360, 0).id();
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(inicio, tarea.id()));
        arcoService.crear(empresaId, adminId, DatosDeArco.entre(tarea.id(), fin));
    }

    @Test
    @DisplayName("Dos pestanas guardan: la segunda recibe 412 con la huella de ahora y lo que cambio")
    void dosPestanas() {
        String leido = xml();
        String huellaLeida = huella(leido);

        ImportacionResponse primera = guardar(leido.replace("Review", "Review first"), huellaLeida);
        assertThat(primera.editados()).isPositive();

        assertThatThrownBy(() -> guardar(leido.replace("Review", "Review second"), huellaLeida))
                .isInstanceOfSatisfying(PrecondicionFallidaException.class, error -> {
                    assertThat(error.getDetalle()).containsEntry("huellaActual", primera.huella());
                    assertThat((List<?>) error.getDetalle().get("ultimosCambios")).isNotEmpty();
                });
        assertThat(xml()).contains("Review first").doesNotContain("Review second");
    }

    @Test
    @DisplayName("La huella de la respuesta es la siguiente If-Match, y un asterisco no compara")
    void huellaEncadenada() {
        String leido = xml();
        ImportacionResponse primera = guardar(leido.replace("Review", "One"), huella(leido));

        ImportacionResponse segunda = guardar(xml().replace("One", "Two"), "\"" + primera.huella() + "\"");

        assertThat(segunda.huella()).isEqualTo(huella(xml()));
        assertThat(guardar(xml().replace("Two", "Three"), "*").editados()).isPositive();
    }

    @Test
    @DisplayName("Si el archivo no cambia nada no se escribe nada, ni en importaciones ni en la bitacora")
    void sinCambios_noEscribe() {
        String leido = xml();
        int importaciones = contar("importaciones");
        int historial = contar("historial_cambios");

        ImportacionResponse igual = guardar(leido, huella(leido));
        ImportacionResponse reformateado = guardar(leido.replace("\n  ", "\n    "), huella(leido));

        assertThat(igual.editados() + igual.creados() + igual.dadosDeBaja()).isZero();
        assertThat(igual.huella()).isEqualTo(huella(leido));
        assertThat(reformateado.editados() + reformateado.creados() + reformateado.dadosDeBaja()).isZero();
        assertThat(contar("importaciones")).isEqualTo(importaciones);
        assertThat(contar("historial_cambios")).isEqualTo(historial);
        assertThat(guardado.revisiones(empresaId, procesoId)).isEmpty();
    }

    @Test
    @DisplayName("Restaurar devuelve la huella de la revision, aunque lo que tenia ya se hubiera borrado")
    void restaurar() {
        RevisionBorradorResponse punto = guardado.puntoDeGuardado(empresaId, adminId, procesoId,
                MotivoDeRevision.CIERRE);
        actividadService.eliminar(empresaId, adminId, tarea.id());
        assertThat(huella(xml())).isNotEqualTo(punto.huella());

        ImportacionResponse restaurado = guardado.restaurar(empresaId, adminId, RolAcceso.ADMINISTRADOR, procesoId,
                punto.id());

        assertThat(restaurado.huella()).isEqualTo(punto.huella());
        assertThat(huella(xml())).isEqualTo(punto.huella());
        assertThat(guardado.revisiones(empresaId, procesoId)).extracting(RevisionBorradorResponse::motivo)
                .containsExactly(MotivoDeRevision.ANTES_DE_RESTAURAR, MotivoDeRevision.CIERRE);
    }

    @Test
    @DisplayName("Un punto de guardado sin cambios devuelve el ultimo, y su XML se descarga como estaba")
    void puntoDeGuardado() {
        String xml = xml();
        RevisionBorradorResponse uno = guardado.puntoDeGuardado(empresaId, adminId, procesoId, MotivoDeRevision.CIERRE);
        RevisionBorradorResponse otro = guardado.puntoDeGuardado(empresaId, adminId, procesoId,
                MotivoDeRevision.CIERRE);

        assertThat(otro.id()).isEqualTo(uno.id());
        assertThat(guardado.modelo(empresaId, procesoId, uno.id())).isEqualTo(xml);
    }

    @Test
    @DisplayName("Guardar deja revisiones, y las mas viejas se van al pasar del tope")
    void revisionesConTope() {
        String actual = "Review";
        for (int vuelta = 0; vuelta < 5; vuelta++) {
            String nuevo = "Name" + vuelta;
            guardar(xml().replace(actual, nuevo), null);
            actual = nuevo;
        }

        List<RevisionBorradorResponse> revisiones = guardado.revisiones(empresaId, procesoId);

        assertThat(revisiones).hasSize(3);
        assertThat(revisiones.getFirst().motivo()).isEqualTo(MotivoDeRevision.AUTOGUARDADO);
        assertThat(revisiones.getFirst().huella()).isEqualTo(huella(xml()));
    }

    @Test
    @DisplayName("Una revision de otro proceso o de otra tienda no se ve")
    void aislamiento() {
        RevisionBorradorResponse punto = guardado.puntoDeGuardado(empresaId, adminId, procesoId,
                MotivoDeRevision.CIERRE);
        Long otroProceso = procesoService.crear(empresaId, adminId, "Other", "Another", "Misc").id();
        int numero = ++tiendas;
        String otroCorreo = "admin" + numero + "@guardado.test";
        Long otraTienda = empresaService.registrar("Otra tienda " + numero, "9479" + numero + "000-1", otroCorreo,
                "Otra", otroCorreo, CLAVE).id();

        assertThatThrownBy(() -> guardado.modelo(empresaId, otroProceso, punto.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> guardado.revisiones(otraTienda, procesoId))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HTTP: el modelo y el diagrama llevan ETag, If-None-Match responde 304 y un If-Match viejo 412")
    void cabeceras() throws Exception {
        String token = login();
        String leido = xml();
        String etiqueta = "\"" + huella(leido) + "\"";

        mockMvc.perform(get("/api/v1/procesos/{id}/modelo", procesoId).with(SesionEnCookies.conSesion(token)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, etiqueta));
        mockMvc.perform(get("/api/v1/procesos/{id}/modelo", procesoId).with(SesionEnCookies.conSesion(token))
                .header(HttpHeaders.IF_NONE_MATCH, etiqueta)).andExpect(status().isNotModified());
        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId).with(SesionEnCookies.conSesion(token)))
                .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.ETAG));

        mockMvc.perform(put("/api/v1/procesos/{id}/modelo", procesoId).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(BPMN).header(HttpHeaders.IF_MATCH, "\"otra\"")
                .content(leido.replace("Review", "Changed")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.huellaActual").value(huella(leido)));
        mockMvc.perform(put("/api/v1/procesos/{id}/modelo", procesoId).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(BPMN).header(HttpHeaders.IF_MATCH, etiqueta)
                .content(leido.replace("Review", "Changed")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + huella(xml()) + "\""));
    }

    @Test
    @DisplayName("HTTP: un recurso editable lleva su version en el ETag, y un If-Match viejo responde 412")
    void versionEnCabeceras() throws Exception {
        String token = login();
        mockMvc.perform(get("/api/v1/actividades/{id}", tarea.id()).with(SesionEnCookies.conSesion(token)))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG, "\"" + tarea.version() + "\""));

        String cuerpo = """
                {"nombre":"Review","tipoActividad":"USUARIO","posicionX":180,"posicionY":0,"version":%d}
                """;
        mockMvc.perform(put("/api/v1/actividades/{id}", tarea.id()).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"99\"").content(cuerpo.formatted(tarea.version())))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(put("/api/v1/actividades/{id}", tarea.id()).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_MATCH, "\"99\"").content(cuerpo.formatted(99)))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(put("/api/v1/actividades/{id}", tarea.id()).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.formatted(99)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HTTP: reintentar un autoguardado con la misma clave no vuelve a aplicarlo")
    void idempotencia() throws Exception {
        String token = login();
        String cambiado = xml().replace("Review", "Changed");

        mockMvc.perform(put("/api/v1/procesos/{id}/modelo", procesoId).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(BPMN).header(IdempotencyFilter.CABECERA, "guardado-1")
                .content(cambiado))
                .andExpect(status().isOk()).andExpect(header().doesNotExist(IdempotencyFilter.REPETIDA));
        int importaciones = contar("importaciones");
        mockMvc.perform(put("/api/v1/procesos/{id}/modelo", procesoId).with(SesionEnCookies.conSesion(token))
                .with(SesionEnCookies.conCsrf()).contentType(BPMN).header(IdempotencyFilter.CABECERA, "guardado-1")
                .content(cambiado))
                .andExpect(status().isOk()).andExpect(header().string(IdempotencyFilter.REPETIDA, "true"));

        assertThat(contar("importaciones")).isEqualTo(importaciones);
    }

    private ImportacionResponse guardar(String xml, String ifMatch) {
        return guardado.guardar(empresaId, adminId, RolAcceso.ADMINISTRADOR, procesoId,
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), ifMatch);
    }

    private String xml() {
        return modeloBpmnService.exportar(empresaId, procesoId).xml();
    }

    private static String huella(String xml) {
        return Huella.de(xml.getBytes(StandardCharsets.UTF_8));
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla + " where empresa_id = ?", Integer.class, empresaId);
    }

    private String login() throws Exception {
        return SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(correo, CLAVE))))
                .andExpect(status().isOk()));
    }
}
