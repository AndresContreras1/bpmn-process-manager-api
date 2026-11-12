package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.facimus.procesos.gestion.dto.request.LoginRequest;
import com.facimus.procesos.gestion.dto.request.PreferenciasRequest;
import com.facimus.procesos.gestion.service.EmpresaService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/**
 * D29: el token CSRF dura lo que dura la sesion. La web manda varias peticiones a la vez: si cada una lo cambiara, una
 * escritura podria salir con la cabecera de un token y la cookie del siguiente, y la API la rechazaria con 403. Cambia
 * al entrar y al salir, para que un token que alguien conocia antes del login no le sirva despues.
 *
 * <p>Las peticiones salen como de un navegador: cada respuesta deja o borra sus cookies, y lo que cambia algo lleva en
 * {@code X-XSRF-TOKEN} el valor de la cookie {@code XSRF-TOKEN}.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TokenCsrfDeLaSesionIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static final String XSRF = "XSRF-TOKEN";
    private static int tiendas;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private EmpresaService empresaService;

    /** Las cookies que guarda el navegador, por nombre. */
    private final Map<String, String> cookies = new LinkedHashMap<>();

    private String admin;

    @BeforeEach
    void registrarTienda() {
        int numero = ++tiendas;
        admin = "admin" + numero + "@token-csrf.test";
        empresaService.registrar("Tienda del token " + numero, "9091" + numero + "000-1", admin, "Tomas Token",
                admin, CLAVE);
    }

    @Test
    @DisplayName("Leer con la sesion no cambia el token CSRF: una escritura que salio con el token de antes de una "
            + "lectura en paralelo sigue pasando")
    void lasLecturasNoCambianElToken() throws Exception {
        arrancarYEntrar();
        // La web lee el token para una escritura mientras dos lecturas siguen en camino
        String leidoAntes = cookies.get(XSRF);

        MvcResult sesion = pedir(get("/api/v1/auth/sesion"));
        MvcResult procesos = pedir(get("/api/v1/procesos"));

        assertThat(sesion.getResponse().getStatus()).isEqualTo(200);
        assertThat(procesos.getResponse().getStatus()).isEqualTo(200);
        assertThat(escritos(sesion)).as("GET /api/v1/auth/sesion no toca la cookie XSRF-TOKEN").isEmpty();
        assertThat(escritos(procesos)).as("GET /api/v1/procesos no toca la cookie XSRF-TOKEN").isEmpty();

        // La escritura sale cuando las lecturas ya respondieron: la cabecera es la que leyo, la cookie la de ahora
        MvcResult preferencias = pedir(put("/api/v1/auth/preferencias")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new PreferenciasRequest("en-US", null))), leidoAntes);
        assertThat(preferencias.getResponse().getStatus())
                .as("cookie %s, cabecera %s", cookies.get(XSRF), leidoAntes).isEqualTo(200);
    }

    @Test
    @DisplayName("Entrar deja un token CSRF nuevo, y salir otro")
    void entrarYSalirLoCambian() throws Exception {
        String delArranque = arrancar();

        MvcResult login = entrar();
        String deLaSesion = cookies.get(XSRF);
        assertThat(escritos(login)).as("el login escribe la cookie XSRF-TOKEN").isNotEmpty();
        assertThat(deLaSesion).isNotBlank().isNotEqualTo(delArranque);

        MvcResult salida = pedir(post("/api/v1/auth/logout"));
        assertThat(salida.getResponse().getStatus()).isEqualTo(204);
        assertThat(cookies.get(XSRF)).isNotBlank().isNotEqualTo(deLaSesion);
    }

    @Test
    @DisplayName("Renovar la sesion es seguir en ella: el token CSRF no cambia")
    void renovarNoLoCambia() throws Exception {
        arrancarYEntrar();
        String deLaSesion = cookies.get(XSRF);

        MvcResult renovada = pedir(post("/api/v1/auth/refresh"));

        assertThat(renovada.getResponse().getStatus()).isEqualTo(200);
        assertThat(escritos(renovada)).as("POST /api/v1/auth/refresh no toca la cookie XSRF-TOKEN").isEmpty();
        assertThat(cookies.get(XSRF)).isEqualTo(deLaSesion);
    }

    /** Lo que hace la web al arrancar: pedir el token CSRF. */
    private String arrancar() throws Exception {
        assertThat(pedir(get("/api/v1/auth/csrf")).getResponse().getStatus()).isEqualTo(204);
        return cookies.get(XSRF);
    }

    private MvcResult entrar() throws Exception {
        MvcResult login = pedir(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new LoginRequest(admin, CLAVE))));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return login;
    }

    private void arrancarYEntrar() throws Exception {
        arrancar();
        entrar();
    }

    /** Una peticion con las cookies guardadas y, en la cabecera, el token CSRF que tiene ahora la cookie. */
    private MvcResult pedir(MockHttpServletRequestBuilder peticion) throws Exception {
        return pedir(peticion, cookies.get(XSRF));
    }

    /** Una peticion con las cookies guardadas y ese token CSRF en la cabecera. */
    private MvcResult pedir(MockHttpServletRequestBuilder peticion, String cabecera) throws Exception {
        cookies.forEach((nombre, valor) -> peticion.cookie(new Cookie(nombre, valor)));
        if (cabecera != null) {
            peticion.header("X-XSRF-TOKEN", cabecera);
        }
        MvcResult resultado = mockMvc.perform(peticion).andReturn();
        // Cada Set-Cookie pisa la cookie de su nombre; una vencida la borra
        for (Cookie cookie : resultado.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        return resultado;
    }

    /** Los Set-Cookie de XSRF-TOKEN de la respuesta, tambien los que la borran. */
    private static List<String> escritos(MvcResult resultado) {
        return resultado.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(XSRF + "="))
                .toList();
    }
}
