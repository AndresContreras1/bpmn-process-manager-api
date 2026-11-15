package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;

/**
 * F19: el repositorio y los permisos desde la web. Las carpetas se mueven, un proceso cambia de carpeta sin arrastrar,
 * los filtros viven en la direccion, Ctrl+K busca en la organizacion entera, una concesion de ver deja ver a una
 * persona y a nadie mas, y quien solo lee no ve botones de cambio. Lo nuevo pasa por axe en los dos temas.
 */
class RepositorioYPermisosTest extends PruebaE2E {

    private static final By FILAS = By.cssSelector("#procesos tbody tr");
    private static final By RESULTADO = By.cssSelector("[data-testid='busqueda-resultado']");
    private static final List<String> REGLAS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    private static final List<String> GRAVES = List.of("serious", "critical");

    @Test
    @DisplayName("una carpeta se mueve, un proceso cambia de carpeta con el teclado y los filtros sobreviven")
    void moverYFiltrarPorLaDireccion() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Arbol", nitDe("f19-arbol"),
                correoDe("f19-arbol"),
                "ramas-del-roble-viejo");
        long operaciones = api.post("/carpetas", "{\"nombre\":\"Operations\"}").get("id").asLong();
        long almacen = api.post("/carpetas", "{\"nombre\":\"Warehouse\"}").get("id").asLong();
        long regalo = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        api.crearProceso("Returns", "Take an order back.", "Fulfillment");
        entrar(tienda);

        // La carpeta Warehouse pasa a estar dentro de Operations, desde el arbol
        ir("/procesos");
        pulsable(By.cssSelector("[data-carpeta='Warehouse']")).click();
        esperarUrl("carpeta=" + almacen);
        elegir(By.id("carpetas-destino"), "Operations");
        pulsable(By.id("carpetas-mover")).click();
        espera.until(navegador -> api.get("/carpetas").findValues("padreId").stream()
                .anyMatch(padre -> padre.asLong() == operaciones));

        // Gift wrapping va a Warehouse sin raton: el dialogo abre en su carpeta, las flechas eligen y Enter mueve
        pulsable(By.id("carpeta-todas")).click();
        esperarFilas(FILAS, 2);
        WebElement fila = navegador.findElements(FILAS).stream()
                .filter(una -> una.getText().contains("Gift wrapping")).findFirst().orElseThrow();
        fila.findElement(By.cssSelector("[data-testid='proceso-mover']")).click();
        WebElement ninguna = visible(By.id("mover-ninguna"));
        espera.until(navegador -> ninguna.equals(navegador.switchTo().activeElement()));
        // Las opciones van en el orden del arbol: sin carpeta, Operations y, dentro, Warehouse
        ninguna.sendKeys(Keys.ARROW_DOWN);
        navegador.switchTo().activeElement().sendKeys(Keys.ARROW_DOWN);
        assertTrue(visible(By.id("mover-carpeta-" + almacen)).isSelected(), "las flechas eligen la carpeta");
        revisarAccesibilidad("el dialogo de mover");
        navegador.switchTo().activeElement().sendKeys(Keys.ENTER);
        visible(By.id("procesos-aviso"));
        assertEquals(almacen, api.get("/procesos/" + regalo).get("proceso").get("carpetaId").asLong());
        espera.until(ExpectedConditions.invisibilityOfElementLocated(By.id("mover-dialogo")));

        // Elegir Operations filtra con lo de dentro, y el filtro queda en la direccion con la clasificacion
        pulsable(By.cssSelector("[data-carpeta='Operations']")).click();
        esperarFilas(FILAS, 1);
        elegir(By.id("procesos-clasificacion"), "Interna");
        esperarUrl("clasificacion=INTERNA");
        esperarUrl("carpeta=" + operaciones);
        navegador.navigate().refresh();
        esperarFilas(FILAS, 1);
        esperarTexto(FILAS, "Gift wrapping");
        assertEquals("INTERNA", new Select(visible(By.id("procesos-clasificacion"))).getFirstSelectedOption()
                .getDomAttribute("value"));
        assertEquals("true", visible(By.cssSelector("[data-carpeta='Operations']")).getDomAttribute("aria-current"));

        // Un enlace con un filtro abre la lista ya filtrada
        ir("/procesos?nombre=Returns");
        esperarFilas(FILAS, 1);
        esperarTexto(FILAS, "Returns");
    }

    @Test
    @DisplayName("Ctrl+K busca, las flechas recorren, Enter abre el proceso y Escape devuelve el foco")
    void buscarConCtrlK() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Buscar", nitDe("f19-buscar"),
                correoDe("f19-buscar"),
                "linternas-del-puerto");
        long credito = api.crearProceso("Credit approval", "Approve a credit for a customer & a store.", "Sales");
        api.crearProceso("Returns", "Take an order back.", "Fulfillment");
        entrar(tienda);

        ir("/procesos");
        esperarFilas(FILAS, 2);
        new Actions(navegador).keyDown(Keys.CONTROL).sendKeys("k").keyUp(Keys.CONTROL).perform();
        WebElement campo = visible(By.id("busqueda-campo"));
        espera.until(navegador -> campo.equals(navegador.switchTo().activeElement()));
        campo.sendKeys("credit");
        esperarTexto(RESULTADO, "Credit approval");
        assertEquals("true", visible(RESULTADO).getDomAttribute("aria-selected"));
        espera.until(navegador -> String.valueOf(navegador.findElement(By.id("busqueda-estado"))
                .getDomProperty("textContent")).contains("1"));
        revisarAccesibilidad("la busqueda global");
        // La flecha abajo con un solo resultado da la vuelta y lo deja activo; Enter lo abre
        campo.sendKeys(Keys.ARROW_DOWN, Keys.ENTER);
        esperarUrl("/procesos/" + credito);
        esperarTexto(Paginas.Detalle.NOMBRE, "Credit approval");

        // Desde el boton del encabezado: Escape cierra y el foco vuelve al boton
        WebElement boton = pulsable(By.id("navbar-buscar"));
        boton.click();
        visible(By.id("busqueda-campo")).sendKeys("returns", Keys.ESCAPE);
        espera.until(ExpectedConditions.invisibilityOfElementLocated(By.id("busqueda-dialogo")));
        espera.until(navegador -> boton.equals(navegador.switchTo().activeElement()));
    }

    @Test
    @DisplayName("ver concedido a una persona deja que ella lo vea y nadie mas; los botones siguen a las concesiones")
    void concederVerYSoloLectura() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Permisos", nitDe("f19-permisos"),
                correoDe("f19-permisos"),
                "faros-de-la-costa-norte");
        long proceso = api.crearProceso("Payroll", "Pay the people of the store.", "People");
        long abierto = api.crearProceso("Opening hours", "When the store opens.", "Store");
        long inventario = api.crearProceso("Inventory count", "Count what is on the shelves.", "Store");
        long carpeta = api.post("/carpetas", "{\"nombre\":\"Human resources\"}").get("id").asLong();
        String correoAna = correoDe("f19-ana");
        String correoBeto = correoDe("f19-beto");
        String correoLuz = correoDe("f19-luz");
        long idDeAna = api.crearUsuario("Ana Editora", correoAna, "montanas-de-niebla-azul", "EDITOR");
        long idDeBeto = api.crearUsuario("Beto Editor", correoBeto, "rios-de-agua-mansa", "EDITOR");
        api.crearUsuario("Luz Lectora", correoLuz, "bosques-de-pino-alto", "SOLO_LECTURA");
        // Modelar Inventory count queda solo para Beto: a Ana, editora, se lo quita aunque su rol se lo daria
        api.post("/procesos/" + inventario + "/concesiones",
                "{\"accion\":\"MODELAR\",\"sujeto\":\"PERSONA\",\"usuarioId\":" + idDeBeto + "}");
        entrar(tienda);

        // El administrador le concede ver Payroll a Ana desde la pantalla de permisos del proceso
        ir("/procesos/" + proceso);
        pulsable(By.id("proceso-detalle-permisos")).click();
        esperarUrl("/procesos/" + proceso + "/permisos");
        WebElement persona = visible(By.id("permisos-usuario"));
        espera.until(navegador -> new Select(persona).getOptions().size() > 3);
        new Select(persona).getOptions().stream().filter(opcion -> opcion.getText().contains("Ana Editora"))
                .findFirst().orElseThrow().click();
        pulsable(By.id("permisos-conceder")).click();
        visible(By.id("permisos-aviso"));
        esperarTexto(By.cssSelector("[data-testid='concesion-sujeto']"), "Ana Editora");
        revisarAccesibilidad("los permisos de un proceso");

        // Una concesion en una carpeta se ve heredada desde el proceso que esta dentro
        long version = api.get("/procesos/" + abierto).get("proceso").get("version").asLong();
        api.put("/procesos/" + abierto + "/ficha", "{\"carpetaId\":" + carpeta
                + ",\"clasificacion\":\"INTERNA\",\"etiquetas\":[],\"version\":" + version + "}");
        api.post("/carpetas/" + carpeta + "/concesiones", "{\"accion\":\"COMENTAR\",\"sujeto\":\"ORGANIZACION\"}");
        ir("/procesos/" + abierto + "/permisos");
        visible(By.cssSelector("[data-testid='concesion-heredada']"));
        visible(By.cssSelector("[data-carpeta='Human resources']"));
        ir("/carpetas/" + carpeta + "/permisos");
        esperarTexto(By.cssSelector("[data-testid='concesion-accion']"), "Comentar");

        // Beto, otro editor, ya no lo ve; Ana si
        ApiDeDatos beto = new ApiDeDatos(API);
        beto.entrar(correoBeto, "rios-de-agua-mansa");
        assertTrue(beto.get("/procesos").findValues("nombre").stream().noneMatch(n -> "Payroll".equals(n.asText())),
                "quien no tiene la concesion no ve el proceso");
        salir();
        entrarCon(correoAna, "montanas-de-niebla-azul");
        espera.until(navegador -> navegador.findElements(FILAS).stream()
                .anyMatch(una -> una.getText().contains("Payroll")));

        // En la lista, cada fila sigue lo que la API dice de su proceso: Inventory count no le deja mover ni editar
        espera.until(navegador -> botonesDeLaFila("Inventory count", "proceso-mover").isEmpty());
        assertTrue(botonesDeLaFila("Inventory count", "proceso-editar").isEmpty(), "ni editar desde la lista");
        assertEquals(1, botonesDeLaFila("Inventory count", "proceso-publicar").size(), "publicar si: nadie lo quito");
        assertEquals(1, botonesDeLaFila("Payroll", "proceso-mover").size(), "Payroll lo modela como cualquier editor");

        // Y el detalle tambien: el rol le daria editar, modelar y mover, y la concesion de Beto se los quita
        ir("/procesos/" + inventario);
        esperarTexto(Paginas.Detalle.NOMBRE, "Inventory count");
        visible(By.id("proceso-detalle-publicar"));
        for (String boton : List.of("editar", "modelar", "mover", "permisos")) {
            assertTrue(navegador.findElements(By.id("proceso-detalle-" + boton)).isEmpty(),
                    "sin modelar Inventory count no tiene el boton " + boton);
        }

        // Administrar Opening hours le abre sus permisos, que su rol no le daria; los de la carpeta no los lee
        api.post("/procesos/" + abierto + "/concesiones",
                "{\"accion\":\"ADMINISTRAR\",\"sujeto\":\"PERSONA\",\"usuarioId\":" + idDeAna + "}");
        ir("/procesos/" + abierto);
        pulsable(By.id("proceso-detalle-permisos")).click();
        esperarUrl("/procesos/" + abierto + "/permisos");
        visible(By.cssSelector("[data-testid='heredada-sin-leer']"));
        visible(By.cssSelector("[data-carpeta='Human resources']"));

        // Luz solo lee: el detalle no le ofrece editar, modelar, publicar, mover, permisos ni borrar
        salir();
        entrarCon(correoLuz, "bosques-de-pino-alto");
        ir("/procesos/" + abierto);
        esperarTexto(Paginas.Detalle.NOMBRE, "Opening hours");
        visible(By.id("proceso-detalle-exportar"));
        for (String boton : List.of("editar", "modelar", "publicar", "mover", "permisos", "compartir", "eliminar")) {
            assertTrue(navegador.findElements(By.id("proceso-detalle-" + boton)).isEmpty(),
                    "quien solo lee no tiene el boton " + boton);
        }
        // Payroll no esta: la concesion de ver es solo de Ana
        ir("/procesos");
        esperarFilas(FILAS, 2);
        assertTrue(navegador.findElements(FILAS).stream().noneMatch(una -> una.getText().contains("Payroll")),
                "Payroll es solo de Ana");
        assertTrue(navegador.findElements(By.cssSelector("[data-testid='proceso-mover']")).isEmpty(),
                "ni mover desde la lista");
    }

    /** Los botones con ese data-testid en la fila del proceso con ese nombre, buscada de nuevo cada vez. */
    private List<WebElement> botonesDeLaFila(String nombre, String boton) {
        return navegador.findElements(FILAS).stream().filter(una -> una.getText().contains(nombre)).findFirst()
                .map(fila -> fila.findElements(By.cssSelector("[data-testid='" + boton + "']"))).orElse(List.of());
    }

    /** axe sobre lo que hay en pantalla, en el tema de ahora y en el otro, y vuelta al de antes. */
    private void revisarAccesibilidad(String donde) {
        for (int vuelta = 0; vuelta < 2; vuelta++) {
            quieta();
            List<String> graves = new AxeBuilder().withTags(REGLAS).analyze(navegador).getViolations().stream()
                    .filter(violacion -> GRAVES.contains(violacion.getImpact()))
                    .map(RepositorioYPermisosTest::describir).toList();
            String tema = navegador.findElement(By.tagName("html")).getDomAttribute("data-bs-theme");
            assertEquals(List.of(), graves, "Violaciones serias de accesibilidad en " + donde + " con el tema " + tema);
            cambiarDeTema(tema);
        }
    }

    /**
     * El tema cambia sin cerrar lo que este abierto: el boton del encabezado quedaria detras del dialogo, asi que se
     * cambia como lo hace el servicio del tema, en el atributo que lee Bootstrap.
     */
    private void cambiarDeTema(String actual) {
        String otro = "dark".equals(actual) ? "light" : "dark";
        ((JavascriptExecutor) navegador)
                .executeScript("document.documentElement.setAttribute('data-bs-theme', arguments[0])", otro);
        espera.until(navegador -> otro.equals(navegador.findElement(By.tagName("html"))
                .getDomAttribute("data-bs-theme")));
    }

    private static String describir(Rule violacion) {
        return violacion.getId() + " (" + violacion.getImpact() + "): " + violacion.getHelp() + " en "
                + violacion.getNodes().stream().limit(3).map(nodo -> String.valueOf(nodo.getTarget())).toList();
    }
}
