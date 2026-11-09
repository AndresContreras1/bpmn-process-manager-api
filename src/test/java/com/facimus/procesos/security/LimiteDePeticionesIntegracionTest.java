package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 32: los cubos del limite de peticiones, con capacidades chicas para verlos llenarse. Cada prueba entra desde su
 * propia direccion, para no gastar del cubo de otra. Turnstile es uno de mentira que solo acepta "humano".
 */
@SpringBootTest(properties = {"limites.activos=true", "limites.autenticacion-por-minuto=3",
        "limites.registro-por-hora=2", "limites.correo-por-hora=2", "limites.escritura-por-minuto=5",
        "limites.cuerpo-maximo=2KB", "turnstile.secreto=secreto-de-pruebas"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LimiteDePeticionesIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final HttpServer TURNSTILE = turnstile();
    private static final AtomicInteger DIRECCIONES = new AtomicInteger();
    private static final AtomicInteger TIENDAS = new AtomicInteger();

    @DynamicPropertySource
    static void turnstileDePrueba(DynamicPropertyRegistry propiedades) {
        propiedades.add("turnstile.url",
                () -> "http://127.0.0.1:" + TURNSTILE.getAddress().getPort() + "/siteverify");
    }

    @AfterAll
    static void apagar() {
        TURNSTILE.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Entrar cuenta por direccion: con el cubo vacio responde 429 con Retry-After, y otra direccion entra")
    void autenticacionPorDireccion() throws Exception {
        RequestPostProcessor desde = direccion();
        for (int intento = 0; intento < 3; intento++) {
            login("nadie" + intento + "@limite.test", "no-es-la-clave", desde).andExpect(status().isUnauthorized());
        }
        login("nadie9@limite.test", "no-es-la-clave", desde).andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"));

        login("nadie9@limite.test", "no-es-la-clave", direccion()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Registrar una tienda pide Turnstile, y cuenta por direccion: el tercero del cubo de dos no entra")
    void registroConTurnstileYPorDireccion() throws Exception {
        RequestPostProcessor desde = direccion();
        registrar(desde, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Verificación requerida"));
        registrar(direccion(), "un-robot").andExpect(status().isBadRequest());

        RequestPostProcessor otra = direccion();
        registrar(otra, "humano").andExpect(status().isCreated());
        registrar(otra, "humano").andExpect(status().isCreated());
        registrar(otra, "humano").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Cambiar cosas cuenta por persona: leer no gasta, y una colega de la misma tienda tiene su propio cubo")
    void escrituraPorPersona() throws Exception {
        int numero = TIENDAS.incrementAndGet();
        String admin = "admin" + numero + "@escritura.test";
        EmpresaResponse tienda = empresaService.registrar("Tienda que escribe " + numero, "9051" + numero + "000-1",
                admin, "Ana", admin, CLAVE);
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE, direccion()).andExpect(status().isOk()));
        for (int proceso = 0; proceso < 5; proceso++) {
            crearProceso(acceso, "Proceso " + proceso).andExpect(status().isCreated());
        }
        crearProceso(acceso, "Uno de mas").andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        for (int lectura = 0; lectura < 10; lectura++) {
            mockMvc.perform(get("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso)))
                    .andExpect(status().isOk());
        }

        Long adminId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, admin);
        String colega = "colega" + numero + "@escritura.test";
        usuarioService.crearColaborador(tienda.id(), adminId, "Luis", colega, CLAVE, RolAcceso.EDITOR);
        String suyo = SesionEnCookies.acceso(login(colega, CLAVE, direccion()).andExpect(status().isOk()));
        crearProceso(suyo, "El de la colega").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Un cuerpo de mas del maximo responde 413 sin llegar a ningun controller")
    void cuerpoDemasiadoGrande() throws Exception {
        String grande = "{\"email\":\"" + "a".repeat(3000) + "@x.test\",\"password\":\"x\"}";
        mockMvc.perform(SesionEnCookies.login().with(direccion()).contentType(MediaType.APPLICATION_JSON)
                        .content(grande))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.title").value("Cuerpo demasiado grande"));
    }

    @Test
    @DisplayName("Dos instancias gastan del mismo cubo: lo que una gasto, la otra ya no lo tiene")
    void dosInstanciasCompartenElCubo() {
        LimitesDePeticiones una = new LimitesDePeticiones(dataSource, true, 3, 2, 2, 5);
        LimitesDePeticiones otra = new LimitesDePeticiones(dataSource, true, 3, 2, 2, 5);
        String clave = "compartida-" + DIRECCIONES.incrementAndGet();

        assertThat(una.gastar(ClaseDeLimite.AUTENTICACION, clave)).isEmpty();
        assertThat(otra.gastar(ClaseDeLimite.AUTENTICACION, clave)).isEmpty();
        assertThat(una.gastar(ClaseDeLimite.AUTENTICACION, clave)).isEmpty();
        assertThat(otra.gastar(ClaseDeLimite.AUTENTICACION, clave)).isPresent();
    }

    @Test
    @DisplayName("La purga se lleva los cubos que ya se llenaron y deja los que estan en uso")
    void olvidarVencidos() {
        LimitesDePeticiones limites = new LimitesDePeticiones(dataSource, true, 3, 2, 2, 5);
        String enUso = "en-uso-" + DIRECCIONES.incrementAndGet();
        limites.gastar(ClaseDeLimite.CORREO, enUso);
        jdbc.update("insert into cubos_de_peticiones (id, estado, vence_en) values (?, null, 1)", "viejo-" + enUso);

        assertThat(limites.olvidarVencidos()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from cubos_de_peticiones where id = ?", Integer.class,
                "correo:" + enUso)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from cubos_de_peticiones where id = ?", Integer.class,
                "viejo-" + enUso)).isZero();
    }

    /** Una direccion que ninguna otra prueba uso, para empezar con los cubos llenos. */
    private static RequestPostProcessor direccion() {
        String ip = "10.32.0." + DIRECCIONES.incrementAndGet();
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }

    private ResultActions login(String email, String clave, RequestPostProcessor desde) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().with(desde).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(email, clave))));
    }

    private ResultActions registrar(RequestPostProcessor desde, String turnstile) throws Exception {
        int numero = TIENDAS.incrementAndGet();
        String correo = "registro" + numero + "@limite.test";
        var peticion = post("/api/v1/empresas").with(desde).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(Map.of("nombreEmpresa", "Tienda limitada " + numero,
                        "nit", "9052" + numero + "000-1", "correoContacto", correo, "nombreAdmin", "Admin",
                        "emailAdmin", correo, "passwordAdmin", CLAVE)));
        if (turnstile != null) {
            peticion.header(VerificacionHumana.CABECERA, turnstile);
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions crearProceso(String acceso, String nombre) throws Exception {
        // Cada una desde otra direccion: lo que cambia algo cuenta por persona, venga de donde venga.
        return mockMvc.perform(post("/api/v1/procesos").with(SesionEnCookies.conSesion(acceso)).with(direccion())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Lo que cubre\",\"categoria\":\"Ops\"}"));
    }

    /** Responde como Cloudflare: success solo si el token es "humano" y llego con el secreto. */
    private static HttpServer turnstile() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/siteverify", intercambio -> {
                String cuerpo;
                try (InputStream entrada = intercambio.getRequestBody()) {
                    cuerpo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
                }
                boolean humano = cuerpo.contains("secret=secreto-de-pruebas") && cuerpo.contains("response=humano");
                byte[] respuesta = ("{\"success\":" + humano + ",\"error-codes\":[]}").getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, respuesta.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(respuesta);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
