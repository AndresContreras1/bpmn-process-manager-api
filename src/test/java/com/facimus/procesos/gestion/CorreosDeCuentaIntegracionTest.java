package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.security.SesionEnCookies;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 48: los correos de la cuenta, de punta a punta. GreenMail hace de servidor SMTP: la prueba lee el correo que de
 * verdad salio, saca el token de su enlace y lo usa como lo usaria la web. La cola no tiene trabajadores en las pruebas:
 * cada prueba la vacia llamandola, que es cuando salen los correos.
 */
@SpringBootTest(properties = {"spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false", "app.url-publica=https://tienda.example/"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CorreosDeCuentaIntegracionTest {

    private static final String CLAVE = "clave-de-la-tienda";
    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");
    private static final AtomicInteger TIENDAS = new AtomicInteger();

    @RegisterExtension
    static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort())
            .withPerMethodLifecycle(false);

    @DynamicPropertySource
    static void servidorDeCorreo(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.mail.host", () -> "localhost");
        propiedades.add("spring.mail.port", () -> SMTP.getSmtp().getPort());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ColaDeTrabajos cola;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void buzonVacio() throws Exception {
        SMTP.purgeEmailFromAllMailboxes();
        while (cola.procesarUno()) {
            // Lo que otra prueba dejo en la cola sale ahora, y su correo se borra con el buzon.
        }
        SMTP.purgeEmailFromAllMailboxes();
    }

    @Test
    @DisplayName("Registrar una tienda manda al administrador el enlace para verificar su correo; el enlace lo verifica "
            + "una sola vez")
    void registro_mandaElEnlaceDeVerificacion() throws Exception {
        String admin = registrarTienda();

        MimeMessage correo = elUnicoCorreoA(admin);
        assertThat(correo.getSubject()).isEqualTo("Verifica tu correo");
        String token = tokenDe(correo);

        confirmar("/api/v1/auth/verificacion/confirmar", Map.of("token", token)).andExpect(status().isNoContent());
        assertThat(verificado(admin)).isTrue();
        confirmar("/api/v1/auth/verificacion/confirmar", Map.of("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Enlace no válido"));
    }

    @Test
    @DisplayName("El correo sale en el idioma de quien hizo la peticion")
    void correo_enElIdiomaDeLaPeticion() throws Exception {
        String admin = registrarTienda("fr-CA");

        assertThat(elUnicoCorreoA(admin).getSubject()).isEqualTo("Vérifiez votre adresse e-mail");
    }

    @Test
    @DisplayName("Del enlace la base guarda solo el SHA-256 de su token, y la cola nunca lo tuvo en claro")
    void token_soloSuHuellaEnLaBase() throws Exception {
        String admin = registrarTienda();
        String token = tokenDe(elUnicoCorreoA(admin));

        assertThat(jdbc.queryForObject("select count(*) from enlaces_de_un_uso where token_hash = ?", Integer.class,
                Huella.de(token))).isOne();
        assertThat(jdbc.queryForObject("select count(*) from enlaces_de_un_uso where token_hash = ?", Integer.class,
                token)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from trabajos where datos like ?", Integer.class,
                "%" + token + "%")).isZero();
    }

    @Test
    @DisplayName("HU-02.1: sin el correo verificado no se invita; verificado, quien acepta la invitacion entra con su "
            + "clave y el rol que se le dio, y el enlace no sirve dos veces")
    void invitacion_deQuienVerificoSuCorreo() throws Exception {
        String admin = registrarTienda();
        String verificacion = tokenDe(elUnicoCorreoA(admin));
        String sesion = entrar(admin, CLAVE);
        String invitada = "invitada" + TIENDAS.incrementAndGet() + "@cuenta.test";
        Map<String, Object> invitacion = Map.of("email", invitada, "rolAcceso", "EDITOR");

        mockMvc.perform(post("/api/v1/usuarios/invitaciones").with(SesionEnCookies.conSesion(sesion))
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(invitacion)))
                .andExpect(status().isConflict());

        confirmar("/api/v1/auth/verificacion/confirmar", Map.of("token", verificacion))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/usuarios/invitaciones").with(SesionEnCookies.conSesion(sesion))
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(invitacion)))
                .andExpect(status().isAccepted());

        MimeMessage correo = elUnicoCorreoA(invitada);
        assertThat(correo.getSubject()).isEqualTo("Te invitaron a una tienda en BPMN Process Manager");
        assertThat(texto(correo)).contains("Administradora", "Tienda de cuentas");
        Map<String, Object> aceptar = Map.of("token", tokenDe(correo), "nombre", "Luis Invitado",
                "password", "clave-de-luis");
        confirmar("/api/v1/auth/invitacion/aceptar", aceptar)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(invitada))
                .andExpect(jsonPath("$.rolAcceso").value("EDITOR"))
                .andExpect(jsonPath("$.debeCambiarClave").value(false));

        assertThat(verificado(invitada)).isTrue();
        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(entrar(invitada, "clave-de-luis"))))
                .andExpect(status().isOk());
        confirmar("/api/v1/auth/invitacion/aceptar", aceptar).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Pedir una recuperacion responde lo mismo exista o no el correo, y solo al que existe le llega algo")
    void recuperacion_noDiceQueCorreosExisten() throws Exception {
        String admin = registrarTienda();
        SMTP.purgeEmailFromAllMailboxes();

        String existente = pedirRecuperacion(admin).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String inexistente = pedirRecuperacion("nadie@cuenta.test").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(existente);
        procesarLaCola();
        assertThat(SMTP.getReceivedMessages()).hasSize(1);
        assertThat(SMTP.getReceivedMessagesForDomain(admin)).hasSize(1);
    }

    @Test
    @DisplayName("El enlace de recuperacion cambia la clave, cierra las sesiones abiertas y no sirve dos veces")
    void recuperacion_cambiaLaClaveYCierraLasSesiones() throws Exception {
        String admin = registrarTienda();
        String sesionVieja = entrar(admin, CLAVE);
        SMTP.purgeEmailFromAllMailboxes();
        pedirRecuperacion(admin).andExpect(status().isAccepted());
        String token = tokenDe(elUnicoCorreoA(admin));

        confirmar("/api/v1/auth/recuperacion/confirmar", Map.of("token", token, "nueva", "la-clave-nueva"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(sesionVieja)))
                .andExpect(status().isUnauthorized());
        entrar(admin, "la-clave-nueva");
        login(admin, CLAVE).andExpect(status().isUnauthorized());
        confirmar("/api/v1/auth/recuperacion/confirmar", Map.of("token", token, "nueva", "otra-clave-mas"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un enlace de recuperacion vencido no sirve, y usar uno deja sin efecto el anterior")
    void recuperacion_vencidaOAnterior_noSirve() throws Exception {
        String admin = registrarTienda();
        SMTP.purgeEmailFromAllMailboxes();
        pedirRecuperacion(admin).andExpect(status().isAccepted());
        String primero = tokenDe(elUnicoCorreoA(admin));
        SMTP.purgeEmailFromAllMailboxes();
        pedirRecuperacion(admin).andExpect(status().isAccepted());
        String segundo = tokenDe(elUnicoCorreoA(admin));
        SMTP.purgeEmailFromAllMailboxes();
        pedirRecuperacion(admin).andExpect(status().isAccepted());
        String tercero = tokenDe(elUnicoCorreoA(admin));

        jdbc.update("update enlaces_de_un_uso set vence_en = creado_en - interval '1 minute' where token_hash = ?",
                Huella.de(tercero));
        confirmar("/api/v1/auth/recuperacion/confirmar", Map.of("token", tercero, "nueva", "la-clave-nueva"))
                .andExpect(status().isBadRequest());
        confirmar("/api/v1/auth/recuperacion/confirmar", Map.of("token", segundo, "nueva", "la-clave-nueva"))
                .andExpect(status().isNoContent());
        confirmar("/api/v1/auth/recuperacion/confirmar", Map.of("token", primero, "nueva", "otra-clave-mas"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una verificacion que esperaba en la cola no sale si el correo ya se verifico con otro enlace")
    void verificacionEnCola_noSaleSiYaSeVerifico() throws Exception {
        String admin = registrarTienda();
        String token = tokenDe(elUnicoCorreoA(admin));
        String sesion = entrar(admin, CLAVE);
        mockMvc.perform(post("/api/v1/auth/verificacion").with(SesionEnCookies.conSesion(sesion)))
                .andExpect(status().isAccepted());

        confirmar("/api/v1/auth/verificacion/confirmar", Map.of("token", token)).andExpect(status().isNoContent());
        SMTP.purgeEmailFromAllMailboxes();
        procesarLaCola();

        assertThat(SMTP.getReceivedMessages()).isEmpty();
    }

    private String registrarTienda() throws Exception {
        return registrarTienda("es-CO");
    }

    /** Registra una tienda y deja salir el correo de verificacion de su administradora. Devuelve su correo. */
    private String registrarTienda(String idioma) throws Exception {
        int numero = TIENDAS.incrementAndGet();
        String admin = "admin" + numero + "@cuenta.test";
        mockMvc.perform(post("/api/v1/empresas")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, idioma)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("nombreEmpresa", "Tienda de cuentas " + numero,
                                "nit", "9006" + numero + "000-1", "correoContacto", admin,
                                "nombreAdmin", "Administradora", "emailAdmin", admin, "passwordAdmin", CLAVE))))
                .andExpect(status().isCreated());
        procesarLaCola();
        return admin;
    }

    private ResultActions pedirRecuperacion(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recuperacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", email))));
    }

    private ResultActions confirmar(String ruta, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(cuerpo)));
    }

    private String entrar(String email, String clave) throws Exception {
        return SesionEnCookies.acceso(login(email, clave).andExpect(status().isOk()));
    }

    private ResultActions login(String email, String clave) throws Exception {
        return mockMvc.perform(SesionEnCookies.login()
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("email", email, "password", clave))));
    }

    /** Lo que esta en la cola sale ahora: en las pruebas no hay trabajadores que lo tomen solos. */
    private void procesarLaCola() {
        while (cola.procesarUno()) {
            // Cada vuelta manda un correo.
        }
    }

    private MimeMessage elUnicoCorreoA(String email) throws Exception {
        procesarLaCola();
        List<MimeMessage> recibidos = List.of(SMTP.getReceivedMessagesForDomain(email));
        assertThat(recibidos).as("correos a %s", email).hasSize(1);
        assertThat(recibidos.getFirst().getAllRecipients()[0].toString()).isEqualTo(email);
        return recibidos.getFirst();
    }

    private static String tokenDe(MimeMessage correo) throws Exception {
        Matcher enlace = TOKEN.matcher(texto(correo));
        assertThat(enlace.find()).as("el correo trae un enlace con su token").isTrue();
        return enlace.group(1);
    }

    /** La parte de texto del correo, ya decodificada: el cuerpo crudo viaja en quoted-printable. */
    private static String texto(Part parte) throws Exception {
        if (parte.isMimeType("text/plain")) {
            return (String) parte.getContent();
        }
        if (parte.getContent() instanceof Multipart partes) {
            for (int numero = 0; numero < partes.getCount(); numero++) {
                String texto = texto(partes.getBodyPart(numero));
                if (texto != null) {
                    return texto;
                }
            }
        }
        return null;
    }

    private boolean verificado(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select correo_verificado_en is not null from usuarios where email = ?", Boolean.class, email));
    }
}
