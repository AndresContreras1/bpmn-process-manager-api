package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** El modelador sobre bpmn-js: dibuja lo que hay, guarda solo y deja ver de donde viene. */
class ModeladorBpmnTest extends PruebaE2E {

    private static final By ESTADO = By.id("modelador-estado");
    private static final By LOGO = By.cssSelector(".bjs-powered-by");

    private long sembrar(String semilla) {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda " + semilla, nitDe(semilla), correoDe(semilla),
                "puentes-de-piedra");
        long proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long pool = api.poolDeLaTienda(proceso);
        long lane = api.crearLane(pool, "Gift desk", api.crearRol("Gift desk"));
        long inicio = api.crearEvento(lane, "Gift requested", "INICIO", 20, 80);
        long tarea = api.crearActividad(lane, "Wrap the order", "USUARIO", 180, 80);
        long fin = api.crearEvento(lane, "Gift ready", "FIN", 340, 80);
        api.conectar(inicio, tarea);
        api.conectar(tarea, fin);
        entrar(tienda);
        return proceso;
    }

    @Test
    @DisplayName("el logo de bpmn.io se ve y nada lo tapa")
    void elLogoSeVe() {
        long proceso = sembrar("logo");
        ir("/procesos/" + proceso + "/modelar");

        WebElement logo = visible(LOGO);

        assertEquals("https://bpmn.io", logo.getDomAttribute("href").replaceAll("/$", ""));
        Object encima = ((JavascriptExecutor) navegador).executeScript("""
                const logo = arguments[0];
                const caja = logo.getBoundingClientRect();
                const punto = document.elementFromPoint(caja.left + caja.width / 2, caja.top + caja.height / 2);
                return logo === punto || logo.contains(punto);
                """, logo);
        assertEquals(Boolean.TRUE, encima, "algo esta tapando el logo de bpmn.io");
    }

    @Test
    @DisplayName("renombrar una tarea en el panel se guarda solo, y la API lo dice")
    void renombrarSeGuardaSolo() {
        long proceso = sembrar("renombrar");
        ir("/procesos/" + proceso + "/modelar");
        String id = api.get("/procesos/" + proceso + "/diagrama").get("actividades").get(0).get("bpmnId").asText();

        pulsable(By.cssSelector("[data-element-id='" + id + "']")).click();
        WebElement nombre = visible(By.id("propiedad-nombre"));
        nombre.clear();
        nombre.sendKeys("Wrap with care", Keys.TAB);

        espera.until(ExpectedConditions.attributeToBe(ESTADO, "data-estado", "pendiente"));
        espera.until(ExpectedConditions.attributeToBe(ESTADO, "data-estado", "guardado"));
        assertEquals("Wrap with care",
                api.get("/procesos/" + proceso + "/diagrama").get("actividades").get(0).get("nombre").asText());
        assertTrue(textoDe(By.id("modelador-resumen")).startsWith("0"), "el proceso sigue limpio");
    }
}
