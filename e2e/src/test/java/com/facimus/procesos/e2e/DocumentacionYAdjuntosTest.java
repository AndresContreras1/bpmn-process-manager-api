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
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.facimus.procesos.e2e.Paginas.Documentacion;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F20: lo que se escribe y se adjunta a un proceso, desde la web. La documentacion se ve como el HTML que devuelve la
 * API; la ficha se guarda entera; un archivo adjunto llega a DISPONIBLE y se baja con su enlace firmado; y el manual
 * se pide, se espera y se baja en PDF.
 *
 * El stack de las pruebas no tiene ClamAV, asi que un documento queda disponible sin revisar en cuanto la cola lo
 * toma, y el manual tarda lo que tarda la cola en armarlo: esas dos esperas son mas largas que las demas.
 */
class DocumentacionYAdjuntosTest extends PruebaE2E {

    private static final Duration ESPERA_DE_LA_COLA = Duration.ofSeconds(60);
    private static final String MARKDOWN = "## Packing\n\n- Wrap the order **twice**";
    private static final String INSTRUCCIONES = "Wrap the order twice and add the card on top.\n";
    private static final List<String> REGLAS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");
    private static final List<String> GRAVES = List.of("serious", "critical");
    private static final List<String> PESTANAS = List.of("texto", "ficha", "adjuntos", "manual");

    @Test
    @DisplayName("se documenta el proceso con su vista previa, se llena la ficha, se adjunta y se baja un archivo, y se "
            + "baja el manual")
    void documentarAdjuntarYBajarElManual() throws IOException {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Manuales", nitDe("manuales"), correoDe("manuales"),
                "tinta-sobre-papel-viejo");
        long proceso = procesoPublicado();
        Path descargas = permitirDescargas("documentar");
        entrar(tienda);

        ir("/procesos/" + proceso);
        pulsable(Documentacion.DESDE_EL_DETALLE).click();
        esperarUrl("/procesos/" + proceso + "/documentacion");

        // La vista previa es el HTML que la API devuelve, no algo que la web convierta por su cuenta
        escribir(Documentacion.TEXTO, MARKDOWN);
        pulsable(Documentacion.VER_PREVIA).click();
        esperarTexto(Documentacion.PREVIA_TITULO, "Packing");
        assertEquals("twice", textoDe(Documentacion.PREVIA_NEGRITA));
        pulsable(Documentacion.GUARDAR).click();
        visible(Documentacion.AVISO);
        assertEquals(MARKDOWN, api.get("/procesos/" + proceso + "/documentacion").get(0).get("markdown").asText());

        pulsar(pulsable(Documentacion.pestana("ficha")));
        escribir(Documentacion.FICHA_OBJETIVO, "Every gift leaves wrapped and with its card");
        escribir(Documentacion.FICHA_RIESGOS, "Running out of paper");
        pulsar(pulsable(Documentacion.FICHA_GUARDAR));
        visible(Documentacion.FICHA_AVISO);
        JsonNode ficha = api.get("/procesos/" + proceso + "/caracterizacion");
        assertEquals("Every gift leaves wrapped and with its card", ficha.get("objetivo").asText());
        assertEquals("Running out of paper", ficha.get("riesgos").asText());

        // El boton abre el selector de archivos; lo que el selector le entrega a la entrada es esto
        pulsar(pulsable(Documentacion.pestana("adjuntos")));
        visible(Documentacion.ZONA);
        navegador.findElement(Documentacion.ARCHIVO).sendKeys(archivoDePrueba().toAbsolutePath().toString());
        WebElement documento = new WebDriverWait(navegador, ESPERA_DE_LA_COLA)
                .until(ExpectedConditions.visibilityOfElementLocated(Documentacion.DISPONIBLE));
        pulsar(documento.findElement(Documentacion.BAJAR));
        assertEquals(INSTRUCCIONES, Files.readString(esperarDescarga(descargas, ".txt"), StandardCharsets.UTF_8));

        pulsar(pulsable(Documentacion.pestana("manual")));
        pulsar(pulsable(Documentacion.GENERAR_MANUAL));
        WebElement manual = new WebDriverWait(navegador, ESPERA_DE_LA_COLA)
                .until(ExpectedConditions.visibilityOfElementLocated(Documentacion.MANUAL_LISTO));
        pulsar(manual.findElement(Documentacion.MANUAL_PDF));
        byte[] pdf = Files.readAllBytes(esperarDescarga(descargas, ".pdf"));
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("quien solo lee ve la documentacion y la ficha, pero no edita, no adjunta ni pide el manual")
    void quienSoloLeeMira() {
        api.registrarTienda("Tienda Lectora", nitDe("lectora"), correoDe("lectora"), "brisa-entre-pinos-altos");
        long proceso = procesoPublicado();
        api.put("/procesos/" + proceso + "/documentacion", """
                {"markdown":"Wrap the order **twice**."}
                """);
        api.put("/procesos/" + proceso + "/caracterizacion", """
                {"objetivo":"Every gift leaves wrapped"}
                """);
        api.crearUsuario("Luis Lector", correoDe("lector"), "nubes-sobre-el-valle", "SOLO_LECTURA");
        entrarCon(correoDe("lector"), "nubes-sobre-el-valle");

        ir("/procesos/" + proceso);
        pulsable(Documentacion.DESDE_EL_DETALLE).click();
        visible(Documentacion.SOLO_LECTURA);
        esperarTexto(Documentacion.GUARDADA, "twice");
        assertTrue(navegador.findElements(Documentacion.TEXTO).isEmpty(), "quien solo lee no tiene editor");

        pulsable(Documentacion.pestana("ficha")).click();
        esperarTexto(Documentacion.FICHA_LEIDA_OBJETIVO, "Every gift leaves wrapped");
        assertTrue(navegador.findElements(Documentacion.FICHA_GUARDAR).isEmpty(), "ni boton para guardar la ficha");

        pulsable(Documentacion.pestana("adjuntos")).click();
        visible(By.id("adjuntos-vacio"));
        assertTrue(navegador.findElements(Documentacion.ZONA).isEmpty(), "ni donde soltar un archivo");

        pulsable(Documentacion.pestana("manual")).click();
        visible(By.id("manual-vacio"));
        assertTrue(navegador.findElements(Documentacion.GENERAR_MANUAL).isEmpty(), "ni boton para pedir el manual");
    }

    @Test
    @DisplayName("las cuatro pestanas no tienen violaciones serias de accesibilidad, en claro y en oscuro")
    void accesibilidadDeLasPestanas() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Documentada", nitDe("documentada"),
                correoDe("documentada"), "mareas-de-la-bahia");
        long proceso = procesoPublicado();
        entrar(tienda);

        // Con algo en cada una: la vista previa abierta, un documento con sus versiones a la vista
        ir("/procesos/" + proceso + "/documentacion");
        escribir(Documentacion.TEXTO, MARKDOWN);
        pulsable(Documentacion.VER_PREVIA).click();
        esperarTexto(Documentacion.PREVIA_TITULO, "Packing");
        pulsable(Documentacion.pestana("adjuntos")).click();
        visible(Documentacion.ZONA);
        navegador.findElement(Documentacion.ARCHIVO).sendKeys(archivoDePrueba().toAbsolutePath().toString());
        new WebDriverWait(navegador, ESPERA_DE_LA_COLA)
                .until(ExpectedConditions.visibilityOfElementLocated(Documentacion.DISPONIBLE))
                .findElement(Documentacion.VERSIONES).click();
        visible(Documentacion.VERSION);

        revisarLasPestanas();
        pulsable(By.id("navbar-tema")).click();
        espera.until(pagina -> "dark".equals(pagina.findElement(By.tagName("html")).getDomAttribute("data-bs-theme")));
        revisarLasPestanas();
    }

    /** Un proceso con una tarea entre su inicio y su fin, publicado: sin version vigente no hay manual. */
    private long procesoPublicado() {
        long proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long lane = api.crearLane(api.poolDeLaTienda(proceso), "Warehouse", api.crearRol("Warehouse"));
        long inicio = api.crearEvento(lane, "Order received", "INICIO", 20, 80);
        long envolver = api.crearActividad(lane, "Wrap the order", "USUARIO", 180, 80);
        long fin = api.crearEvento(lane, "Order wrapped", "FIN", 340, 80);
        api.conectar(inicio, envolver);
        api.conectar(envolver, fin);
        api.publicar(proceso);
        return proceso;
    }

    /** Chrome sin pantalla no baja nada si no se le dice adonde: una carpeta vacia por prueba, dentro de target. */
    private Path permitirDescargas(String prueba) throws IOException {
        Path carpeta = Files.createDirectories(
                Path.of("target", "e2e", "descargas", prueba + "-" + System.currentTimeMillis())).toAbsolutePath();
        ((ChromeDriver) navegador).executeCdpCommand("Browser.setDownloadBehavior",
                Map.of("behavior", "allow", "downloadPath", carpeta.toString()));
        return carpeta;
    }

    /** Un texto plano, que el antivirus de la API acepta sin mas. */
    private static Path archivoDePrueba() {
        try {
            Path carpeta = Files.createDirectories(Path.of("target", "e2e", "archivos"));
            return Files.writeString(carpeta.resolve("instrucciones-de-empaque.txt"), INSTRUCCIONES,
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
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

    /**
     * Pulsa algo que puede haber quedado fuera de la ventana, por debajo o por encima: lo trae al centro al instante.
     * Selenium tambien desplaza la pagina, pero Bootstrap pide un desplazamiento suave y el clic llegaria antes que
     * ella.
     */
    private void pulsar(WebElement boton) {
        ((JavascriptExecutor) navegador).executeScript(
                "arguments[0].scrollIntoView({block: 'center', behavior: 'instant'})", boton);
        boton.click();
    }

    private void revisarLasPestanas() {
        for (String pestana : PESTANAS) {
            pulsable(Documentacion.pestana(pestana)).click();
            visible(By.id("panel-" + pestana));
            quieta();
            Results resultado = new AxeBuilder().withTags(REGLAS).analyze(navegador);
            List<String> graves = resultado.getViolations().stream()
                    .filter(violacion -> GRAVES.contains(violacion.getImpact()))
                    .map(DocumentacionYAdjuntosTest::describir)
                    .toList();
            assertEquals(List.of(), graves, "Violaciones serias de accesibilidad en la pestana " + pestana
                    + " con el tema " + navegador.findElement(By.tagName("html")).getDomAttribute("data-bs-theme"));
        }
    }

    private static String describir(Rule violacion) {
        return violacion.getId() + " (" + violacion.getImpact() + "): " + violacion.getHelp() + " en "
                + violacion.getNodes().stream().limit(3).map(nodo -> String.valueOf(nodo.getTarget())).toList();
    }
}
