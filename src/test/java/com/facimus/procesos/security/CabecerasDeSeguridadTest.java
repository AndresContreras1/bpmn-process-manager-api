package com.facimus.procesos.security;

import static com.facimus.procesos.security.ApiPrincipalRequestPostProcessor.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.facimus.procesos.common.model.RolAcceso;

/**
 * Las cabeceras de seguridad de la API, leidas en cada una de sus rutas. Sin sesion cada ruta responde lo que le toca
 * (401, 403 o 400), y la respuesta lleva las cabeceras igual; una ruta nueva entra sola en la prueba. Las de la web
 * las pone NGINX, y las comprueba el pipeline sobre el contenedor.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CabecerasDeSeguridadTest {

    private static final String CSP_DE_LA_API =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping rutas;

    @Test
    @DisplayName("Cada ruta de la API responde con las cabeceras de seguridad y una CSP que no deja cargar nada")
    void cadaRuta_respondeConLasCabeceras() throws Exception {
        SoftAssertions todas = new SoftAssertions();
        int revisadas = 0;
        for (RequestMappingInfo ruta : rutas.getHandlerMethods().keySet()) {
            HttpMethod metodo = ruta.getMethodsCondition().getMethods().stream()
                    .findFirst()
                    .map(RequestMethod::asHttpMethod)
                    .orElse(HttpMethod.GET);
            for (String patron : ruta.getPatternValues()) {
                if (patron.startsWith("/api/")) {
                    String url = patron.replaceAll("\\{[^}]+}", "1");
                    comprobar(todas, metodo + " " + url,
                            mockMvc.perform(request(metodo, url)).andReturn().getResponse());
                    revisadas++;
                }
            }
        }
        todas.assertAll();
        assertThat(revisadas).as("rutas revisadas").isGreaterThan(100);
    }

    @Test
    @DisplayName("Una respuesta con datos, de quien tiene sesion, lleva las mismas cabeceras y no se guarda en cache")
    void respuestaConSesion_llevaLasCabeceras() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/v1/procesos")
                .with(principal(RolAcceso.ADMINISTRADOR))).andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(200);
        SoftAssertions todas = new SoftAssertions();
        comprobar(todas, "GET /api/v1/procesos con sesion", respuesta);
        todas.assertThat(respuesta.getHeader("Cache-Control")).contains("no-store");
        todas.assertAll();
    }

    @Test
    @DisplayName("Lo que llego por HTTPS lleva HSTS por 365 dias con los subdominios; lo que llego por HTTP, no")
    void hsts_soloSobreHttps() throws Exception {
        String porHttps = mockMvc.perform(get("/api/v1/procesos").secure(true)).andReturn().getResponse()
                .getHeader("Strict-Transport-Security");
        String porHttp = mockMvc.perform(get("/api/v1/procesos")).andReturn().getResponse()
                .getHeader("Strict-Transport-Security");

        assertThat(porHttps).isEqualTo("max-age=31536000 ; includeSubDomains");
        assertThat(porHttp).isNull();
    }

    @Test
    @DisplayName("Swagger UI, fuera de prod, carga sus scripts y estilos del mismo origen y nada de afuera")
    void documentacion_cargaSoloLoSuyo() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(respuesta.getHeader("Content-Security-Policy")).isEqualTo("default-src 'self'; "
                + "img-src 'self' data:; style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'self'; "
                + "form-action 'self'; frame-ancestors 'none'");
        assertThat(respuesta.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    private static void comprobar(SoftAssertions todas, String ruta, MockHttpServletResponse respuesta) {
        todas.assertThat(respuesta.getHeader("Content-Security-Policy")).as(ruta).isEqualTo(CSP_DE_LA_API);
        todas.assertThat(respuesta.getHeader("X-Content-Type-Options")).as(ruta).isEqualTo("nosniff");
        todas.assertThat(respuesta.getHeader("X-Frame-Options")).as(ruta).isEqualTo("DENY");
        todas.assertThat(respuesta.getHeader("Referrer-Policy")).as(ruta).isEqualTo("no-referrer");
        todas.assertThat(respuesta.getHeader("Permissions-Policy")).as(ruta).isEqualTo("accelerometer=(), camera=(), "
                + "geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()");
        todas.assertThat(respuesta.getHeader("Cross-Origin-Opener-Policy")).as(ruta).isEqualTo("same-origin");
        todas.assertThat(respuesta.getHeader("Cross-Origin-Resource-Policy")).as(ruta).isEqualTo("same-origin");
    }
}
