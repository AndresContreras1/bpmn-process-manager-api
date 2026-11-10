package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WindowType;

/**
 * F13: la sesion son las cookies; la web no guarda en el navegador quien entro, lo pregunta a la API al arrancar. Y
 * las pestañas se avisan: salir en una cierra todas.
 */
class SesionEnPestanasTest extends PruebaE2E {

    @Test
    @DisplayName("recargar la pagina conserva la sesion aunque el navegador no guarde el perfil")
    void recargarConservaLaSesion() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Recarga", nitDe("recarga"), correoDe("recarga"),
                "lluvia-de-noviembre");
        entrar(tienda);

        List<?> guardado = (List<?>) ((JavascriptExecutor) navegador).executeScript(
                "return Object.keys(localStorage).map(k => localStorage.getItem(k))");
        assertFalse(guardado.stream().anyMatch(valor -> String.valueOf(valor).contains(tienda.correo())),
                "nada de lo que guarda el navegador dice quien entro: " + guardado);

        navegador.navigate().refresh();
        esperarUrl("/procesos");
        assertTrue(visible(Paginas.Sesion.USUARIO).isDisplayed(), "despues de recargar sigue dentro");
        visible(Paginas.Procesos.NUEVO);
    }

    @Test
    @DisplayName("salir en una pestaña lleva a las demas al login, y les dice por que")
    void salirEnUnaPestanaCierraLasDemas() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Pestanas", nitDe("pestanas"), correoDe("pestanas"),
                "faroles-en-la-niebla");
        entrar(tienda);
        String primera = navegador.getWindowHandle();

        navegador.switchTo().newWindow(WindowType.TAB);
        ir("/procesos");
        esperarUrl("/procesos");
        visible(Paginas.Sesion.USUARIO);
        String segunda = navegador.getWindowHandle();

        navegador.switchTo().window(primera);
        pulsable(Paginas.Sesion.USUARIO).click();
        pulsable(Paginas.Sesion.SALIR).click();
        esperarUrl("/login");

        navegador.switchTo().window(segunda);
        esperarUrl("/login?sesion=cerrada");
        esperarTexto(Paginas.Sesion.CERRADA_EN_OTRA, "otra pestaña");
        assertEquals(0, navegador.findElements(Paginas.Sesion.USUARIO).size(), "la otra pestaña ya no tiene a nadie");
    }
}
