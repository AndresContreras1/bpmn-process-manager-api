package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.security.SesionEnCookies;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 31: la bitacora encadenada. Lo que pasa en la tienda llega solo, despues del commit; nadie la cambia ni la borra,
 * ni con SQL; si alguien lo consigue apagando el trigger, la verificacion dice en que registro; y dos sucesos a la
 * vez de la misma tienda quedan uno detras del otro.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BitacoraIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private BitacoraService bitacoraService;

    @Autowired
    private JdbcTemplate jdbc;

    private String admin;
    private Long empresaId;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@bitacora.test";
        empresaId = empresaService.registrar("Tienda auditada " + numero, "9061" + numero + "000-1", admin,
                "Berta Bitacora", admin, CLAVE).id();
    }

    @Test
    @DisplayName("Entrar bien y mal llega sola a la bitacora, con la IP, el navegador y el id de la peticion")
    void loginsEnLaBitacora() throws Exception {
        login(admin, "no-es-la-clave").andExpect(status().isUnauthorized());
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE).andExpect(status().isOk()));

        JsonNode registros = esperarRegistros(acceso, 3);
        JsonNode sesion = deLaAccion(registros, "SESION_INICIADA");
        assertThat(sesion.get("accion").asString()).isEqualTo("SESION_INICIADA");
        assertThat(sesion.get("exito").asBoolean()).isTrue();
        assertThat(sesion.get("actorTipo").asString()).isEqualTo("USUARIO");
        assertThat(sesion.get("detalle").asString()).contains("PASSWORD");
        assertThat(sesion.get("ip").asString()).isEqualTo("127.0.0.1");
        assertThat(sesion.get("userAgent").asString()).isEqualTo("Navegador de pruebas");
        assertThat(sesion.get("requestId").asString()).isNotBlank();
        JsonNode fallo = deLaAccion(registros, "LOGIN_FALLIDO");
        assertThat(fallo.get("accion").asString()).isEqualTo("LOGIN_FALLIDO");
        assertThat(fallo.get("exito").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Registrar la tienda deja el alta de su administrador como el primer registro de la cadena")
    void primerRegistro() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE));
        JsonNode registros = esperarRegistros(acceso, 2);
        JsonNode alta = registros.get(registros.size() - 1);
        assertThat(alta.get("secuencia").asLong()).isOne();
        assertThat(alta.get("accion").asString()).isEqualTo("USUARIO_CREADO");
        assertThat(alta.get("detalle").asString()).isEqualTo("{\"rol\":\"ADMINISTRADOR\"}");
    }

    @Test
    @DisplayName("Cambiar la configuracion queda con sus politicas, sin nombres, y la cadena se verifica entera")
    void configuracionYVerificacion() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE));
        JsonNode antes = jsonMapper.readTree(mockMvc.perform(get("/api/v1/empresas/actual/configuracion")
                .with(SesionEnCookies.conSesion(acceso))).andReturn().getResponse().getContentAsString());
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("politicaEstructura", antes.get("politicaEstructura").asString());
        cuerpo.put("politicaSegundoFactor", "TODOS");
        cuerpo.put("version", antes.get("version").asLong());
        mockMvc.perform(put("/api/v1/empresas/actual/configuracion").with(SesionEnCookies.conSesion(acceso))
                        .contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk());

        JsonNode configuracion = deLaAccion(esperarRegistros(acceso, 3), "CONFIGURACION_CAMBIADA");
        assertThat(configuracion.get("accion").asString()).isEqualTo("CONFIGURACION_CAMBIADA");
        assertThat(configuracion.get("detalle").asString()).contains("\"politicaSegundoFactor\":\"TODOS\"")
                .doesNotContain("Berta");

        mockMvc.perform(get("/api/v1/auditoria/verificacion").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integra").value(true))
                .andExpect(jsonPath("$.registros").value(3));
    }

    @Test
    @DisplayName("Ni la API ni nadie la cambia o la borra: el trigger rechaza UPDATE, DELETE y TRUNCATE")
    void inmutable() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE));
        esperarRegistros(acceso, 1);

        // La API, como procesos_app, ni siquiera tiene el permiso (PR 51).
        assertThatThrownBy(() -> jdbc.update("update bitacora set accion = 'OTRA' where empresa_id = ?", empresaId))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        assertThatThrownBy(() -> jdbc.update("delete from bitacora where empresa_id = ?", empresaId))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("permission denied");
        // Y el dueno de la tabla, que si lo tiene, se encuentra con el trigger.
        assertThatThrownBy(() -> jdbc.execute("reset role; update bitacora set accion = 'OTRA' where empresa_id = "
                + empresaId)).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("La bitacora no se cambia");
        assertThatThrownBy(() -> jdbc.execute("reset role; delete from bitacora where empresa_id = " + empresaId))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("La bitacora no se cambia");
        assertThatThrownBy(() -> jdbc.execute("reset role; truncate bitacora")).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("La bitacora no se cambia");
    }

    @Test
    @DisplayName("Quien apaga el trigger y cambia una fila rompe la cadena en esa secuencia, y la verificacion lo dice")
    void alterarUnaFilaRompeLaCadena() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE));
        login(admin, CLAVE);
        login(admin, CLAVE);
        esperarRegistros(acceso, 4);

        // Como el dueno de la tabla, que es lo unico que puede apagar el trigger: la API no puede ni eso (PR 51).
        jdbc.execute("reset role; alter table bitacora disable trigger bitacora_sin_cambios; "
                + "update bitacora set ip = '198.51.100.1' where empresa_id = " + empresaId + " and secuencia = 2; "
                + "alter table bitacora enable trigger bitacora_sin_cambios");

        mockMvc.perform(get("/api/v1/auditoria/verificacion").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(jsonPath("$.integra").value(false))
                .andExpect(jsonPath("$.rotaEn").value(2))
                .andExpect(jsonPath("$.motivo").value("El hash no coincide con lo que dice el registro."));
    }

    @Test
    @DisplayName("Dos sucesos a la vez de la misma tienda quedan uno detras del otro, y la cadena sigue entera")
    void concurrencia() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int suceso = 0; suceso < 40; suceso++) {
                int numero = suceso;
                tareas.add(hilos.submit(() -> bitacoraService.anotar(new SucesoAuditable(empresaId,
                        SucesoAuditable.TipoDeActor.SISTEMA, null, "PRUEBA", null, null, true,
                        Map.of("numero", numero), null, null, null, null,
                        LocalDateTime.now(ZoneOffset.UTC).withNano(0)))));
            }
            for (Future<?> tarea : tareas) {
                tarea.get();
            }
        } finally {
            hilos.shutdown();
        }

        assertThat(jdbc.queryForList("select secuencia from bitacora where empresa_id = ? and accion = 'PRUEBA' "
                + "order by secuencia", Long.class, empresaId)).hasSize(40);
        assertThat(bitacoraService.verificar(empresaId).integra()).isTrue();
        assertThat(jdbc.queryForObject("select max(secuencia) - count(*) from bitacora where empresa_id = ?",
                Long.class, empresaId)).isZero();
    }

    @Test
    @DisplayName("Se lee de lo mas nuevo a lo mas viejo, de a paginas con cursor, y cada tienda solo ve la suya")
    void paginasYAislamiento() throws Exception {
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE));
        login(admin, CLAVE);
        login(admin, CLAVE);
        esperarRegistros(acceso, 4);

        JsonNode primera = pagina(acceso, "?tamano=3");
        assertThat(primera.get("registros")).hasSize(3);
        assertThat(primera.get("registros").get(0).get("secuencia").asLong()).isEqualTo(4);
        assertThat(primera.get("siguiente").asLong()).isEqualTo(2);
        JsonNode segunda = pagina(acceso, "?tamano=3&antes=" + primera.get("siguiente").asLong());
        assertThat(segunda.get("registros")).hasSize(1);
        assertThat(segunda.get("registros").get(0).get("secuencia").asLong()).isOne();
        assertThat(segunda.get("siguiente").isNull()).isTrue();

        mockMvc.perform(get("/api/v1/auditoria?tamano=51").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isBadRequest());
        String otra = "otra" + tiendas + "@bitacora.test";
        empresaService.registrar("Otra auditada " + tiendas, "9069" + tiendas + "000-1", otra, "Otra", otra, CLAVE);
        String suyo = SesionEnCookies.acceso(login(otra, CLAVE));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(pagina(suyo, "").get("registros"))
                .hasSize(2));
    }

    @Test
    @DisplayName("La exportacion en CSV no deja una formula que una hoja de calculo ejecute")
    void csvSinFormulas() throws Exception {
        mockMvc.perform(SesionEnCookies.login().header("User-Agent", "=HYPERLINK(\"https://evil.test\",\"x\")")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new LoginRequest(admin, CLAVE))))
                .andExpect(status().isOk());
        String acceso = SesionEnCookies.acceso(login(admin, CLAVE));
        esperarRegistros(acceso, 3);

        String csv = mockMvc.perform(get("/api/v1/auditoria/exportacion?formato=csv")
                        .with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("secuencia,momento,").contains("\"'=HYPERLINK(\"\"https://evil.test\"\"")
                .doesNotContain(",=HYPERLINK");
        mockMvc.perform(get("/api/v1/auditoria/exportacion?formato=json").with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].secuencia").value(1))
                .andExpect(jsonPath("$[0].accion").value("USUARIO_CREADO"));
    }

    /** Los sucesos llegan cada uno en su hilo: el orden entre dos casi simultaneos no se da por sentado. */
    private static JsonNode deLaAccion(JsonNode registros, String accion) {
        for (JsonNode registro : registros) {
            if (accion.equals(registro.get("accion").asString())) {
                return registro;
            }
        }
        throw new AssertionError("No hay ningun registro de " + accion + " en " + registros);
    }

    /** Espera a que la bitacora tenga al menos esos registros: se escribe despues del commit, en otro hilo. */
    private JsonNode esperarRegistros(String acceso, int cuantos) {
        JsonNode[] registros = new JsonNode[1];
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            registros[0] = pagina(acceso, "?tamano=50").get("registros");
            assertThat(registros[0].size()).isGreaterThanOrEqualTo(cuantos);
        });
        return registros[0];
    }

    private JsonNode pagina(String acceso, String consulta) throws Exception {
        return jsonMapper.readTree(mockMvc.perform(get("/api/v1/auditoria" + consulta)
                        .with(SesionEnCookies.conSesion(acceso)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions login(String email, String clave) throws Exception {
        return mockMvc.perform(SesionEnCookies.login().header("User-Agent", "Navegador de pruebas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(email, clave))));
    }
}
