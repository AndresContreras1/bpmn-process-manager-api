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

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.selenium.AxeBuilder;

/** Dos personas sobre el mismo proceso, y lo que cambio entre dos estados suyos. */
class PresenciaYComparacionTest extends PruebaE2E {

    private static final By ESTADO = By.id("modelador-estado");
    private static final By PALETA = By.cssSelector(".djs-palette");
    private static final By BLOQUEO = By.id("modelador-bloqueo");
    private static final By TOMAR = By.id("modelador-tomar-control");

    private long proceso;
    private long lane;
    private ApiDeDatos.Tienda tienda;
    private ApiDeDatos otra;

    private void sembrar(String semilla) {
        tienda = api.registrarTienda("Tienda " + semilla, nitDe(semilla), correoDe(semilla), "puentes-de-piedra");
        proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long pool = api.poolDeLaTienda(proceso);
        lane = api.crearLane(pool, "Gift desk", api.crearRol("Gift desk"));
        long inicio = api.crearEvento(lane, "Gift requested", "INICIO", 20, 80);
        long tarea = api.crearActividad(lane, "Wrap the order", "USUARIO", 180, 80);
        long fin = api.crearEvento(lane, "Gift ready", "FIN", 340, 80);
        api.conectar(inicio, tarea);
        api.conectar(tarea, fin);
    }

    private void entrarOtraPersona(String semilla) {
        String correo = correoDe(semilla + "-otra");
        api.crearUsuario("Sara Rios", correo, "faroles-del-muelle", "EDITOR");
        otra = new ApiDeDatos(API);
        otra.entrar(correo, "faroles-del-muelle");
    }

    private void abrirModelador() {
        entrar(tienda);
        ir("/procesos/" + proceso + "/modelar");
        visible(PALETA);
        espera.until(navegador -> api.get("/procesos/" + proceso + "/bloqueo").get("mio").asBoolean());
    }

    @Test
    @DisplayName("quien esta se ve, y otra persona puede tomar el control y soltarlo")
    void tomarElControl() {
        sembrar("control");
        entrarOtraPersona("control");
        abrirModelador();
        visible(By.cssSelector("#modelador-presencia [data-usuario]"));

        otra.post("/procesos/" + proceso + "/bloqueo/tomar-control", "");

        esperarTexto(BLOQUEO, "Sara Rios");
        espera.until(ExpectedConditions.invisibilityOfElementLocated(PALETA));

        otra.post("/procesos/" + proceso + "/bloqueo/soltar", "");
        pulsable(TOMAR).click();

        visible(PALETA);
        espera.until(ExpectedConditions.invisibilityOfElementLocated(BLOQUEO));
        assertTrue(api.get("/procesos/" + proceso + "/bloqueo").get("mio").asBoolean());
    }

    @Test
    @DisplayName("si otro guardo mientras tanto, se puede comparar y recargar")
    void conflictoConCompararYRecargar() {
        sembrar("conflicto");
        abrirModelador();
        String tarea = api.get("/procesos/" + proceso + "/diagrama").get("actividades").get(0).get("bpmnId").asText();

        long nueva = api.crearActividad(lane, "Add a card", "USUARIO", 180, 200);

        pulsable(By.cssSelector("[data-element-id='" + tarea + "']")).click();
        WebElement nombre = visible(By.id("propiedad-nombre"));
        nombre.clear();
        nombre.sendKeys("Wrap with care", Keys.TAB);
        espera.until(ExpectedConditions.attributeToBe(ESTADO, "data-estado", "conflicto"));

        pulsable(By.id("modelador-comparar")).click();

        esperarTexto(By.id("comparacion-quitados"), "Add a card");
        esperarTexto(By.id("comparacion-cambiados"), "Wrap with care");

        pulsable(By.id("modelador-recargar")).click();
        espera.until(ExpectedConditions.attributeToBe(ESTADO, "data-estado", "guardado"));
        String nuevaId = bpmnIdDe(nueva);
        visible(By.cssSelector("[data-element-id='" + nuevaId + "']"));
    }

    private String bpmnIdDe(long actividad) {
        for (var una : api.get("/procesos/" + proceso + "/diagrama").get("actividades")) {
            if (una.get("id").asLong() == actividad) {
                return una.get("bpmnId").asText();
            }
        }
        throw new AssertionError("No hay una actividad " + actividad);
    }

    @Test
    @DisplayName("la comparacion dice con color y con texto lo que cambio desde la version publicada")
    void compararContraLaVersion() {
        sembrar("comparar");
        api.publicar(proceso);
        long nueva = api.crearActividad(lane, "Add a card", "USUARIO", 180, 200);

        entrar(tienda);
        ir("/procesos/" + proceso + "/comparar");

        esperarTexto(By.id("comparacion-agregados"), "Add a card");
        visible(By.cssSelector("[data-element-id='" + bpmnIdDe(nueva) + "'].marca-agregado"));
        quieta();

        Results resultado = new AxeBuilder().withTags(List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa"))
                .analyze(navegador);
        List<String> graves = resultado.getViolations().stream()
                .filter(violacion -> List.of("serious", "critical").contains(violacion.getImpact()))
                .map(violacion -> violacion.getId() + " en "
                        + violacion.getNodes().stream().limit(3).map(nodo -> String.valueOf(nodo.getTarget())).toList())
                .toList();
        assertEquals(List.of(), graves);
    }
}
