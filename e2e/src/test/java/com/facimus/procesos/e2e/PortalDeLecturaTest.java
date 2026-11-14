package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F22: el portal de lectura, recorrido por quien solo lee. Elige una carpeta y encuentra el proceso con su revision
 * vencida; busca un paso por su nombre y la busqueda lo lleva a ese paso; en el diagrama
 * hace clic en un paso y lee su documentacion; recorre el arbol con el teclado; baja el documento del paso y el
 * manual que alguien ya armo. En ningun lado hay algo que cambiar, y las dos pantallas pasan por axe en los dos temas.
 *
 * El stack de las pruebas no tiene ClamAV: el documento queda disponible en cuanto la cola lo toma, y el manual tarda
 * lo que tarda la cola en armarlo, asi que esas esperas son mas largas que las demas.
 */
class PortalDeLecturaTest extends PruebaE2E {

    private static final Duration ESPERA_DE_LA_COLA = Duration.ofSeconds(60);
    private static final String INSTRUCCIONES = "Wrap the order twice and add the card on top.\n";
    private static final String LECTORA = "rumbo-de-los-faros";
    private static final List<String> REGLAS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    private static final List<String> GRAVES = List.of("serious", "critical");
    private static final By PANEL = By.id("portal-panel-elemento");
    private static final By DOCUMENTACION = By.id("portal-documentacion");

    @Test
    @DisplayName("quien solo lee recorre una carpeta, busca, abre un proceso, lee un paso y baja su documento y el "
            + "manual, sin nada que cambiar")
    void recorrerElPortal() throws IOException {
        api.registrarTienda("Tienda Portal", nitDe("f22-portal"), correoDe("f22-portal"),
                "linternas-del-puerto");
        api.crearUsuario("Lucia Lectora", correoDe("f22-lectora"), LECTORA, "SOLO_LECTURA");
        long carpeta = api.post("/carpetas", """
                {"nombre":"Fulfillment","padreId":null}
                """).get("id").asLong();
        long proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        String tarea = prepararProceso(proceso, carpeta, api.usuarioId());
        Path descargas = permitirDescargas();

        // Quien solo lee empieza en el portal, y lo tiene primero en la barra lateral
        entrarCon(correoDe("f22-lectora"), LECTORA);
        esperarUrl("/portal");
        visible(By.id("portal-titulo"));
        assertEquals("navbar-portal", visible(By.cssSelector("#barra-lateral .nav-link")).getDomAttribute("id"),
                "el portal va primero para quien solo lee");
        assertTrue(navegador.findElements(By.id("carpetas-crear")).isEmpty(), "las carpetas no se cambian aqui");
        revisarAccesibilidad("la entrada del portal");

        // La carpeta lista lo publicado, con la revision vencida a la vista
        pulsar(By.cssSelector("[data-carpeta='Fulfillment']"));
        esperarUrl("carpeta=" + carpeta);
        WebElement fila = visible(By.cssSelector("[data-testid='portal-proceso'][data-proceso='Gift wrapping']"));
        assertEquals(1, fila.findElements(By.cssSelector("[data-testid='portal-vencida']")).size(),
                "la revision vencida se ve en la lista");

        // La busqueda encuentra el paso por su nombre y lo abre elegido
        escribir(By.id("portal-texto"), "wrap");
        enviarConEnter(By.id("portal-texto"));
        esperarUrl("q=wrap");
        By paso = By.xpath("//li[@data-tipo='ELEMENTO']/a[@data-testid='portal-resultado-abrir'"
                + " and normalize-space()='Wrap the order']");
        pulsar(paso);
        esperarUrl("/portal/procesos/" + proceso);
        espera.until(pagina -> tarea.equals(pagina.findElement(PANEL).getDomAttribute("data-elemento")));
        esperarTexto(DOCUMENTACION, "twice");

        // La ficha: el dueno por su nombre, la clasificacion y la revision vencida
        esperarTexto(By.id("portal-ficha-dueno"), "Tienda Portal Admin");
        visible(By.id("portal-proceso-vencida"));
        esperarTexto(By.cssSelector("dd[data-campo='objetivo']"), "Every gift leaves wrapped");

        // Sin nada elegido el panel habla del proceso entero; un clic en el paso lo cambia a ese paso
        ir("/portal/procesos/" + proceso);
        esperarTexto(PANEL, "proceso entero");
        visible(By.cssSelector("#portal-diagrama [data-element-id='" + tarea + "'].marca-documentado"));
        pulsar(By.cssSelector("#portal-diagrama [data-element-id='" + tarea + "']"));
        espera.until(pagina -> tarea.equals(pagina.findElement(PANEL).getDomAttribute("data-elemento")));
        esperarTexto(DOCUMENTACION, "twice");
        esperarContenido(By.id("portal-anuncio"), "Wrap the order");

        // Con el teclado: el arbol de la estructura lleva al mismo paso
        visible(By.cssSelector("#estructura-arbol [tabindex='0']")).sendKeys(Keys.HOME);
        espera.until(pagina -> "1".equals(pagina.switchTo().activeElement().getDomAttribute("aria-level")));
        WebElement pool = navegador.switchTo().activeElement();
        String primera = pool.getDomAttribute("data-fila");
        pool.sendKeys(Keys.ENTER);
        espera.until(pagina -> primera.equals(pagina.findElement(PANEL).getDomAttribute("data-elemento")));
        assertTrue(navegador.findElements(By.id("estructura-acciones")).isEmpty(), "el arbol no ofrece cambiar nada");

        // El documento del paso se baja con su enlace firmado
        pulsar(By.cssSelector("#portal-diagrama [data-element-id='" + tarea + "']"));
        pulsar(By.cssSelector("[data-testid='portal-documento-bajar']"));
        assertEquals(INSTRUCCIONES, Files.readString(esperarDescarga(descargas, ".txt"), StandardCharsets.UTF_8));
        revisarAccesibilidad("la pagina de un proceso publicado");

        // El manual que ya armo alguien; quien solo lee no tiene boton para pedir otro
        assertTrue(navegador.findElements(By.id("portal-manual-pedir")).isEmpty(), "quien solo lee no pide el manual");
        pulsar(By.id("portal-manual-pdf"));
        byte[] pdf = Files.readAllBytes(esperarDescarga(descargas, ".pdf"));
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));

        // En ninguna parte del portal hay un control que cambie algo
        for (String control : List.of("proceso-detalle-editar", "proceso-detalle-modelar", "proceso-detalle-publicar",
                "ficha-guardar", "carpetas-nombre", "estructura-renombrar", "estructura-borrar")) {
            assertTrue(navegador.findElements(By.id(control)).isEmpty(), "el portal no tiene " + control);
        }
        assertTrue(navegador.findElements(By.cssSelector("#portal-proceso input, #portal-proceso textarea, "
                + "#portal-proceso select")).isEmpty(), "ni campos que escribir");
    }

    /**
     * Inicio, una tarea y fin, archivado en la carpeta con su dueno y una revision que ya paso, con la ficha, la
     * documentacion y un documento en la tarea; se publica y se arma su manual. Devuelve el bpmnId de la tarea.
     */
    private String prepararProceso(long proceso, long carpeta, long dueno) {
        long lane = api.crearLane(api.poolDeLaTienda(proceso), "Warehouse", api.crearRol("Warehouse"));
        long inicio = api.crearEvento(lane, "Order received", "INICIO", 20, 80);
        JsonNode envolver = api.post("/lanes/" + lane + "/actividades", """
                {"nombre":"Wrap the order","descripcion":"With the gift paper","tipoActividad":"USUARIO",
                 "posicionX":180,"posicionY":80}
                """);
        long fin = api.crearEvento(lane, "Order wrapped", "FIN", 340, 80);
        api.conectar(inicio, envolver.get("id").asLong());
        api.conectar(envolver.get("id").asLong(), fin);
        String tarea = envolver.get("bpmnId").asText();

        long version = api.get("/procesos/" + proceso).get("proceso").get("version").asLong();
        api.put("/procesos/" + proceso + "/ficha", """
                {"carpetaId":%d,"duenoId":%d,"proximaRevision":"2026-01-15","clasificacion":"CONFIDENCIAL",
                 "etiquetas":["gifts"],"version":%d}
                """.formatted(carpeta, dueno, version));
        api.put("/procesos/" + proceso + "/caracterizacion", """
                {"objetivo":"Every gift leaves wrapped and with its card"}
                """);
        api.put("/procesos/" + proceso + "/documentacion", """
                {"elemento":"%s","markdown":"Wrap the order **twice**."}
                """.formatted(tarea));
        api.post("/procesos/" + proceso + "/documentos?nombre=packing-instructions.txt&elemento=" + tarea,
                INSTRUCCIONES);
        api.publicar(proceso);
        api.post("/procesos/" + proceso + "/manuales", "");
        new WebDriverWait(navegador, ESPERA_DE_LA_COLA).until(cualquiera -> listos(proceso));
        return tarea;
    }

    /** El documento disponible y el manual armado: la cola ya paso por los dos. */
    private boolean listos(long proceso) {
        boolean documento = "DISPONIBLE".equals(api.get("/procesos/" + proceso + "/documentos").get(0)
                .get("estado").asText());
        boolean manual = "DISPONIBLE".equals(api.get("/procesos/" + proceso + "/manuales").get(0)
                .get("estado").asText());
        return documento && manual;
    }

    /**
     * Un clic en algo que puede quedar fuera de la ventana: lo trae al centro al instante, porque el desplazamiento
     * suave de Bootstrap terminaria despues del clic.
     */
    private void pulsar(By donde) {
        WebElement boton = pulsable(donde);
        ((JavascriptExecutor) navegador)
                .executeScript("arguments[0].scrollIntoView({block: 'center', behavior: 'instant'})", boton);
        pulsable(donde).click();
    }

    /** Lo que dice una region que solo oye un lector de pantalla: getText no lee lo que no se ve. */
    private void esperarContenido(By donde, String texto) {
        espera.until(pagina -> String.valueOf(pagina.findElement(donde).getDomProperty("textContent"))
                .contains(texto));
    }

    /** Chrome sin pantalla no baja nada si no se le dice adonde: una carpeta vacia, dentro de target. */
    private Path permitirDescargas() throws IOException {
        Path carpeta = Files.createDirectories(
                Path.of("target", "e2e", "descargas", "portal-" + System.currentTimeMillis())).toAbsolutePath();
        ((ChromeDriver) navegador).executeCdpCommand("Browser.setDownloadBehavior",
                Map.of("behavior", "allow", "downloadPath", carpeta.toString()));
        return carpeta;
    }

    /** Lo bajado, cuando Chrome termina: mientras baja, el archivo se llama .crdownload. */
    private Path esperarDescarga(Path carpeta, String extension) {
        return new WebDriverWait(navegador, Duration.ofSeconds(30)).until(cualquiera -> bajado(carpeta, extension));
    }

    private static Path bajado(Path carpeta, String extension) {
        try (Stream<Path> archivos = Files.list(carpeta)) {
            return archivos.filter(archivo -> archivo.getFileName().toString().endsWith(extension))
                    .findFirst()
                    .orElse(null);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void revisarAccesibilidad(String donde) {
        for (int vuelta = 0; vuelta < 2; vuelta++) {
            quieta();
            List<String> graves = new AxeBuilder().withTags(REGLAS).analyze(navegador).getViolations().stream()
                    .filter(violacion -> GRAVES.contains(violacion.getImpact()))
                    .map(PortalDeLecturaTest::describir).toList();
            String tema = navegador.findElement(By.tagName("html")).getDomAttribute("data-bs-theme");
            assertEquals(List.of(), graves, "Violaciones serias de accesibilidad en " + donde + " con el tema " + tema);
            cambiarDeTema(tema);
        }
    }

    /** Como en RevisionYComentariosTest: el tema cambia sin cerrar lo que este abierto. */
    private void cambiarDeTema(String actual) {
        String otro = "dark".equals(actual) ? "light" : "dark";
        ((JavascriptExecutor) navegador)
                .executeScript("document.documentElement.setAttribute('data-bs-theme', arguments[0])", otro);
        espera.until(pagina -> otro.equals(pagina.findElement(By.tagName("html")).getDomAttribute("data-bs-theme")));
    }

    private static String describir(Rule violacion) {
        return violacion.getId() + " (" + violacion.getImpact() + "): " + violacion.getHelp() + " en "
                + violacion.getNodes().stream().limit(3).map(nodo -> String.valueOf(nodo.getTarget())).toList();
    }
}
