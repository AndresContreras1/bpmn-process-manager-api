package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.ConsultaDns;
import com.facimus.procesos.gestion.service.EmpresaService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 50: entrar con un proveedor de OpenID Connect, contra uno de mentira que firma id_token de verdad. La ida lleva
 * state, nonce y PKCE; la vuelta solo vale en el navegador que la empezo y con el nonce que se mando; un correo no
 * verificado no abre nada; un dominio verificado une a la tienda; un correo con cuenta espera a que su dueno lo
 * confirme; y con segundo factor el proveedor es solo el primero.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EntradaConProveedorIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String WEB = "http://web.test";
    private static final ProveedorDePrueba PROVEEDOR = arrancar();
    private static int tiendas;

    @DynamicPropertySource
    static void proveedor(DynamicPropertyRegistry propiedades) {
        propiedades.add("oidc.proveedores.pruebas.client-id", () -> ProveedorDePrueba.CLIENTE);
        propiedades.add("oidc.proveedores.pruebas.client-secret", () -> ProveedorDePrueba.SECRETO);
        propiedades.add("oidc.proveedores.pruebas.authorization-uri", () -> PROVEEDOR.base() + "/authorize");
        propiedades.add("oidc.proveedores.pruebas.token-uri", () -> PROVEEDOR.base() + "/token");
        propiedades.add("oidc.proveedores.pruebas.jwk-set-uri", () -> PROVEEDOR.base() + "/jwks");
        propiedades.add("oidc.proveedores.pruebas.issuer", PROVEEDOR::base);
        propiedades.add("app.url-publica", () -> WEB);
    }

    @AfterAll
    static void apagar() {
        PROVEEDOR.close();
    }

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

    @MockitoBean
    private ConsultaDns dns;

    private String admin;
    private String dominio;
    private Long empresaId;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        dominio = "tienda" + numero + "-" + UUID.randomUUID().toString().substring(0, 8) + ".test";
        admin = "admin@" + dominio;
        empresaId = empresaService.registrar("Tienda con proveedor " + numero, "9041" + numero + "000-1", admin,
                "Olga Proveedora", admin, CLAVE).id();
    }

    @Test
    @DisplayName("Los proveedores configurados se listan, y la ida lleva state, nonce y PKCE con S256")
    void idaConStateNonceYPkce() throws Exception {
        mockMvc.perform(get("/api/v1/auth/proveedores")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("pruebas"));

        Ida ida = ida();
        assertThat(ida.parametros()).containsKeys("state", "nonce", "code_challenge")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("client_id", ProveedorDePrueba.CLIENTE)
                .containsEntry("response_type", "code");
        assertThat(ida.parametros().get("scope")).contains("openid", "email");
        assertThat(ida.navegador().isHttpOnly()).isTrue();
        assertThat(ida.navegador().getSecure()).isTrue();
    }

    @Test
    @DisplayName("Sin cuenta ni dominio verificado no entra nadie, y el codigo se cambio con el verificador de PKCE")
    void sinCuenta() throws Exception {
        String destino = vuelta(ida(), "sub-1", "alguien@otro-dominio.test", true);

        assertThat(destino).isEqualTo(WEB + "/login?error=sin-cuenta");
        assertThat(PROVEEDOR.ultimaPeticionDeToken()).containsKey("code_verifier")
                .containsEntry("grant_type", "authorization_code");
        assertThat(PROVEEDOR.ultimaAutorizacion()).startsWith("Basic ");
    }

    @Test
    @DisplayName("Un correo que el proveedor no verifico no abre nada, aunque sea el de una cuenta")
    void correoNoVerificado() throws Exception {
        assertThat(vuelta(ida(), "sub-2", admin, false)).isEqualTo(WEB + "/login?error=correo-no-verificado");
        assertThat(jdbc.queryForObject("select count(*) from vinculos_pendientes where email = ?", Integer.class,
                admin)).isZero();
    }

    @Test
    @DisplayName("La vuelta solo vale en el navegador que empezo la ida, con su state, con su nonce y una vez")
    void stateNonceYNavegador() throws Exception {
        Ida ida = ida();
        PROVEEDOR.alCambiar("codigo-ajeno", new ProveedorDePrueba.Persona("sub-3", admin, true,
                ida.parametros().get("nonce")));
        // Sin la cookie del navegador que la empezo.
        assertThat(mockMvc.perform(get("/api/v1/auth/oidc/callback/pruebas").param("code", "codigo-ajeno")
                        .param("state", ida.parametros().get("state")))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo(WEB + "/login?error=proveedor");
        // Con la cookie de otro navegador, que empezo su propia ida.
        Ida ajena = ida();
        assertThat(mockMvc.perform(get("/api/v1/auth/oidc/callback/pruebas").param("code", "codigo-ajeno")
                        .param("state", ida.parametros().get("state")).cookie(ajena.navegador()))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo(WEB + "/login?error=proveedor");
        // Con otro state.
        assertThat(mockMvc.perform(get("/api/v1/auth/oidc/callback/pruebas").param("code", "codigo-ajeno")
                        .param("state", "otro-state").cookie(ida.navegador()))
                .andReturn().getResponse().getRedirectedUrl()).isEqualTo(WEB + "/login?error=proveedor");
        // Con un nonce que no es el que se mando.
        Ida otra = ida();
        PROVEEDOR.alCambiar("codigo-nonce", new ProveedorDePrueba.Persona("sub-3", admin, true, "otro-nonce"));
        assertThat(callback(otra, "codigo-nonce").getRedirectedUrl()).isEqualTo(WEB + "/login?error=proveedor");
        // La buena entra una vez; la misma vuelta otra vez ya no encuentra su peticion.
        Ida buena = ida();
        PROVEEDOR.alCambiar("codigo-bueno", new ProveedorDePrueba.Persona("sub-3", admin, true,
                buena.parametros().get("nonce")));
        assertThat(callback(buena, "codigo-bueno").getRedirectedUrl()).startsWith(WEB + "/vincular#token=");
        PROVEEDOR.alCambiar("codigo-bueno", new ProveedorDePrueba.Persona("sub-3", admin, true,
                buena.parametros().get("nonce")));
        assertThat(callback(buena, "codigo-bueno").getRedirectedUrl()).isEqualTo(WEB + "/login?error=proveedor");
    }

    @Test
    @DisplayName("Desde un dominio verificado se une a la tienda con el rol por defecto, y despues entra directo")
    void dominioVerificado() throws Exception {
        String sinVerificar = "nadie@" + dominio;
        jdbc.update("insert into dominios_verificados (empresa_id, dominio, rol_por_defecto, token, creado_en) "
                + "values (?, ?, 'EDITOR', 'x', current_timestamp)", empresaId, dominio);
        assertThat(vuelta(ida(), "sub-4", sinVerificar, true)).isEqualTo(WEB + "/login?error=sin-cuenta");

        jdbc.update("update dominios_verificados set verificado_en = current_timestamp where dominio = ?", dominio);
        Ida ida = ida();
        PROVEEDOR.alCambiar("codigo-dominio", new ProveedorDePrueba.Persona("sub-4", "nueva@" + dominio, true,
                ida.parametros().get("nonce")));
        MockHttpServletResponse respuesta = callback(ida, "codigo-dominio");

        assertThat(respuesta.getRedirectedUrl()).isEqualTo(WEB + "/");
        String acceso = respuesta.getCookie(CookiesDeSesion.ACCESO).getValue();
        assertThat(jwtService.validar(acceso).orElseThrow().rol().name()).isEqualTo("EDITOR");
        assertThat(jwtService.validarAcceso(acceso).orElseThrow().factores()).containsExactly(Factor.PROVEEDOR_EXTERNO);
        assertThat(jdbc.queryForObject("select correo_verificado_en is not null from usuarios where email = ?",
                Boolean.class, "nueva@" + dominio)).isTrue();

        assertThat(vuelta(ida(), "sub-4", "nueva@" + dominio, true)).isEqualTo(WEB + "/");
    }

    @Test
    @DisplayName("Con un correo que ya tiene cuenta, vincular pide la clave; despues el proveedor entra directo")
    void vincularUnaCuenta() throws Exception {
        String destino = vuelta(ida(), "sub-5", admin, true);
        assertThat(destino).startsWith(WEB + "/vincular#token=");
        String token = destino.substring(destino.indexOf("#token=") + "#token=".length());

        vincular(Map.of("token", token, "password", "no es la clave")).andExpect(status().isUnauthorized());
        ResultActions vinculada = vincular(Map.of("token", token, "password", CLAVE)).andExpect(status().isOk());
        String acceso = SesionEnCookies.acceso(vinculada);
        assertThat(jwtService.validarAcceso(acceso).orElseThrow().factores())
                .containsExactlyInAnyOrder(Factor.PASSWORD, Factor.PROVEEDOR_EXTERNO);
        mockMvc.perform(get("/api/v1/auth/identidades").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].proveedor").value("pruebas"));
        vincular(Map.of("token", token, "password", CLAVE)).andExpect(status().isBadRequest());

        assertThat(vuelta(ida(), "sub-5", admin, true)).isEqualTo(WEB + "/");
    }

    @Test
    @DisplayName("Con segundo factor, el proveedor deja el login a medias y el codigo lo completa")
    void conSegundoFactor() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        JsonNode inscripcion = jsonMapper.readTree(mockMvc.perform(post("/api/v1/auth/segundo-factor/totp")
                .with(SesionEnCookies.conSesion(acceso))).andReturn().getResponse().getContentAsString());
        byte[] secreto = Base32.decodificar(inscripcion.get("secreto").asString());
        mockMvc.perform(post("/api/v1/auth/segundo-factor/totp/confirmacion").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("codigo", codigo(secreto, 0)))))
                .andExpect(status().isOk());
        String vinculo = vuelta(ida(), "sub-6", admin, true);
        vincular(Map.of("token", vinculo.substring(vinculo.indexOf("#token=") + 7), "codigo", codigo(secreto, 1)))
                .andExpect(status().isOk());

        Ida ida = ida();
        PROVEEDOR.alCambiar("codigo-mfa", new ProveedorDePrueba.Persona("sub-6", admin, true,
                ida.parametros().get("nonce")));
        MockHttpServletResponse respuesta = callback(ida, "codigo-mfa");
        assertThat(respuesta.getRedirectedUrl()).isEqualTo(WEB + "/login?paso=segundo-factor");
        String pendiente = respuesta.getCookie(CookiesDeSesion.ACCESO).getValue();
        assertThat(jwtService.validar(pendiente).orElseThrow().segundoFactorPendiente()).isTrue();

        String codigoDeRecuperacion = recuperacion(acceso);
        String completo = SesionEnCookies.acceso(mockMvc.perform(post("/api/v1/auth/segundo-factor/verificacion")
                        .with(SesionEnCookies.conSesion(pendiente)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("codigo", codigoDeRecuperacion))))
                .andExpect(status().isOk()));
        assertThat(jwtService.validarAcceso(completo).orElseThrow().factores())
                .containsExactlyInAnyOrder(Factor.PROVEEDOR_EXTERNO, Factor.CODIGO_DE_RECUPERACION);
    }

    @Test
    @DisplayName("Si la tienda entra solo con proveedores, la clave le sirve al administrador y a nadie mas")
    void soloEntradaExterna() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        String editor = "editora@" + dominio;
        JsonNode creado = jsonMapper.readTree(mockMvc.perform(post("/api/v1/usuarios")
                        .with(SesionEnCookies.conSesion(acceso)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Eva Editora", "email", editor,
                                "password", CLAVE, "rolAcceso", "EDITOR"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", creado.get("id").asLong());
        String vinculo = vuelta(ida(), "sub-7", editor, true);
        String accesoEditor = SesionEnCookies.acceso(vincular(Map.of("token",
                vinculo.substring(vinculo.indexOf("#token=") + 7), "password", CLAVE)).andExpect(status().isOk()));

        JsonNode antes = jsonMapper.readTree(mockMvc.perform(get("/api/v1/empresas/actual/configuracion")
                .with(SesionEnCookies.conSesion(acceso))).andReturn().getResponse().getContentAsString());
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("politicaEstructura", antes.get("politicaEstructura").asString());
        cuerpo.put("soloEntradaExterna", true);
        cuerpo.put("version", antes.get("version").asLong());
        mockMvc.perform(put("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.soloEntradaExterna").value(true));

        mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(editor, CLAVE))))
                .andExpect(status().isForbidden());
        login(admin);
        Long identidad = jsonMapper.readTree(mockMvc.perform(get("/api/v1/auth/identidades")
                        .with(SesionEnCookies.conSesion(accesoEditor))).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();
        mockMvc.perform(delete("/api/v1/auth/identidades/" + identidad).with(SesionEnCookies.conSesion(accesoEditor)))
                .andExpect(status().isConflict());
        assertThat(vuelta(ida(), "sub-7", editor, true)).isEqualTo(WEB + "/");
    }

    @Test
    @DisplayName("Un dominio se verifica con su registro TXT, y el que ya verifico otra tienda no se verifica dos veces")
    void verificarDominio() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        JsonNode agregado = jsonMapper.readTree(mockMvc.perform(post("/api/v1/empresas/actual/dominios")
                        .with(SesionEnCookies.conSesion(acceso)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("dominio", dominio.toUpperCase(),
                                "rolPorDefecto", "SOLO_LECTURA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dominio").value(dominio))
                .andExpect(jsonPath("$.verificado").value(false))
                .andReturn().getResponse().getContentAsString());
        String registro = agregado.get("registroTxt").asString();
        String verificacion = "/api/v1/empresas/actual/dominios/" + agregado.get("id").asLong() + "/verificacion";

        given(dns.registrosTxt(dominio)).willReturn(List.of("v=spf1 -all"));
        mockMvc.perform(post(verificacion).with(SesionEnCookies.conSesion(acceso))).andExpect(status().isBadRequest());
        given(dns.registrosTxt(dominio)).willReturn(List.of("v=spf1 -all", registro));
        mockMvc.perform(post(verificacion).with(SesionEnCookies.conSesion(acceso))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificado").value(true));

        String otro = "otra-admin@" + UUID.randomUUID() + ".test";
        empresaService.registrar("Otra tienda " + tiendas, "9049" + tiendas + "000-1", otro, "Otra", otro, CLAVE);
        String accesoOtro = SesionEnCookies.acceso(login(otro));
        JsonNode suyo = jsonMapper.readTree(mockMvc.perform(post("/api/v1/empresas/actual/dominios")
                        .with(SesionEnCookies.conSesion(accesoOtro)).contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("dominio", dominio, "rolPorDefecto",
                                "EDITOR"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        given(dns.registrosTxt(dominio)).willReturn(List.of(registro, suyo.get("registroTxt").asString()));
        mockMvc.perform(post("/api/v1/empresas/actual/dominios/" + suyo.get("id").asLong() + "/verificacion")
                .with(SesionEnCookies.conSesion(accesoOtro))).andExpect(status().isConflict());
        // Ni la otra tienda ve el dominio de esta, ni un editor ve los de la suya.
        mockMvc.perform(get("/api/v1/empresas/actual/dominios").with(SesionEnCookies.conSesion(accesoOtro)))
                .andExpect(jsonPath("$[0].verificado").value(false));
    }

    /** La ida al proveedor: los parametros de la direccion a la que manda el navegador, y su cookie. */
    private record Ida(Map<String, String> parametros, Cookie navegador) {
    }

    private Ida ida() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/v1/auth/oidc/pruebas"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        String destino = respuesta.getRedirectedUrl();
        assertThat(destino).startsWith(PROVEEDOR.base() + "/authorize?");
        Map<String, String> parametros = Arrays.stream(URI.create(destino).getRawQuery().split("&"))
                .map(par -> par.split("=", 2))
                .collect(Collectors.toMap(par -> par[0], par -> URLDecoder.decode(par[1], StandardCharsets.UTF_8)));
        return new Ida(parametros, respuesta.getCookie(PeticionesOidc.COOKIE));
    }

    /** Vuelve del proveedor con un codigo que da el id_token de esa persona, con el nonce de la ida. */
    private String vuelta(Ida ida, String sub, String email, boolean verificado) throws Exception {
        String codigo = "codigo-" + UUID.randomUUID();
        PROVEEDOR.alCambiar(codigo, new ProveedorDePrueba.Persona(sub, email, verificado,
                ida.parametros().get("nonce")));
        return callback(ida, codigo).getRedirectedUrl();
    }

    private MockHttpServletResponse callback(Ida ida, String codigo) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/oidc/callback/pruebas").param("code", codigo)
                        .param("state", ida.parametros().get("state")).cookie(ida.navegador()))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
    }

    private ResultActions vincular(Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/identidades/vinculo").with(SesionEnCookies.conCsrf())
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk());
    }

    /** Uno de los codigos de recuperacion de la persona, renovados para la prueba. */
    private String recuperacion(String acceso) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(post("/api/v1/auth/segundo-factor/codigos")
                        .with(SesionEnCookies.conSesion(acceso))).andReturn().getResponse().getContentAsString())
                .get("codigos").get(0).asString();
    }

    private static String codigo(byte[] secreto, int desplazamiento) {
        return Totp.codigo(secreto, Totp.paso(Instant.now()) + desplazamiento, Totp.DIGITOS);
    }

    private static ProveedorDePrueba arrancar() {
        try {
            return new ProveedorDePrueba();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
