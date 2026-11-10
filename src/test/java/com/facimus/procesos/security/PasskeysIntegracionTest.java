package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.security.AutenticadorDePrueba.Variante;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PR 49b: las passkeys, de punta a punta, con un autenticador que firma como un navegador. Se registran con la sesion
 * recien confirmada, completan el login a medias que deja la clave, confirman quien es la persona y entran sin clave;
 * lo que el dispositivo firma se rechaza si viene de otro origen, sin verificar a la persona, con la firma rota,
 * con un contador que no sube o para otro desafio, que ademas sirve una sola vez.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PasskeysIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String BASE = "/api/v1/auth/segundo-factor/passkeys";
    private static final String VERIFICACION = "/api/v1/auth/segundo-factor/verificacion/passkey";
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

    private final AutenticadorDePrueba autenticador = new AutenticadorDePrueba();
    private String admin;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@passkeys.test";
        empresaService.registrar("Tienda con passkeys " + numero, "9041" + numero + "000-1", admin, "Lia Llaves",
                admin, CLAVE);
    }

    @Test
    @DisplayName("Registrar una passkey: opciones que piden verificar a la persona y una credencial descubrible, y los "
            + "diez codigos con el primer segundo factor")
    void registrar() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));

        JsonNode opciones = cuerpo(mockMvc.perform(post(BASE + "/opciones").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()));
        assertThat(opciones.path("desafio").asString()).hasSizeGreaterThan(40);
        JsonNode creacion = opciones.path("opciones");
        assertThat(creacion.path("rp").path("id").asString()).isEqualTo("localhost");
        assertThat(creacion.path("user").path("name").asString()).isEqualTo(admin);
        assertThat(creacion.path("authenticatorSelection").path("residentKey").asString()).isEqualTo("required");
        assertThat(creacion.path("authenticatorSelection").path("userVerification").asString())
                .isEqualTo("required");
        assertThat(creacion.path("attestation").asString()).isEqualTo("none");

        JsonNode registrada = cuerpo(registrar(acceso, opciones, "Portatil", autenticador.registrar(creacion))
                .andExpect(status().isCreated()));
        assertThat(registrada.path("passkey").path("etiqueta").asString()).isEqualTo("Portatil");
        assertThat(registrada.path("passkey").path("sincronizada").asBoolean()).isTrue();
        assertThat(registrada.path("codigosDeRecuperacion")).hasSize(10);

        mockMvc.perform(get(BASE).with(SesionEnCookies.conSesion(acceso))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ultimoUsoEn").doesNotExist());
        mockMvc.perform(get("/api/v1/auth/segundo-factor").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(jsonPath("$.totp").value(false))
                .andExpect(jsonPath("$.passkeys").value(1))
                .andExpect(jsonPath("$.codigosRestantes").value(10));
        // La bitacora llega por el outbox, despues del commit.
        await().atMost(Duration.ofSeconds(10)).until(() -> bitacora("PASSKEY_REGISTRADA") == 1);

        // La segunda no trae codigos: ya los tiene, y quedan fuera las opciones de la que ya registro.
        JsonNode otras = cuerpo(mockMvc.perform(post(BASE + "/opciones").with(SesionEnCookies.conSesion(acceso))));
        assertThat(otras.path("opciones").path("excludeCredentials")).hasSize(1);
        assertThat(cuerpo(registrar(acceso, otras, null, autenticador.registrar(otras.path("opciones")))
                .andExpect(status().isCreated())).path("codigosDeRecuperacion")).isEmpty();
    }

    @Test
    @DisplayName("Con una passkey, la clave deja el login a medias, y la passkey lo completa con los dos factores")
    void segundoFactor() throws Exception {
        registrarUna(SesionEnCookies.acceso(login(admin)));

        ResultActions aMedias = login(admin).andExpect(jsonPath("$.segundoFactorPendiente").value(true));
        String pendiente = SesionEnCookies.acceso(aMedias);
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(pendiente)))
                .andExpect(status().isUnauthorized());

        JsonNode opciones = cuerpo(mockMvc.perform(post(VERIFICACION + "/opciones")
                .with(SesionEnCookies.conSesion(pendiente))).andExpect(status().isOk()));
        assertThat(opciones.path("opciones").path("allowCredentials")).hasSize(1);
        assertThat(opciones.path("opciones").path("userVerification").asString()).isEqualTo("required");

        String completa = SesionEnCookies.acceso(firmar(VERIFICACION, pendiente, opciones,
                autenticador.firmar(opciones.path("opciones"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.segundoFactorPendiente").value(false)));
        assertThat(jwtService.validarAcceso(completa).orElseThrow().factores())
                .containsExactlyInAnyOrder(Factor.PASSWORD, Factor.PASSKEY);
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(completa))).andExpect(status().isOk());
        mockMvc.perform(get(BASE).with(SesionEnCookies.conSesion(completa)))
                .andExpect(jsonPath("$[0].ultimoUsoEn").exists());
    }

    @Test
    @DisplayName("Entrar sin clave: las opciones no nombran ninguna passkey y la sesion sale con la passkey como factor")
    void entrarSinClave() throws Exception {
        registrarUna(SesionEnCookies.acceso(login(admin)));

        JsonNode opciones = opcionesDeEntrada();
        assertThat(opciones.path("opciones").path("allowCredentials").isEmpty()).isTrue();
        ResultActions entrada = entrar(opciones, autenticador.firmar(opciones.path("opciones")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value(admin))
                .andExpect(jsonPath("$.segundoFactorPendiente").value(false));
        String acceso = SesionEnCookies.acceso(entrada);
        assertThat(jwtService.validarAcceso(acceso).orElseThrow().factores()).containsExactly(Factor.PASSKEY);
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(SesionEnCookies.conRefresco(SesionEnCookies.refresco(entrada))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un desafio sirve una vez, para su proposito, y vence a los cinco minutos")
    void desafios() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        registrarUna(acceso);

        JsonNode opciones = opcionesDeEntrada();
        entrar(opciones, autenticador.firmar(opciones.path("opciones"))).andExpect(status().isOk());
        entrar(opciones, autenticador.firmar(opciones.path("opciones"))).andExpect(status().isUnauthorized());

        JsonNode vencidas = opcionesDeEntrada();
        jdbc.update("update desafios_webauthn set creado_en = creado_en - interval '6 minutes'");
        entrar(vencidas, autenticador.firmar(vencidas.path("opciones"))).andExpect(status().isUnauthorized());

        // El de un registro no sirve para entrar, aunque la firma responda a su desafio.
        JsonNode deRegistro = opcionesDeRegistro(acceso);
        entrar(deRegistro, autenticador.firmar(deRegistro.path("opciones"))).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from desafios_webauthn where proposito = 'REGISTRO'",
                Integer.class)).isPositive();
    }

    @Test
    @DisplayName("Se rechaza lo firmado desde otro origen, sin verificar a la persona, con la firma rota, con un "
            + "contador que no sube o como si fuera un registro")
    void firmasTorcidas() throws Exception {
        registrarUna(SesionEnCookies.acceso(login(admin)));

        for (Variante torcida : new Variante[] {Variante.normal().conOrigen("https://phishing.example"),
                Variante.normal().sinVerificarALaPersona(), Variante.normal().conFirmaRota(),
                Variante.normal().conTipo("webauthn.create")}) {
            JsonNode opciones = opcionesDeEntrada();
            entrar(opciones, autenticador.firmar(opciones.path("opciones"), torcida))
                    .andExpect(status().isUnauthorized());
        }

        JsonNode buena = opcionesDeEntrada();
        entrar(buena, autenticador.firmar(buena.path("opciones"), Variante.normal().conContador(7)))
                .andExpect(status().isOk());
        // Una copia de la clave firmaria con un contador que ya paso: delata que hay dos.
        JsonNode copia = opcionesDeEntrada();
        entrar(copia, autenticador.firmar(copia.path("opciones"), Variante.normal().conContador(7)))
                .andExpect(status().isUnauthorized());
        await().atMost(Duration.ofSeconds(10)).until(() -> bitacora("PASSKEY_FALLIDA") == 5);
    }

    @Test
    @DisplayName("Una registro que no verifica no guarda nada, y una passkey ya registrada responde 409")
    void registroTorcido() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));

        JsonNode opciones = opcionesDeRegistro(acceso);
        registrar(acceso, opciones, null, autenticador.registrar(opciones.path("opciones"),
                Variante.normal().conOrigen("https://phishing.example"))).andExpect(status().isBadRequest());
        JsonNode sinVerificar = opcionesDeRegistro(acceso);
        registrar(acceso, sinVerificar, null, autenticador.registrar(sinVerificar.path("opciones"),
                Variante.normal().sinVerificarALaPersona())).andExpect(status().isBadRequest());
        assertThat(cuerpo(mockMvc.perform(get(BASE).with(SesionEnCookies.conSesion(acceso))))).isEmpty();

        JsonNode buenas = opcionesDeRegistro(acceso);
        ObjectNode credencial = autenticador.registrar(buenas.path("opciones"));
        registrar(acceso, buenas, null, credencial).andExpect(status().isCreated());
        JsonNode otraVez = opcionesDeRegistro(acceso);
        registrar(acceso, otraVez, null, credencial).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("En el login a medias solo sirve una passkey de esa persona, y cada fallo cuenta para su limite")
    void soloLaDeEsaPersona() throws Exception {
        registrarUna(SesionEnCookies.acceso(login(admin)));
        String otra = "otra-" + UUID.randomUUID() + "@passkeys.test";
        empresaService.registrar("Otra tienda " + otra, "9042" + (tiendas + 500) + "000-1", otra, "Otra Persona",
                otra, CLAVE);
        AutenticadorDePrueba suyo = new AutenticadorDePrueba();
        JsonNode opcionesSuyas = opcionesDeRegistro(SesionEnCookies.acceso(login(otra)));
        registrar(SesionEnCookies.acceso(login(otra)), opcionesSuyas, null,
                suyo.registrar(opcionesSuyas.path("opciones"))).andExpect(status().isCreated());

        String pendiente = SesionEnCookies.acceso(login(admin));
        for (int intento = 1; intento <= 5; intento++) {
            JsonNode opciones = cuerpo(mockMvc.perform(post(VERIFICACION + "/opciones")
                    .with(SesionEnCookies.conSesion(pendiente))));
            ObjectNode ajena = suyo.firmarCon(suyo.claves().getFirst(), opciones.path("opciones"), Variante.normal());
            firmar(VERIFICACION, pendiente, opciones, ajena).andExpect(status().isBadRequest());
        }
        JsonNode opciones = cuerpo(mockMvc.perform(post(VERIFICACION + "/opciones")
                .with(SesionEnCookies.conSesion(pendiente))));
        firmar(VERIFICACION, pendiente, opciones, autenticador.firmar(opciones.path("opciones")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Confirmar quien es con una passkey renueva el acceso marcado como verificado ahora")
    void reautenticar() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        registrarUna(acceso);
        String completo = completarConPasskey(SesionEnCookies.acceso(login(admin)));
        Instant antes = jwtService.validarAcceso(completo).orElseThrow().verificadoEn();

        JsonNode opciones = cuerpo(mockMvc.perform(post(VERIFICACION + "/opciones")
                .with(SesionEnCookies.conSesion(completo))).andExpect(status().isOk()));
        String renovado = SesionEnCookies.acceso(firmar("/api/v1/auth/reautenticacion/passkey", completo, opciones,
                autenticador.firmar(opciones.path("opciones"))).andExpect(status().isOk()));
        assertThat(jwtService.validarAcceso(renovado).orElseThrow().verificadoEn()).isAfterOrEqualTo(antes);

        // La clave sola ya no confirma nada: tiene segundo factor.
        mockMvc.perform(post("/api/v1/auth/reautenticacion").with(SesionEnCookies.conSesion(completo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("password", CLAVE))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Quitar la ultima passkey se lleva los codigos y el login vuelve a la clave sola; con la aplicacion, "
            + "los codigos se quedan")
    void quitar() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        registrarUna(acceso);
        String completo = completarConPasskey(SesionEnCookies.acceso(login(admin)));
        Long id = cuerpo(mockMvc.perform(get(BASE).with(SesionEnCookies.conSesion(completo)))).get(0).path("id")
                .asLong();

        mockMvc.perform(patch(BASE + "/" + id).with(SesionEnCookies.conSesion(completo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("etiqueta", "Telefono"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.etiqueta").value("Telefono"));

        // La aplicacion de autenticacion, ademas: confirmarla no trae codigos nuevos, ya los tiene.
        JsonNode inscripcion = cuerpo(mockMvc.perform(post("/api/v1/auth/segundo-factor/totp")
                .with(SesionEnCookies.conSesion(completo))).andExpect(status().isOk()));
        byte[] secreto = Base32.decodificar(inscripcion.get("secreto").asString());
        mockMvc.perform(post("/api/v1/auth/segundo-factor/totp/confirmacion").with(SesionEnCookies.conSesion(completo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("codigo",
                                Totp.codigo(secreto, Totp.paso(Instant.now()), Totp.DIGITOS)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigos.length()").value(0));

        mockMvc.perform(delete(BASE + "/" + id).with(SesionEnCookies.conSesion(completo)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/segundo-factor").with(SesionEnCookies.conSesion(completo)))
                .andExpect(jsonPath("$.passkeys").value(0)).andExpect(jsonPath("$.codigosRestantes").value(10));

        mockMvc.perform(delete("/api/v1/auth/segundo-factor/totp").with(SesionEnCookies.conSesion(completo)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/segundo-factor").with(SesionEnCookies.conSesion(completo)))
                .andExpect(jsonPath("$.codigosRestantes").value(0));
        login(admin).andExpect(jsonPath("$.segundoFactorPendiente").value(false));
        assertThat(jdbc.queryForObject("select count(*) from codigos_de_recuperacion c join usuarios u "
                + "on u.id = c.usuario_id where u.email = ?", Integer.class, admin)).isZero();
    }

    @Test
    @DisplayName("Una persona desactivada no entra con su passkey, y el administrador que le quita el segundo factor "
            + "le quita tambien sus passkeys")
    void desactivadaYQuitadaPorElAdministrador() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        String editora = "editora-" + UUID.randomUUID() + "@passkeys.test";
        Long editoraId = cuerpo(mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Eva Editora", "email", editora,
                                "password", CLAVE, "rolAcceso", "EDITOR"))))
                .andExpect(status().isCreated())).path("id").asLong();
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", editoraId);
        AutenticadorDePrueba suyo = new AutenticadorDePrueba();
        String deEditora = SesionEnCookies.acceso(login(editora));
        JsonNode opciones = opcionesDeRegistro(deEditora);
        registrar(deEditora, opciones, null, suyo.registrar(opciones.path("opciones")))
                .andExpect(status().isCreated());

        jdbc.update("update usuarios set activo = false where id = ?", editoraId);
        JsonNode entrada = opcionesDeEntrada();
        entrar(entrada, suyo.firmar(entrada.path("opciones"))).andExpect(status().isUnauthorized());
        jdbc.update("update usuarios set activo = true where id = ?", editoraId);

        mockMvc.perform(delete("/api/v1/usuarios/" + editoraId + "/segundo-factor")
                .with(SesionEnCookies.conSesion(acceso))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from passkeys where usuario_id = ?", Integer.class,
                editoraId)).isZero();
        JsonNode despues = opcionesDeEntrada();
        entrar(despues, suyo.firmar(despues.path("opciones"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Anonimizar a una persona borra sus passkeys y su identificador de WebAuthn")
    void anonimizar() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        String editora = "anonima-" + UUID.randomUUID() + "@passkeys.test";
        Long editoraId = cuerpo(mockMvc.perform(post("/api/v1/usuarios").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombre", "Ana Anonima", "email", editora,
                                "password", CLAVE, "rolAcceso", "EDITOR"))))
                .andExpect(status().isCreated())).path("id").asLong();
        jdbc.update("update usuarios set debe_cambiar_clave = false where id = ?", editoraId);
        String deEditora = SesionEnCookies.acceso(login(editora));
        JsonNode opciones = opcionesDeRegistro(deEditora);
        registrar(deEditora, opciones, null, new AutenticadorDePrueba().registrar(opciones.path("opciones")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/usuarios/" + editoraId + "/anonimizar").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from passkeys where usuario_id = ?", Integer.class,
                editoraId)).isZero();
        assertThat(jdbc.queryForObject("select webauthn_id is null from usuarios where id = ?", Boolean.class,
                editoraId)).isTrue();
    }

    @Test
    @DisplayName("Una persona no tiene mas de diez passkeys")
    void maximo() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin));
        for (int i = 0; i < 10; i++) {
            registrarUna(acceso);
        }
        JsonNode opciones = opcionesDeRegistro(acceso);
        registrar(acceso, opciones, null, autenticador.registrar(opciones.path("opciones")))
                .andExpect(status().isConflict());
    }

    /** Registra una passkey nueva en el autenticador, con la sesion recien abierta. */
    private void registrarUna(String acceso) throws Exception {
        JsonNode opciones = opcionesDeRegistro(acceso);
        registrar(acceso, opciones, null, autenticador.registrar(opciones.path("opciones")))
                .andExpect(status().isCreated());
    }

    /** Completa un login a medias con la passkey y devuelve el acceso de la sesion completa. */
    private String completarConPasskey(String pendiente) throws Exception {
        JsonNode opciones = cuerpo(mockMvc.perform(post(VERIFICACION + "/opciones")
                .with(SesionEnCookies.conSesion(pendiente))).andExpect(status().isOk()));
        return SesionEnCookies.acceso(firmar(VERIFICACION, pendiente, opciones,
                autenticador.firmar(opciones.path("opciones"))).andExpect(status().isOk()));
    }

    private JsonNode opcionesDeRegistro(String acceso) throws Exception {
        return cuerpo(mockMvc.perform(post(BASE + "/opciones").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()));
    }

    private JsonNode opcionesDeEntrada() throws Exception {
        return cuerpo(mockMvc.perform(post("/api/v1/auth/passkeys/opciones").with(SesionEnCookies.conCsrf()))
                .andExpect(status().isOk()));
    }

    private ResultActions registrar(String acceso, JsonNode opciones, String etiqueta, JsonNode credencial)
            throws Exception {
        ObjectNode cuerpo = jsonMapper.createObjectNode();
        cuerpo.put("desafio", opciones.path("desafio").asString());
        if (etiqueta != null) {
            cuerpo.put("etiqueta", etiqueta);
        }
        cuerpo.set("credencial", credencial);
        return mockMvc.perform(post(BASE).with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions firmar(String ruta, String acceso, JsonNode opciones, JsonNode credencial)
            throws Exception {
        return mockMvc.perform(post(ruta).with(SesionEnCookies.conSesion(acceso))
                .contentType(MediaType.APPLICATION_JSON).content(firma(opciones, credencial)));
    }

    private ResultActions entrar(JsonNode opciones, JsonNode credencial) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/passkeys/entrada").with(SesionEnCookies.conCsrf())
                .contentType(MediaType.APPLICATION_JSON).content(firma(opciones, credencial)));
    }

    private String firma(JsonNode opciones, JsonNode credencial) {
        ObjectNode cuerpo = jsonMapper.createObjectNode();
        cuerpo.put("desafio", opciones.path("desafio").asString());
        cuerpo.set("credencial", credencial);
        return jsonMapper.writeValueAsString(cuerpo);
    }

    /** Las entradas de la bitacora de la tienda del administrador con esa accion. */
    private int bitacora(String accion) {
        return jdbc.queryForObject("select count(*) from bitacora where accion = ? "
                + "and empresa_id = (select empresa_id from usuarios where email = ?)", Integer.class, accion, admin);
    }

    private JsonNode cuerpo(ResultActions resultado) throws Exception {
        return jsonMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(email, CLAVE))))
                .andExpect(status().isOk());
    }
}
