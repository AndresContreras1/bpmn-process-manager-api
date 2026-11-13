package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.fasterxml.jackson.databind.JsonNode;

/** El modelador sin raton: todo lo que se hace en el lienzo se puede hacer desde el arbol, con el teclado. */
class VistaEstructuraTest extends PruebaE2E {

    private static final By ARBOL = By.id("estructura-arbol");
    private static final By ESTADO = By.id("modelador-estado");
    private static final By ANUNCIO = By.id("estructura-anuncio");
    private static final List<String> REGLAS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    private static final List<String> GRAVES = List.of("serious", "critical");

    private long proceso;
    private long lanePedidos;
    private long laneBodega;

    private void sembrar(String semilla) {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda " + semilla, nitDe(semilla), correoDe(semilla),
                "puentes-de-piedra");
        proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long pool = api.poolDeLaTienda(proceso);
        lanePedidos = api.crearLane(pool, "Orders", api.crearRol("Orders"));
        laneBodega = api.crearLane(pool, "Warehouse", api.crearRol("Warehouse"));
        long inicio = api.crearEvento(lanePedidos, "Gift requested", "INICIO", 20, 80);
        long tarea = api.crearActividad(lanePedidos, "Wrap the order", "USUARIO", 180, 80);
        api.conectar(inicio, tarea);
        entrar(tienda);
        ir("/procesos/" + proceso + "/modelar");
        visible(By.cssSelector(".djs-element.djs-shape"));
        pulsable(By.id("modelador-estructura")).click();
        visible(ARBOL);
    }

    private JsonNode diagrama() {
        return api.get("/procesos/" + proceso + "/diagrama");
    }

    private By fila(String bpmnId) {
        return By.cssSelector("[data-fila='" + bpmnId + "']");
    }

    private String bpmnIdDeLane(long lane) {
        for (JsonNode una : diagrama().get("lanes")) {
            if (una.get("id").asLong() == lane) {
                return una.get("bpmnId").asText();
            }
        }
        throw new AssertionError("No hay una lane " + lane);
    }

    private String enfocado() {
        return navegador.switchTo().activeElement().getDomAttribute("data-fila");
    }

    private void esperarGuardado() {
        espera.until(ExpectedConditions.attributeToBe(ESTADO, "data-estado", "guardado"));
    }

    @Test
    @DisplayName("las flechas recorren el arbol: pool, lanes y nodos")
    void lasFlechasRecorrenElArbol() {
        sembrar("arbol");
        String lane = bpmnIdDeLane(lanePedidos);
        WebElement primero = visible(By.cssSelector("#estructura-arbol [tabindex='0']"));
        primero.sendKeys(Keys.ARROW_DOWN);

        espera.until(navegador -> lane.equals(enfocado()));
        assertEquals("2", navegador.switchTo().activeElement().getDomAttribute("aria-level"));
        navegador.switchTo().activeElement().sendKeys(Keys.ARROW_LEFT);
        espera.until(ExpectedConditions.attributeToBe(fila(lane), "aria-expanded", "false"));
        navegador.switchTo().activeElement().sendKeys(Keys.ARROW_RIGHT);
        espera.until(ExpectedConditions.attributeToBe(fila(lane), "aria-expanded", "true"));
    }

    @Test
    @DisplayName("crear, conectar, mover de lane, renombrar y borrar sin arrastrar, y la API lo dice")
    void todasLasAccionesSinArrastrar() {
        sembrar("acciones");
        String laneOrigen = bpmnIdDeLane(lanePedidos);
        String tarea = diagrama().get("actividades").get(0).get("bpmnId").asText();

        // Crear en la lane
        visible(fila(laneOrigen)).click();
        new Select(visible(By.id("estructura-tipo"))).selectByValue("bpmn:EndEvent");
        visible(By.id("estructura-nombre-nuevo")).sendKeys("Gift ready", Keys.ENTER);
        espera.until(ExpectedConditions.textToBePresentInElementLocated(ANUNCIO, "Gift ready"));
        esperarGuardado();
        espera.until(navegador -> diagrama().get("eventos").size() == 2);

        // Conectar
        visible(fila(tarea)).click();
        new Select(visible(By.id("estructura-destino"))).selectByVisibleText("Gift ready");
        pulsable(By.id("estructura-conectar")).click();
        espera.until(navegador -> diagrama().get("arcos").size() == 2);

        // Mover de lane
        new Select(visible(By.id("estructura-lane"))).selectByVisibleText("Warehouse");
        pulsable(By.id("estructura-mover")).click();
        espera.until(navegador -> diagrama().get("actividades").get(0).get("laneId").asLong() == laneBodega);

        // Renombrar
        WebElement nombre = visible(By.id("estructura-nombre"));
        nombre.clear();
        nombre.sendKeys("Wrap with care", Keys.ENTER);
        espera.until(navegador -> "Wrap with care".equals(
                diagrama().get("actividades").get(0).get("nombre").asText()));

        // Borrar desde el teclado
        visible(fila(tarea)).click();
        navegador.switchTo().activeElement().sendKeys(Keys.DELETE);
        espera.until(navegador -> diagrama().get("actividades").isEmpty());
        assertTrue(textoDelAnuncio().contains("Wrap with care"), "el borrado se anuncia: " + textoDelAnuncio());
    }

    private String textoDelAnuncio() {
        return navegador.findElement(ANUNCIO).getDomProperty("textContent");
    }

    @Test
    @DisplayName("el modelador con la vista de estructura no tiene violaciones serias de accesibilidad")
    void sinViolacionesSerias() {
        sembrar("axe");
        quieta();

        Results resultado = new AxeBuilder().withTags(REGLAS).analyze(navegador);

        List<String> graves = resultado.getViolations().stream()
                .filter(violacion -> GRAVES.contains(violacion.getImpact()))
                .map(violacion -> violacion.getId() + " en "
                        + violacion.getNodes().stream().limit(3).map(nodo -> String.valueOf(nodo.getTarget())).toList())
                .toList();
        assertEquals(List.of(), graves);
    }

    @Test
    @DisplayName("nada que se pulse en el menu contextual mide menos de 24 px")
    void objetivosDeVeinticuatroPixeles() {
        sembrar("objetivos");
        String tarea = diagrama().get("actividades").get(0).get("bpmnId").asText();
        pulsable(By.cssSelector("[data-element-id='" + tarea + "']")).click();
        visible(By.cssSelector(".djs-context-pad.open .entry"));

        for (WebElement entrada : navegador.findElements(By.cssSelector(".djs-context-pad.open .entry"))) {
            assertTrue(entrada.getRect().getWidth() >= 24 && entrada.getRect().getHeight() >= 24,
                    entrada.getDomAttribute("data-action") + " mide " + entrada.getRect().getDimension());
        }
    }
}
