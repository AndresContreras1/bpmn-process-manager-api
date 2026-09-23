package com.facimus.procesos.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;

import tools.jackson.databind.json.JsonMapper;

/** Matriz de permisos por rol de acceso que aplica SecurityConfig, con la aplicacion y los tokens reales. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutorizacionPorRolTest {

    private static final String ADMIN = "admin@autorizacion.com";
    private static final String EDITOR = "editor@autorizacion.com";
    private static final String LECTOR = "lector@autorizacion.com";
    private static final String CLAVE = "clave12345";

    // La autorizacion se decide antes del controller: con un id que no existe, la peticion que pasa
    // la regla llega al service y responde 404; la que no la pasa responde 403.
    private static final long ID_INEXISTENTE = Long.MAX_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    private final Map<RolAcceso, String> correos = Map.of(RolAcceso.ADMINISTRADOR, ADMIN, RolAcceso.EDITOR, EDITOR,
            RolAcceso.SOLO_LECTURA, LECTOR);
    private final Map<RolAcceso, String> tokens = new EnumMap<>(RolAcceso.class);

    @BeforeAll
    void iniciarSesionConCadaRol() throws Exception {
        Long empresaId = empresaService.registrar("Tienda Autorizacion", "900111222-3", "contacto@autorizacion.com",
                "Administrador", ADMIN, CLAVE).id();
        usuarioService.crearColaborador(empresaId, "Editor", EDITOR, CLAVE, RolAcceso.EDITOR);
        usuarioService.crearColaborador(empresaId, "Lector", LECTOR, CLAVE, RolAcceso.SOLO_LECTURA);

        for (RolAcceso rol : RolAcceso.values()) {
            tokens.put(rol, login(correos.get(rol), CLAVE));
        }
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            # Consultar: cualquier rol (HU-07, HU-20, HU-24)
            SOLO_LECTURA  | GET    | /api/v1/procesos               | 200
            SOLO_LECTURA  | GET    | /api/v1/roles                  | 200
            SOLO_LECTURA  | GET    | /api/v1/procesos/{id}/diagrama | 404
            SOLO_LECTURA  | GET    | /api/v1/empresas/actual        | 200
            SOLO_LECTURA  | GET    | /api/v1/empresas/{id}          | 404

            # Usuarios: solo el administrador (HU-02)
            ADMINISTRADOR | GET    | /api/v1/usuarios               | 200
            ADMINISTRADOR | GET    | /api/v1/usuarios/{id}          | 404
            EDITOR        | GET    | /api/v1/usuarios               | 403
            EDITOR        | GET    | /api/v1/usuarios/{id}          | 403

            # Roles de proceso: los modifica solo el administrador (HU-17 a HU-19)
            ADMINISTRADOR | DELETE | /api/v1/roles/{id}             | 404
            EDITOR        | POST   | /api/v1/roles                  | 403
            EDITOR        | DELETE | /api/v1/roles/{id}             | 403

            # Procesos: administrador y editor modifican (HU-04, HU-05), solo el administrador elimina (HU-06)
            EDITOR        | PATCH  | /api/v1/procesos/{id}          | 404
            SOLO_LECTURA  | POST   | /api/v1/procesos               | 403
            SOLO_LECTURA  | PATCH  | /api/v1/procesos/{id}          | 403
            ADMINISTRADOR | DELETE | /api/v1/procesos/{id}          | 404
            EDITOR        | DELETE | /api/v1/procesos/{id}          | 403

            # Compartir procesos: solo el administrador comparte y deja de compartir (HU-23)
            EDITOR        | POST   | /api/v1/procesos/{id}/compartidos        | 403
            SOLO_LECTURA  | POST   | /api/v1/procesos/{id}/compartidos        | 403
            ADMINISTRADOR | DELETE | /api/v1/procesos/{id}/compartidos/424242 | 404
            EDITOR        | DELETE | /api/v1/procesos/{id}/compartidos/424242 | 403
            SOLO_LECTURA  | GET    | /api/v1/procesos/{id}/compartidos        | 404
            SOLO_LECTURA  | GET    | /api/v1/procesos/compartidos-conmigo     | 200

            # Revision con IA: la piden administrador y editor, porque cada una cuesta una llamada
            EDITOR        | POST   | /api/v1/procesos/{id}/revision           | 404
            SOLO_LECTURA  | POST   | /api/v1/procesos/{id}/revision           | 403

            # Modelado: solo el administrador elimina pools, lanes y mensajes (HU-21, HU-22, HU-25)
            ADMINISTRADOR | DELETE | /api/v1/pools/{id}             | 404
            ADMINISTRADOR | DELETE | /api/v1/lanes/{id}             | 404
            ADMINISTRADOR | DELETE | /api/v1/mensajes/{id}          | 404
            EDITOR        | DELETE | /api/v1/pools/{id}             | 403
            EDITOR        | DELETE | /api/v1/lanes/{id}             | 403
            EDITOR        | DELETE | /api/v1/mensajes/{id}          | 403
            SOLO_LECTURA  | DELETE | /api/v1/pools/{id}             | 403
            SOLO_LECTURA  | DELETE | /api/v1/lanes/{id}             | 403
            SOLO_LECTURA  | DELETE | /api/v1/mensajes/{id}          | 403
            SOLO_LECTURA  | PUT    | /api/v1/mensajes/{id}/correlacion | 403

            # Actividades, arcos y gateways: solo el administrador elimina (HU-10, HU-13, HU-16)
            ADMINISTRADOR | DELETE | /api/v1/actividades/{id}       | 404
            ADMINISTRADOR | DELETE | /api/v1/arcos/{id}             | 404
            ADMINISTRADOR | DELETE | /api/v1/gateways/{id}          | 404
            EDITOR        | DELETE | /api/v1/actividades/{id}       | 403
            EDITOR        | DELETE | /api/v1/arcos/{id}             | 403
            EDITOR        | DELETE | /api/v1/gateways/{id}          | 403

            # Cerrar sesion: cualquier rol (HU-03)
            SOLO_LECTURA  | POST   | /api/v1/auth/logout                   | 204
            """)
    void Autorizacion_peticionSegunRol_devuelveEstadoDeLaMatriz(RolAcceso rol, HttpMethod metodo, String ruta,
            int estadoEsperado) throws Exception {
        // El logout cierra la sesion de su token: ese caso abre una propia para no cerrar la que comparten los demas.
        String token = ruta.equals("/api/v1/auth/logout") ? login(correos.get(rol), CLAVE) : tokens.get(rol);
        var peticion = request(metodo, ruta, ID_INEXISTENTE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (metodo == HttpMethod.PATCH && ruta.equals("/api/v1/procesos/{id}")) {
            peticion.contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PUBLICADO\",\"version\":0}");
        }
        var resultado = mockMvc.perform(peticion).andExpect(status().is(estadoEsperado));
        // El titulo distingue de donde viene la respuesta: un 404 del service, no de una ruta que ya no existe;
        // un 403 de la regla de roles, no de otra barrera que tambien responda 403.
        if (estadoEsperado == HttpStatus.NOT_FOUND.value()) {
            resultado.andExpect(jsonPath("$.title").value("Recurso no encontrado"));
        } else if (estadoEsperado == HttpStatus.FORBIDDEN.value()) {
            resultado.andExpect(jsonPath("$.title").value("Sin permisos"));
        }
    }

    private String login(String email, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(respuesta).get("accessToken").asString();
    }
}
