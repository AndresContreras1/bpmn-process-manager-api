package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.repository.SegundoFactorRepository;
import com.facimus.procesos.gestion.service.EmpresaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 49: el segundo factor de punta a punta. Inscribir y confirmar la aplicacion de autenticacion, el login en dos
 * pasos con un token a medias que no llega a ningun endpoint de negocio, el codigo que no se usa dos veces, los codigos
 * de recuperacion, el limite de intentos, la reautenticacion con el codigo y quitarlo.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SegundoFactorIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SegundoFactorRepository segundoFactorRepository;

    @Autowired
    private TransactionTemplate transacciones;

    private String admin;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@segundo-factor.test";
        empresaService.registrar("Tienda con segundo factor " + numero, "9021" + numero + "000-1", admin,
                "Ana Segura", admin, CLAVE);
    }

    @Test
    @DisplayName("Inscribir da el secreto y su URI, y el login no lo pide hasta confirmarlo con un codigo bueno")
    void inscribirYConfirmar() throws Exception {
        String acceso = SesionEnCookies.acceso(login());
        JsonNode inscripcion = inscribir(acceso);
        assertThat(inscripcion.get("uri").asString()).startsWith("otpauth://totp/BPMN%20Process%20Manager:")
                .contains("secret=" + inscripcion.get("secreto").asString(), "issuer=BPMN%20Process%20Manager",
                        "digits=6", "period=30");
        estado(acceso).andExpect(jsonPath("$.totp").value(false));
        // Sin confirmar, el login sigue siendo de un paso.
        login().andExpect(jsonPath("$.segundoFactorPendiente").value(false));

        confirmar(acceso, "000000".equals(codigo(inscripcion, 0)) ? "111111" : "000000")
                .andExpect(status().isBadRequest());
        confirmar(acceso, codigo(inscripcion, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.codigos", hasSize(10)))
                .andExpect(jsonPath("$.codigos[0]").value(org.hamcrest.Matchers.matchesPattern("[a-z2-7]{5}-[a-z2-7]{5}")));

        estado(acceso).andExpect(jsonPath("$.totp").value(true)).andExpect(jsonPath("$.codigosRestantes").value(10));
        assertThat(jdbc.queryForObject("select secreto_cifrado from segundos_factores sf join usuarios u "
                + "on u.id = sf.usuario_id where u.email = ?", String.class, admin))
                .doesNotContain(inscripcion.get("secreto").asString());
        assertThat(jdbc.queryForList("select codigo_hash from codigos_de_recuperacion c join usuarios u "
                + "on u.id = c.usuario_id where u.email = ?", String.class, admin)).hasSize(10)
                .allMatch(huella -> huella.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("Con segundo factor, la clave deja un login a medias de cinco minutos, sin refresco ni sesion")
    void loginEnDosPasos() throws Exception {
        activar();

        ResultActions mitad = login().andExpect(jsonPath("$.segundoFactorPendiente").value(true))
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.usuario.email").value(admin))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.hasItem(startsWith(CookiesDeSesion.REFRESCO + "=;"))));
        String pendiente = SesionEnCookies.acceso(mitad);
        ApiPrincipal principal = jwtService.validar(pendiente).orElseThrow();
        assertThat(principal.segundoFactorPendiente()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from sesiones where codigo = ?", Integer.class,
                principal.sesion())).isZero();
        // F13: la web que se recarga a mitad del login sabe de quien es y que solo falta el codigo
        mockMvc.perform(get("/api/v1/auth/sesion").with(SesionEnCookies.conSesion(pendiente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(admin))
                .andExpect(jsonPath("$.segundoFactorPendiente").value(true))
                .andExpect(jsonPath("$.inactividadSegundos").doesNotExist());
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', textBlock = """
            GET    | /api/v1/procesos
            GET    | /api/v1/procesos?incluirInactivos=true
            GET    | /api/v1/procesos/compartidos-conmigo
            GET    | /api/v1/procesos/1
            GET    | /api/v1/procesos/1/diagrama
            GET    | /api/v1/procesos/1/diagnostico
            GET    | /api/v1/procesos/1/versiones
            GET    | /api/v1/procesos/1/versiones/1/diagrama
            GET    | /api/v1/procesos/1/compartidos
            GET    | /api/v1/procesos/1/bandeja-entrada
            GET    | /api/v1/procesos/1/tablero
            GET    | /api/v1/casos
            GET    | /api/v1/casos/1/eventos
            GET    | /api/v1/tareas?mias=true
            GET    | /api/v1/roles
            GET    | /api/v1/usuarios
            GET    | /api/v1/usuarios/1/roles-proceso
            GET    | /api/v1/empresas/actual
            GET    | /api/v1/empresas/actual/configuracion
            GET    | /api/v1/empresas/actual/historial
            GET    | /api/v1/eventos/1
            GET    | /api/v1/auth/segundo-factor
            POST   | /api/v1/procesos
            POST   | /api/v1/procesos/1/casos
            POST   | /api/v1/procesos/1/compartidos
            POST   | /api/v1/procesos/1/revision
            POST   | /api/v1/procesos/1/mensajes-entrantes
            POST   | /api/v1/casos/1/cancelar
            POST   | /api/v1/tareas/1/completar
            POST   | /api/v1/roles
            POST   | /api/v1/usuarios
            POST   | /api/v1/usuarios/1/restablecer-clave
            POST   | /api/v1/usuarios/1/anonimizar
            POST   | /api/v1/lanes/1/eventos
            POST   | /api/v1/empresas/actual/baja
            POST   | /api/v1/simulacion/tick
            POST   | /api/v1/auth/password
            POST   | /api/v1/auth/reautenticacion
            POST   | /api/v1/auth/verificacion
            POST   | /api/v1/auth/segundo-factor/totp
            POST   | /api/v1/auth/segundo-factor/codigos
            PUT    | /api/v1/empresas/actual/configuracion
            PUT    | /api/v1/mensajes/1/correlacion
            PUT    | /api/v1/usuarios/1/roles-proceso
            PATCH  | /api/v1/procesos/1
            PATCH  | /api/v1/casos/1/variables
            DELETE | /api/v1/procesos/1
            DELETE | /api/v1/roles/1
            DELETE | /api/v1/usuarios/1
            DELETE | /api/v1/usuarios/1/segundo-factor
            DELETE | /api/v1/auth/segundo-factor/totp
            DELETE | /api/v1/empresas/actual/baja
            """)
    @DisplayName("El login a medias no llega a ninguna ruta de la matriz: 401 \"Segundo factor requerido\"")
    void loginAMedias_noLlegaANada(HttpMethod metodo, String ruta) throws Exception {
        activar();
        String pendiente = SesionEnCookies.acceso(login());

        mockMvc.perform(request(metodo, ruta).with(SesionEnCookies.conSesion(pendiente))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Segundo factor requerido"));
    }

    @Test
    @DisplayName("El codigo de la aplicacion completa el login con los dos factores, y no sirve dos veces")
    void completarConElCodigo() throws Exception {
        JsonNode inscripcion = activar();
        String pendiente = SesionEnCookies.acceso(login());
        String codigo = codigo(inscripcion, 1);

        ResultActions completo = completar(pendiente, codigo).andExpect(status().isOk())
                .andExpect(jsonPath("$.segundoFactorPendiente").value(false));
        String acceso = SesionEnCookies.acceso(completo);
        assertThat(SesionEnCookies.refresco(completo)).isNotBlank();
        assertThat(jwtService.validarAcceso(acceso).orElseThrow().factores())
                .containsExactlyInAnyOrder(Factor.PASSWORD, Factor.TOTP);
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso))).andExpect(status().isOk());

        // El mismo codigo en otro login a medias ya no entra, y el token a medias que ya se uso tampoco sirve.
        completar(SesionEnCookies.acceso(login()), codigo).andExpect(status().isBadRequest());
        completar(pendiente, codigo(inscripcion, 1)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Aceptar un paso es atomico: el mismo paso, o uno anterior, no se acepta dos veces aunque se lea viejo")
    void aceptarPaso_esAtomico() throws Exception {
        activar();
        Long id = jdbc.queryForObject("select sf.id from segundos_factores sf join usuarios u on u.id = sf.usuario_id "
                + "where u.email = ?", Long.class, admin);
        long ultimo = jdbc.queryForObject("select ultimo_paso from segundos_factores where id = ?", Long.class, id);

        // Dos peticiones que leyeron el mismo ultimo paso llegan aqui con el mismo codigo: entra una sola.
        assertThat(aceptar(id, ultimo + 1)).isEqualTo(1);
        assertThat(aceptar(id, ultimo + 1)).isZero();
        assertThat(aceptar(id, ultimo)).isZero();
    }

    private int aceptar(Long id, long paso) {
        Integer filas = transacciones.execute(estado -> segundoFactorRepository.aceptarPaso(id, paso));
        return filas == null ? -1 : filas;
    }

    @Test
    @DisplayName("Un codigo de recuperacion entra una sola vez, se escriba como se escriba")
    void codigoDeRecuperacion() throws Exception {
        JsonNode inscripcion = activar();
        String recuperacion = codigosDe(inscripcion).get(3);

        String acceso = SesionEnCookies.acceso(completar(SesionEnCookies.acceso(login()),
                " " + recuperacion.toUpperCase().replace("-", "") + " ").andExpect(status().isOk()));
        assertThat(jwtService.validarAcceso(acceso).orElseThrow().factores())
                .containsExactlyInAnyOrder(Factor.PASSWORD, Factor.CODIGO_DE_RECUPERACION);
        estado(acceso).andExpect(jsonPath("$.codigosRestantes").value(9));

        completar(SesionEnCookies.acceso(login()), recuperacion).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Probar codigos cuenta con el limite del login: al quinto fallo responde 429")
    void limiteDeIntentos() throws Exception {
        activar();
        String pendiente = SesionEnCookies.acceso(login());
        for (int intento = 0; intento < 5; intento++) {
            completar(pendiente, "zzzzz-zzzzz").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("El código no es válido o ya se usó."));
        }
        completar(pendiente, "zzzzz-zzzzz").andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Con segundo factor, reautenticarse pide el codigo: la clave sola no basta")
    void reautenticarConElCodigo() throws Exception {
        JsonNode inscripcion = activar();
        String acceso = SesionEnCookies.acceso(completar(SesionEnCookies.acceso(login()), codigo(inscripcion, 1)));

        reautenticar(acceso, Map.of("password", CLAVE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("código")));
        String nueva = SesionEnCookies.acceso(reautenticar(acceso, Map.of("codigo", codigosDe(inscripcion).get(0)))
                .andExpect(status().isOk()));
        assertThat(jwtService.validarAcceso(nueva).orElseThrow().factores())
                .contains(Factor.CODIGO_DE_RECUPERACION, Factor.TOTP, Factor.PASSWORD);
    }

    @Test
    @DisplayName("Inscribir, quitar y renovar los codigos piden una verificacion de los ultimos diez minutos")
    void gestionarPideVerificacionReciente() throws Exception {
        String acceso = SesionEnCookies.acceso(login());
        ApiPrincipal principal = jwtService.validar(acceso).orElseThrow();
        String vieja = jwtService.generarToken(principal, java.util.EnumSet.of(Factor.PASSWORD),
                Instant.now().minus(11, ChronoUnit.MINUTES), Duration.ofMinutes(15));

        mockMvc.perform(post("/api/v1/auth/segundo-factor/totp").with(SesionEnCookies.conSesion(vieja)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Verificación requerida"));
        mockMvc.perform(delete("/api/v1/auth/segundo-factor/totp").with(SesionEnCookies.conSesion(vieja)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/segundo-factor/codigos").with(SesionEnCookies.conSesion(vieja)))
                .andExpect(status().isForbidden());
        estado(vieja).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Quitar el propio deja el login otra vez en un paso y borra los codigos")
    void quitarElPropio() throws Exception {
        JsonNode inscripcion = activar();
        String acceso = SesionEnCookies.acceso(completar(SesionEnCookies.acceso(login()), codigo(inscripcion, 1)));

        mockMvc.perform(delete("/api/v1/auth/segundo-factor/totp").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isNoContent());

        login().andExpect(jsonPath("$.segundoFactorPendiente").value(false));
        assertThat(jdbc.queryForObject("select count(*) from codigos_de_recuperacion c join usuarios u "
                + "on u.id = c.usuario_id where u.email = ?", Integer.class, admin)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from historial_cambios h join usuarios u "
                + "on u.empresa_id = h.empresa_id where u.email = ? and h.descripcion_cambio like ?", Integer.class, admin,
                "Segundo factor de \"Ana Segura\"%")).isEqualTo(2);
    }

    @Test
    @DisplayName("Un administrador le quita el segundo factor a otra persona y le cierra las sesiones, el suyo no")
    void elAdministradorLoQuita() throws Exception {
        String acceso = SesionEnCookies.acceso(login());
        String colega = "colega-" + UUID.randomUUID() + "@segundo-factor.test";
        JsonNode creado = cuerpo(mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Luis Colega", "email", colega,
                                "password", CLAVE, "rolAcceso", "EDITOR"))))
                .andExpect(status().isCreated()));
        long colegaId = creado.get("id").asLong();
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", colegaId);
        String accesoColega = SesionEnCookies.acceso(mockMvc.perform(SesionEnCookies.login()
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(colega, CLAVE)))));
        JsonNode suya = inscribir(accesoColega);
        confirmar(accesoColega, codigo(suya, 0)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/usuarios/" + colegaId + "/segundo-factor")
                        .with(SesionEnCookies.conSesion(accesoColega)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/usuarios/" + colegaId + "/segundo-factor")
                        .with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(accesoColega)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(colega, CLAVE))))
                .andExpect(jsonPath("$.segundoFactorPendiente").value(false));
        Long propio = jwtService.validar(acceso).orElseThrow().usuarioId();
        mockMvc.perform(delete("/api/v1/usuarios/" + propio + "/segundo-factor")
                        .with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Nada del segundo factor queda en las claves de idempotencia: ni el secreto ni los codigos")
    void sinIdempotencia() throws Exception {
        String acceso = SesionEnCookies.acceso(login());
        String clave = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/auth/segundo-factor/totp").with(SesionEnCookies.conSesion(acceso))
                        .header("Idempotency-Key", clave))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        assertThat(jdbc.queryForObject("select count(*) from claves_idempotencia where clave = ?", Integer.class,
                clave)).isZero();
    }

    /** Inscribe y confirma la aplicacion de autenticacion del administrador; devuelve el secreto y los codigos. */
    private JsonNode activar() throws Exception {
        String acceso = SesionEnCookies.acceso(login());
        JsonNode inscripcion = inscribir(acceso);
        JsonNode codigos = cuerpo(confirmar(acceso, codigo(inscripcion, 0)).andExpect(status().isOk()));
        ((tools.jackson.databind.node.ObjectNode) inscripcion).set("codigos", codigos.get("codigos"));
        return inscripcion;
    }

    /** El codigo del paso de ahora mas el desplazamiento: el 0 se gasta al confirmar, el 1 queda para el login. */
    private static String codigo(JsonNode inscripcion, int desplazamiento) {
        byte[] secreto = Base32.decodificar(inscripcion.get("secreto").asString());
        return Totp.codigo(secreto, Totp.paso(Instant.now()) + desplazamiento, Totp.DIGITOS);
    }

    private static List<String> codigosDe(JsonNode inscripcion) {
        List<String> codigos = new ArrayList<>();
        inscripcion.get("codigos").forEach(codigo -> codigos.add(codigo.asString()));
        return codigos;
    }

    private JsonNode inscribir(String acceso) throws Exception {
        return cuerpo(mockMvc.perform(post("/api/v1/auth/segundo-factor/totp").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()));
    }

    private ResultActions confirmar(String acceso, String codigo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/segundo-factor/totp/confirmacion")
                .with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("codigo", codigo))));
    }

    private ResultActions completar(String pendiente, String codigo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/segundo-factor/verificacion")
                .with(SesionEnCookies.conSesion(pendiente))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("codigo", codigo))));
    }

    private ResultActions estado(String acceso) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/segundo-factor").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk());
    }

    private ResultActions reautenticar(String acceso, Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reautenticacion").with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(admin, CLAVE))))
                .andExpect(status().isOk());
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return jsonMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
