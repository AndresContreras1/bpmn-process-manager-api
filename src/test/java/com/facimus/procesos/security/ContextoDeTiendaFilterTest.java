package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;

/** PR 51: de que tienda son las filas de cada peticion. */
class ContextoDeTiendaFilterTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Con sesion, las filas de su tienda y de ninguna otra")
    void conSesion() {
        entrarComo(7L);
        assertThat(alcanceDe("GET", "/api/v1/procesos")).isEqualTo(Alcance.deLaTienda(7L));
        assertThat(alcanceDe("POST", "/api/v1/procesos")).isEqualTo(Alcance.deLaTienda(7L));
    }

    @Test
    @DisplayName("Lo que busca a la persona por correo o por token es del sistema, aunque ya haya una sesion")
    void sinTiendaTodavia() {
        assertThat(alcanceDe("POST", "/api/v1/auth/login")).isEqualTo(Alcance.SISTEMA);
        assertThat(alcanceDe("POST", "/api/v1/empresas")).isEqualTo(Alcance.SISTEMA);
        entrarComo(7L);
        assertThat(alcanceDe("POST", "/api/v1/auth/refresh")).isEqualTo(Alcance.SISTEMA);
        assertThat(alcanceDe("GET", "/api/v1/auth/login")).isEqualTo(Alcance.deLaTienda(7L));
    }

    @Test
    @DisplayName("Cualquier otra peticion sin sesion, ninguna fila; y al terminar el hilo vuelve a lo que tenia")
    void sinSesionNinguna() throws Exception {
        assertThat(alcanceDe("GET", "/api/v1/procesos")).isEqualTo(Alcance.NINGUNA);

        AtomicReference<Alcance> durante = new AtomicReference<>();
        new ContextoDeTiendaFilter().doFilter(new MockHttpServletRequest("GET", "/api/v1/procesos"),
                new MockHttpServletResponse(), (peticion, respuesta) -> durante.set(ContextoDeTienda.actual()
                        .orElseThrow()));
        assertThat(durante.get()).isEqualTo(Alcance.NINGUNA);
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    private static Alcance alcanceDe(String metodo, String ruta) {
        return ContextoDeTiendaFilter.alcance(new MockHttpServletRequest(metodo, ruta));
    }

    private static void entrarComo(Long empresaId) {
        ApiPrincipal principal = new ApiPrincipal(1L, empresaId, RolAcceso.EDITOR, "ana@acme.com", "s", false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }
}
