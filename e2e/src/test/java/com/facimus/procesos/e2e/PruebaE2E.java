package com.facimus.procesos.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Lo que comparten todas las pruebas de punta a punta: el navegador, la espera y la captura de lo que se vio al
 * fallar.
 *
 * Nunca hay un Thread.sleep. Una espera fija o sobra o se queda corta segun el dia; lo que se espera aqui es una
 * condicion concreta de la pagina, y si no se cumple en diez segundos la prueba falla diciendo que esperaba.
 */
abstract class PruebaE2E {

    /** Donde esta la web y donde la API. En el compose, las dos detras del mismo puerto. */
    protected static final String BASE = ajuste("e2e.base.url", "E2E_BASE_URL", "http://localhost");
    protected static final String API = ajuste("e2e.api.url", "E2E_API_URL", BASE + "/api/v1");

    private static final Duration ESPERA = Duration.ofSeconds(10);
    private static final Path CAPTURAS = Path.of("target", "e2e");

    protected WebDriver navegador;
    protected WebDriverWait espera;
    protected ApiDeDatos api;

    @BeforeEach
    void abrirNavegador() {
        ChromeOptions opciones = new ChromeOptions();
        opciones.addArguments("--headless=new", "--window-size=1440,900", "--no-sandbox",
                "--disable-dev-shm-usage", "--disable-gpu");
        navegador = new ChromeDriver(opciones);
        espera = new WebDriverWait(navegador, ESPERA);
        api = new ApiDeDatos(API);
    }

    /**
     * Al fallar, una captura de lo que habia en pantalla. Un fallo de punta a punta sin la pantalla es una
     * adivinanza: el mensaje dice que no aparecio algo, y la imagen dice que aparecio en su lugar.
     */
    @AfterEach
    void cerrarNavegador(TestInfo prueba) throws IOException {
        if (navegador == null) {
            return;
        }
        try {
            Files.createDirectories(CAPTURAS);
            byte[] imagen = ((TakesScreenshot) navegador).getScreenshotAs(OutputType.BYTES);
            Files.write(CAPTURAS.resolve(prueba.getDisplayName().replaceAll("\\W+", "-") + ".png"), imagen);
        } finally {
            navegador.quit();
        }
    }

    protected void ir(String ruta) {
        navegador.get(BASE + ruta);
    }

    /** El elemento cuando ya se puede pulsar, no cuando existe: Angular lo pinta antes de tenerlo listo. */
    protected WebElement pulsable(By donde) {
        return espera.until(ExpectedConditions.elementToBeClickable(donde));
    }

    /**
     * Manda un formulario desde el teclado, con Enter sobre uno de sus campos.
     *
     * Hace falta cuando el boton queda mas abajo del borde de la ventana: el navegador sin pantalla no desplaza la
     * pagina por su cuenta y el clic se pierde. Una persona pulsa Enter igual, asi que la prueba tampoco miente.
     */
    protected void enviarConEnter(By campo) {
        visible(campo).sendKeys(Keys.ENTER);
    }

    protected WebElement visible(By donde) {
        return espera.until(ExpectedConditions.visibilityOfElementLocated(donde));
    }

    protected void escribir(By donde, String texto) {
        WebElement campo = visible(donde);
        campo.clear();
        campo.sendKeys(texto);
    }

    /** Entra con el administrador de una tienda recien registrada. */
    protected void entrar(ApiDeDatos.Tienda tienda) {
        entrarCon(tienda.correo(), tienda.clave());
    }

    /** Entra con cualquiera: hace falta para probar lo que ve quien no es administrador. */
    protected void entrarCon(String correo, String clave) {
        ir("/login");
        escribir(Paginas.Login.EMAIL, correo);
        escribir(Paginas.Login.CLAVE, clave);
        pulsable(Paginas.Login.ENTRAR).click();
        esperarUrl("/procesos");
    }

    /** Sale sin pasar por el menu: la sesion vive en localStorage y borrarla es salir. */
    protected void salir() {
        ((JavascriptExecutor) navegador).executeScript("localStorage.clear()");
    }

    /** Elige una opcion de un desplegable por el texto que se lee, que es lo unico estable de un option. */
    protected void elegir(By donde, String texto) {
        new Select(visible(donde)).selectByVisibleText(texto);
    }

    protected String textoDe(By donde) {
        return visible(donde).getText();
    }

    /**
     * Espera a que queden exactamente tantas filas. Contar sin esperar no sirve en un listado que filtra: el
     * numero de ahora es el de antes de que el filtro conteste, y la espera por texto vuelve enseguida si el texto
     * ya estaba en la primera fila.
     */
    protected void esperarFilas(By donde, int cuantas) {
        espera.until(ExpectedConditions.numberOfElementsToBe(donde, cuantas));
    }

    protected void esperarTexto(By donde, String texto) {
        espera.until(ExpectedConditions.textToBePresentInElementLocated(donde, texto));
    }

    protected void esperarUrl(String fragmento) {
        espera.until(ExpectedConditions.urlContains(fragmento));
    }

    /**
     * De donde sale cada direccion: una propiedad si se paso con -D, si no la variable de entorno, que la hereda
     * el proceso que arranca surefire, y si no lo de siempre, que es el compose en el puerto 80.
     */
    private static String ajuste(String propiedad, String variable, String porDefecto) {
        String valor = System.getProperty(propiedad, System.getenv(variable));
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }

    /**
     * Un identificador distinto en cada corrida y en cada clase.
     *
     * Por clase, porque dos escenarios a la vez no pueden pelearse por el mismo NIT. Y por corrida, porque una
     * tienda registrada no se borra: con identificadores fijos, la segunda vez que alguien corriera la suite
     * contra la misma base fallaria entera con un 409, que es lo que paso la primera vez que se corrio aqui.
     */
    private static final String CORRIDA = String.format("%08d", System.currentTimeMillis() % 100_000_000L);

    protected static String nitDe(String semilla) {
        return "9" + CORRIDA + "-" + (Math.abs(semilla.hashCode()) % 90 + 10);
    }

    protected static String correoDe(String semilla) {
        return semilla + "-" + CORRIDA + "@e2e.test";
    }
}
