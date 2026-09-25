package com.facimus.procesos.security;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.CompartirProcesoRequest;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.RolProcesoService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.service.ActividadService;
import com.facimus.procesos.modelado.service.ArcoService;
import com.facimus.procesos.modelado.service.DatosDeArco;
import com.facimus.procesos.modelado.service.EventoService;
import com.facimus.procesos.modelado.service.LaneService;
import com.facimus.procesos.modelado.service.PoolService;

import tools.jackson.databind.json.JsonMapper;

/**
 * HU-23, la unica excepcion al aislamiento (HU-03): la empresa duena comparte un proceso y la invitada solo lee su
 * diagrama. Cualquier otra empresa, y cualquier cambio, siguen sin encontrar el proceso.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcesosCompartidosIntegracionTest {

    private static final String CLAVE = "clave12345";
    private static final String NIT_DUENA = "900555666-7";
    private static final String NIT_INVITADA = "900555777-8";
    private static final String NIT_AJENA = "900555888-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

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

    private Long duenaId;
    private Long invitadaId;
    private Long adminDuenaId;
    private String tokenDuena;
    private String tokenEditoraDuena;
    private String tokenInvitada;
    private String tokenAjena;

    @BeforeAll
    void registrarTresTiendas() throws Exception {
        duenaId = empresaService.registrar("Tienda duena", NIT_DUENA, "contacto@duena.com", "Administradora",
                "admin@duena.com", CLAVE).id();
        invitadaId = empresaService.registrar("Operador logistico", NIT_INVITADA, "contacto@invitada.com",
                "Administrador", "admin@invitada.com", CLAVE).id();
        empresaService.registrar("Tienda ajena", NIT_AJENA, "contacto@ajena.com", "Administrador", "admin@ajena.com",
                CLAVE);
        adminDuenaId = usuarioRepository.findByEmail("admin@duena.com").orElseThrow().getId();
        usuarioService.crearColaborador(duenaId, null, "Editora", "editora@duena.com", CLAVE, RolAcceso.EDITOR);

        tokenDuena = login("admin@duena.com");
        tokenEditoraDuena = login("editora@duena.com");
        tokenInvitada = login("admin@invitada.com");
        tokenAjena = login("admin@ajena.com");
    }

    @Test
    @DisplayName("La invitada ve el proceso en su lista y lee la version publicada, marcada como compartida")
    void compartir_laInvitadaLeeElDiagrama() throws Exception {
        Long procesoId = nuevoProceso("Order fulfillment");
        modelarUnProcesoPublicable(procesoId);

        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/api/v1/procesos/" + procesoId + "/compartidos/" + invitadaId))
                .andExpect(jsonPath("$.nombre").value("Operador logistico"));

        mockMvc.perform(get("/api/v1/procesos/compartidos-conmigo").header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.proceso.id == " + procesoId + ")].empresaPropietariaNombre")
                        .value("Tienda duena"));
        // El historial de la duena avisa de que vera la invitada: hoy, nada, porque no hay version publicada.
        mockMvc.perform(get("/api/v1/procesos/{id}/historial", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDuena)))
                .andExpect(jsonPath("$[*].descripcionCambio", hasItems(
                        "Proceso compartido en solo lectura con Operador logistico. No verá nada hasta que el "
                                + "proceso se publique.")));

        // D2: mientras no haya una version publicada, la invitada no tiene nada que leer.
        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                .andExpect(status().isNotFound());
        publicar(procesoId);

        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compartido").value(true))
                .andExpect(jsonPath("$.proceso.nombre").value("Order fulfillment"))
                .andExpect(jsonPath("$.proceso.versionPublicada").value(1))
                .andExpect(jsonPath("$.pools[0].tipoParticipante").value("EMPRESA"));
        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDuena)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compartido").value(false));
    }

    @Test
    @DisplayName("Compartir es solo leer: la invitada no ve el detalle ni los pools, y no puede cambiar nada")
    void compartir_noDejaCambiarNada() throws Exception {
        Long procesoId = nuevoProceso("Returns and refunds");
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena)).andExpect(status().isCreated());

        List<MockHttpServletRequestBuilder> peticiones = List.of(
                get("/api/v1/procesos/{id}", procesoId),
                get("/api/v1/procesos/{id}/pools", procesoId),
                get("/api/v1/procesos/{id}/compartidos", procesoId),
                put("/api/v1/procesos/{id}", procesoId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Cambiado\",\"descripcion\":\"Desde la invitada\",\"categoria\":\"X\","
                                + "\"version\":0}"),
                post("/api/v1/procesos/{id}/pools", procesoId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Intruso\",\"tipoParticipante\":\"PROVEEDOR\",\"cajaNegra\":true}"),
                delete("/api/v1/procesos/{id}/compartidos/{invitada}", procesoId, invitadaId));
        for (MockHttpServletRequestBuilder peticion : peticiones) {
            mockMvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/v1/procesos/{id}", procesoId).header(HttpHeaders.AUTHORIZATION, bearer(tokenDuena)))
                .andExpect(jsonPath("$.proceso.nombre").value("Returns and refunds"));
    }

    @Test
    @DisplayName("Una tienda a la que no se le compartio el proceso no lo encuentra, ni por su id ni en su lista")
    void empresaNoInvitada_noLoEncuentra() throws Exception {
        Long procesoId = nuevoProceso("Payments");
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenAjena)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/procesos/compartidos-conmigo").header(HttpHeaders.AUTHORIZATION, bearer(tokenAjena)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Dejar de compartir quita el acceso, y la bitacora del proceso guarda las dos cosas")
    void dejarDeCompartir_quitaElAcceso() throws Exception {
        Long procesoId = nuevoProceso("Inventory count");
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena)).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/procesos/{id}/compartidos/{invitada}", procesoId, invitadaId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDuena)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/procesos/{id}/compartidos", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDuena)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/procesos/{id}/historial", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenDuena)))
                .andExpect(jsonPath("$[*].descripcionCambio", hasItems(
                        "Proceso compartido en solo lectura con Operador logistico. No verá nada hasta que el "
                                + "proceso se publique.",
                        "Se dejó de compartir el proceso con Operador logistico.")));
    }

    @Test
    @DisplayName("Un proceso eliminado deja de verse tambien para la invitada")
    void procesoEliminado_dejaDeVerseParaLaInvitada() throws Exception {
        Long procesoId = nuevoProceso("Supplier onboarding");
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena)).andExpect(status().isCreated());

        procesoService.eliminarLogico(duenaId, procesoId, adminDuenaId);

        mockMvc.perform(get("/api/v1/procesos/{id}/diagrama", procesoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/procesos/compartidos-conmigo").header(HttpHeaders.AUTHORIZATION, bearer(tokenInvitada)))
                .andExpect(jsonPath("$.content[?(@.proceso.id == " + procesoId + ")]").isEmpty());
    }

    @Test
    @DisplayName("No se comparte con la propia tienda, con un NIT que no existe, sin NIT, siendo editora ni dos veces")
    void compartir_reglas() throws Exception {
        Long procesoId = nuevoProceso("Customer support");

        mockMvc.perform(compartir(procesoId, NIT_DUENA, tokenDuena))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Un proceso no se comparte con su propia empresa."));
        mockMvc.perform(compartir(procesoId, "900000000-0", tokenDuena))
                .andExpect(status().isNotFound());
        mockMvc.perform(compartir(procesoId, "", tokenDuena))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nit").exists());
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenEditoraDuena))
                .andExpect(status().isForbidden());
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena))
                .andExpect(status().isCreated());
        mockMvc.perform(compartir(procesoId, NIT_INVITADA, tokenDuena))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El proceso ya está compartido con Operador logistico."));
    }

    private Long nuevoProceso(String nombre) {
        return procesoService.crear(duenaId, adminDuenaId, nombre, "Proceso de la tienda duena", "Operations").id();
    }

    /** Lo minimo que el diagnostico da por bueno: una lane, un inicio, un paso y un fin, enlazados. */
    private void modelarUnProcesoPublicable(Long procesoId) {
        Long tienda = poolService.listarPorProceso(duenaId, procesoId).getFirst().id();
        Long rolId = rolProcesoService.crear(duenaId, adminDuenaId, "Ventas de " + procesoId, null).id();
        Long laneId = laneService.crear(duenaId, adminDuenaId, tienda, "Sales", rolId).id();
        Long inicio = eventoService.crear(duenaId, adminDuenaId, laneId, "Order received", TipoEvento.INICIO,
                40, 80).id();
        Long recibir = actividadService.crear(duenaId, adminDuenaId, laneId, "Receive order", "Check the cart",
                TipoActividad.USUARIO, 180, 80).id();
        Long fin = eventoService.crear(duenaId, adminDuenaId, laneId, "Order accepted", TipoEvento.FIN, 340, 80).id();
        arcoService.crear(duenaId, adminDuenaId, DatosDeArco.entre(inicio, recibir));
        arcoService.crear(duenaId, adminDuenaId, DatosDeArco.entre(recibir, fin));
    }

    private void publicar(Long procesoId) {
        Long version = procesoService.obtener(duenaId, procesoId, false).version();
        procesoService.cambiarEstado(duenaId, procesoId, adminDuenaId, EstadoProceso.PUBLICADO, version);
    }

    private MockHttpServletRequestBuilder compartir(Long procesoId, String nit, String token) throws Exception {
        return post("/api/v1/procesos/{id}/compartidos", procesoId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new CompartirProcesoRequest(nit)));
    }

    private String login(String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
