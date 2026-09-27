package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * El camino que hace que este proyecto exista: un diagrama que no se puede publicar, se arregla en el editor, y se
 * publica.
 *
 * El esqueleto lo siembra la API. Lo que se prueba aqui es el editor, y montar el esqueleto a base de clics haria
 * que esta prueba fallase por cosas que no son el editor.
 */
class ModelarYPublicarTest extends PruebaE2E {

    @Test
    @DisplayName("el diagnostico impide publicar, el editor lo arregla y entonces publica")
    void arreglaElDiagramaEnElEditorYLoPublica() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Modelar", nitDe("modelar"),
                correoDe("modelar"), "modelar12345");
        long proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long pool = api.poolDeLaTienda(proceso);
        long rol = api.crearRol("Gift desk");
        long lane = api.crearLane(pool, "Gift desk", rol);
        api.crearEvento(lane, "Gift requested", "INICIO", 20, 80);
        api.crearEvento(lane, "Gift ready", "FIN", 340, 80);

        entrar(tienda);
        ir("/procesos/" + proceso + "/editar-diagrama");
        esperarTexto(Paginas.Editor.TITULO, "Gift wrapping");

        // Un inicio y un fin sin nada entre ellos no es un proceso: el diagnostico lo dice y publicar esta cerrado
        String antes = textoDe(Paginas.Editor.CUENTA);
        assertFalse(antes.startsWith("0 errors"), "un diagrama a medias tiene que tener errores, y tiene: " + antes);
        // visible y no pulsable: pulsable espera a que se pueda pulsar, que es justo lo que aqui no tiene que pasar
        assertFalse(visible(Paginas.Editor.PUBLICAR).isEnabled(), "con errores no se puede publicar");

        // Se agrega la tarea que falta, desde la paleta, sobre la lane elegida
        pulsable(Paginas.Editor.LANE).click();
        pulsable(By.id("paleta-actividad")).click();
        esperarTexto(Paginas.Editor.ELEGIDO, "New task");
        escribir(Paginas.Editor.NOMBRE_ACTIVIDAD, "Wrap the order");
        pulsable(Paginas.Editor.GUARDAR).click();
        esperarTexto(Paginas.Editor.ELEGIDO, "Wrap the order");

        // Conectar el inicio con la tarea y la tarea con el fin es lo que convierte tres cajas en un proceso
        conectar("Gift requested", "Wrap the order");
        conectar("Wrap the order", "Gift ready");
        espera.until(ExpectedConditions.visibilityOfElementLocated(Paginas.Editor.LIMPIO));
        assertTrue(textoDe(Paginas.Editor.CUENTA).startsWith("0 errors"), "el diagrama tendria que quedar limpio");

        pulsable(Paginas.Editor.PUBLICAR).click();
        esperarTexto(Paginas.Editor.PUBLICADO, "version 1");

        // Y el proceso queda publicado, con su version, para quien lo mire desde fuera del editor
        ir("/procesos/" + proceso);
        esperarTexto(Paginas.Detalle.NOMBRE, "Gift wrapping");
        assertEquals("Published", textoDe(Paginas.Detalle.ESTADO));
        assertTrue(textoDe(Paginas.Detalle.CONTEO).contains("Tasks: 1"),
                "el visor tiene que contar la tarea que se acaba de modelar");
        assertTrue(textoDe(Paginas.Detalle.CONTEO).contains("Events: 2"),
                "y los dos eventos, que antes de F4b no se dibujaban");
    }

    /** Dos clics en el lienzo: el modo de conectar no elige nodos, los une. */
    private void conectar(String desde, String hasta) {
        pulsable(By.id("proceso-editor-conectar")).click();
        nodoDelLienzo(desde).click();
        nodoDelLienzo(hasta).click();
    }

    /** Un nodo del dibujo por su etiqueta accesible, que es como lo encontraria alguien con un lector de pantalla. */
    private WebElement nodoDelLienzo(String nombre) {
        By nodos = By.cssSelector("#diagrama-bpmn [role='button']");
        espera.until(ExpectedConditions.presenceOfElementLocated(nodos));
        List<WebElement> candidatos = navegador.findElements(nodos);
        return candidatos.stream()
                .filter(nodo -> {
                    String etiqueta = nodo.getDomAttribute("aria-label");
                    return etiqueta != null && etiqueta.contains(nombre);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay ningun nodo llamado \"" + nombre + "\" en el diagrama"));
    }
}
