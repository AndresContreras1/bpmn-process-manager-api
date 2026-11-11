package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
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
                correoDe("modelar"), "puentes-de-piedra");
        long proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long pool = api.poolDeLaTienda(proceso);
        long rol = api.crearRol("Gift desk");
        long lane = api.crearLane(pool, "Gift desk", rol);
        long idInicio = api.crearEvento(lane, "Gift requested", "INICIO", 20, 80);
        long idFin = api.crearEvento(lane, "Gift ready", "FIN", 340, 80);
        String inicio = bpmnIdDe(proceso, idInicio);
        String fin = bpmnIdDe(proceso, idFin);

        entrar(tienda);
        ir("/procesos/" + proceso + "/modelar");
        visible(elemento(inicio));

        // Un inicio y un fin sin nada entre ellos no es un proceso: el diagnostico lo dice y publicar esta cerrado
        espera.until(ExpectedConditions.presenceOfElementLocated(Paginas.Modelador.RESUMEN));
        String antes = textoDe(Paginas.Modelador.RESUMEN);
        assertFalse(antes.startsWith("0 "), "un diagrama a medias tiene que tener errores, y tiene: " + antes);
        // visible y no pulsable: pulsable espera a que se pueda pulsar, que es justo lo que aqui no tiene que pasar
        assertFalse(visible(Paginas.Modelador.PUBLICAR).isEnabled(), "con errores no se puede publicar");

        // Se agrega la tarea que falta desde el menu del inicio, y se le pone nombre al crearla
        pulsable(elemento(inicio)).click();
        pulsable(accion("append.append-task")).click();
        WebElement nombre = visible(Paginas.Modelador.EDICION_DIRECTA);
        nombre.sendKeys("Wrap the order", Keys.ENTER);

        // Conectar la tarea con el fin es lo que convierte tres cajas en un proceso; la tarea queda elegida al crearla
        pulsable(accion("connect")).click();
        new Actions(navegador).moveToElement(visible(elemento(fin))).click().perform();

        // Lo guarda solo: el estado se comprueba por la API, que es lo que de verdad quedo
        espera.until(navegador -> api.get("/procesos/" + proceso + "/diagrama").get("arcos").size() == 2);
        // Una tarea sin tipo no se puede correr: el diagnostico la marca, y se elige desde su menu de cambiar de tipo
        String tarea = api.get("/procesos/" + proceso + "/diagrama").get("actividades").get(0).get("bpmnId").asText();
        pulsable(elemento(tarea)).click();
        pulsable(accion("replace")).click();
        pulsable(By.cssSelector(".djs-popup [data-id='replace-with-user-task']")).click();

        espera.until(navegador -> textoDe(Paginas.Modelador.RESUMEN).startsWith("0 "));
        espera.until(ExpectedConditions.elementToBeClickable(Paginas.Modelador.PUBLICAR));
        assertEquals("Wrap the order",
                api.get("/procesos/" + proceso + "/diagrama").get("actividades").get(0).get("nombre").asText());

        pulsable(Paginas.Modelador.PUBLICAR).click();
        esperarTexto(Paginas.Modelador.PUBLICADO, "versión 1");

        // Y el proceso queda publicado, con su version, para quien lo mire desde fuera del editor
        ir("/procesos/" + proceso);
        esperarTexto(Paginas.Detalle.NOMBRE, "Gift wrapping");
        assertEquals("Publicado", textoDe(Paginas.Detalle.ESTADO));
        assertTrue(textoDe(Paginas.Detalle.CONTEO).contains("Tareas: 1"),
                "el visor tiene que contar la tarea que se acaba de modelar");
        assertTrue(textoDe(Paginas.Detalle.CONTEO).contains("Eventos: 2"),
                "y los dos eventos, que antes de F4b no se dibujaban");

        // Y la vuelta atras: retirar la version que se acaba de publicar, que es lo unico que se le puede cambiar
        assertEquals("VIGENTE", visible(Paginas.Detalle.version(1)).getDomAttribute("data-estado"));
        pulsable(Paginas.Detalle.version(1)).click();
        esperarTexto(Paginas.Detalle.CONGELADO, "tal como se publicó");
        pulsable(Paginas.Detalle.RETIRAR).click();
        pulsable(Paginas.Detalle.CONFIRMAR_RETIRO).click();
        esperarTexto(Paginas.Detalle.AVISO, "Se retiró la versión 1");
        espera.until(ExpectedConditions.attributeToBe(Paginas.Detalle.version(1), "data-estado", "RETIRADA"));
        assertTrue(textoDe(Paginas.Detalle.version(1)).contains("retirada"),
                "el selector dice, en espanol, que la version 1 ya no esta vigente");
    }

    private String bpmnIdDe(long proceso, long evento) {
        for (var uno : api.get("/procesos/" + proceso + "/diagrama").get("eventos")) {
            if (uno.get("id").asLong() == evento) {
                return uno.get("bpmnId").asText();
            }
        }
        throw new AssertionError("No hay un evento " + evento);
    }

    /** Un elemento del dibujo por su bpmnId. */
    private static By elemento(String bpmnId) {
        return By.cssSelector("[data-element-id='" + bpmnId + "']");
    }

    /** Una accion del menu contextual del elemento elegido. */
    private static By accion(String nombre) {
        return By.cssSelector(".djs-context-pad.open [data-action='" + nombre + "']");
    }
}
