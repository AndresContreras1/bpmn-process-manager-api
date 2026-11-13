package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;

/**
 * F11: ninguna pantalla tiene una violacion seria o critica de accesibilidad segun axe-core, con las reglas de WCAG 2.1
 * nivel AA, en el tema claro y en el oscuro. Las menores y las moderadas se ven en el informe de axe pero no cortan la
 * prueba: lo que deja fuera a alguien es lo serio.
 */
class AccesibilidadTest extends PruebaE2E {

    private static final List<String> REGLAS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");
    private static final List<String> GRAVES = List.of("serious", "critical");

    /** Lo que ve quien no entro todavia. */
    private static final List<String> PUBLICAS = List.of("/", "/login", "/registro");

    /** Cada pantalla de la barra lateral, y crear un proceso, con un administrador. */
    private static final List<String> CON_SESION = List.of("/procesos", "/portal", "/procesos/nuevo", "/tareas",
            "/casos", "/solicitudes", "/tablero", "/simulacion", "/usuarios", "/roles", "/configuracion",
            "/politica-de-publicacion", "/historial", "/cuenta", "/cuenta/notificaciones");

    @Test
    @DisplayName("las pantallas publicas no tienen violaciones serias de accesibilidad, en claro y en oscuro")
    void pantallasPublicas() {
        ir("/");
        revisarTodas(PUBLICAS);
        cambiarDeTema();
        revisarTodas(PUBLICAS);
    }

    @Test
    @DisplayName("las pantallas con sesion no tienen violaciones serias de accesibilidad, en claro y en oscuro")
    void pantallasConSesion() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Accesible", nitDe("accesibilidad"),
                correoDe("accesible"), "faroles-del-muelle");
        api.entrar(tienda.correo(), tienda.clave());
        api.crearProceso("Accessible order", "From the order to its delivery", "Sales");
        entrar(tienda);

        revisarTodas(CON_SESION);
        cambiarDeTema();
        revisarTodas(CON_SESION);
    }

    private void revisarTodas(List<String> rutas) {
        for (String ruta : rutas) {
            ir(ruta);
            visible(By.cssSelector("main h1"));
            quieta();
            Results resultado = new AxeBuilder().withTags(REGLAS).analyze(navegador);
            List<String> graves = resultado.getViolations().stream()
                    .filter(violacion -> GRAVES.contains(violacion.getImpact()))
                    .map(AccesibilidadTest::describir)
                    .toList();
            assertEquals(List.of(), graves, "Violaciones serias de accesibilidad en " + ruta + " con el tema "
                    + navegador.findElement(By.tagName("html")).getDomAttribute("data-bs-theme"));
        }
    }

    /** El boton del encabezado, como lo usa una persona: el tema queda guardado y vale en las demas pantallas. */
    private void cambiarDeTema() {
        pulsable(By.id("navbar-tema")).click();
        espera.until(navegador -> "dark".equals(navegador.findElement(By.tagName("html"))
                .getDomAttribute("data-bs-theme")));
    }

    private static String describir(Rule violacion) {
        return violacion.getId() + " (" + violacion.getImpact() + "): " + violacion.getHelp() + " en "
                + violacion.getNodes().stream().limit(3).map(nodo -> String.valueOf(nodo.getTarget())).toList();
    }
}
