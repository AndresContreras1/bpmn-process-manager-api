package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.support.ui.Select;

/**
 * F12: la web en otro idioma, de punta a punta. El navegador pide ingles; la organizacion, registrada por la API sin
 * decir idioma, trabaja en espanol. Lo que vale es lo que la persona eligio, despues lo de su organizacion, y solo sin
 * sesion lo que pide el navegador.
 */
class RecorrerEnInglesTest extends PruebaE2E {

    @Override
    protected String idiomaDelNavegador() {
        return "en-US";
    }

    @Test
    @DisplayName("sin sesion la web sale en el idioma del navegador; al entrar, en el de la organizacion; y quien elige "
            + "ingles la recorre en ingles, tambien por un enlace sin idioma")
    void recorreLaWebEnIngles() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Idiomas", nitDe("idiomas"), correoDe("idiomas"),
                "faro-sobre-la-bahia");
        long proceso = api.crearProceso("Returns desk", "Take back what a customer returns.", "Returns");

        // Sin sesion, la portada en el idioma que pide el navegador
        ir("/");
        esperarUrl("/en-US/");
        esperarTexto(Paginas.Idioma.LOGIN, "Sign in");

        // Al entrar, la organizacion trabaja en espanol y la web se abre en el suyo
        entrar(tienda);
        esperarUrl("/es-CO/procesos");
        esperarTexto(Paginas.Idioma.SECCION_PROCESOS, "Procesos");

        // La persona elige ingles en la barra: queda guardado en su cuenta y la misma pantalla se abre en ingles
        pulsable(Paginas.Idioma.MENU).click();
        pulsable(Paginas.Idioma.opcion("en-US")).click();
        esperarUrl("/en-US/procesos");
        esperarTexto(Paginas.Idioma.SECCION_PROCESOS, "Processes");
        Cookie idioma = navegador.manage().getCookieNamed("idioma");
        assertEquals("en-US", idioma.getValue(), "la eleccion queda en la cookie que lee NGINX");

        // Un enlace sin idioma va al elegido, y el proceso se lee en ingles
        ir("/procesos/" + proceso);
        esperarUrl("/en-US/procesos/" + proceso);
        esperarTexto(Paginas.Detalle.NOMBRE, "Returns desk");
        assertEquals("Draft", textoDe(Paginas.Detalle.ESTADO));

        // La cuenta dice que el idioma es suyo, y la fecha de registro sale en ingles
        ir("/cuenta");
        esperarUrl("/en-US/cuenta");
        espera.until(driver -> "en-US".equals(new Select(visible(Paginas.Idioma.CUENTA_IDIOMA))
                .getFirstSelectedOption().getDomAttribute("value")));
        assertTrue(textoDe(Paginas.Idioma.CUENTA_FECHA).matches("[A-Z][a-z]+ \\d{1,2}, \\d{4}"),
                "la fecha larga en ingles es como November 10, 2026, y salio " + textoDe(Paginas.Idioma.CUENTA_FECHA));

        // Volver a seguir a la organizacion devuelve la web al espanol
        new Select(visible(Paginas.Idioma.CUENTA_IDIOMA)).selectByValue("");
        pulsable(Paginas.Idioma.CUENTA_GUARDAR).click();
        esperarUrl("/es-CO/cuenta");
        assertEquals("es-CO", navegador.manage().getCookieNamed("idioma").getValue());
    }
}
