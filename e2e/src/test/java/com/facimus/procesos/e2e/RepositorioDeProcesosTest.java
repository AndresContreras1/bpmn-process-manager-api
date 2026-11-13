package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** Archivar un proceso en una carpeta y encontrarlo por lo que se le puso. */
class RepositorioDeProcesosTest extends PruebaE2E {

    private static final By FILAS = By.cssSelector("#procesos tbody tr");

    @Test
    @DisplayName("se crea una carpeta, se archiva un proceso y la lista lo filtra por carpeta y etiqueta")
    void archivarYFiltrar() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Repositorio", nitDe("repositorio"),
                correoDe("repositorio"), "puentes-de-piedra");
        long archivado = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        api.crearProceso("Returns", "Take an order back.", "Fulfillment");
        entrar(tienda);

        ir("/procesos");
        visible(By.id("carpetas-nombre")).sendKeys("Operations");
        pulsable(By.id("carpetas-crear")).click();
        By carpeta = By.cssSelector("[data-carpeta='Operations']");
        visible(carpeta);

        ir("/procesos/" + archivado);
        WebElement desplegable = visible(By.id("ficha-carpeta"));
        espera.until(navegador -> new Select(desplegable).getOptions().size() == 2);
        new Select(desplegable).selectByVisibleText("Operations");
        new Select(visible(By.id("ficha-clasificacion"))).selectByValue("CONFIDENCIAL");
        // La ficha queda bajo el borde de la ventana: se manda con Enter, como haria una persona
        visible(By.id("ficha-etiquetas")).sendKeys("Logistics, gdpr", Keys.ENTER);
        visible(By.id("ficha-aviso"));
        assertEquals("CONFIDENCIAL", api.get("/procesos/" + archivado).get("proceso").get("clasificacion").asText());

        ir("/procesos");
        espera.until(ExpectedConditions.numberOfElementsToBe(FILAS, 2));
        pulsable(carpeta).click();
        espera.until(ExpectedConditions.numberOfElementsToBe(FILAS, 1));
        esperarTexto(FILAS, "Gift wrapping");

        pulsable(By.id("carpeta-todas")).click();
        espera.until(ExpectedConditions.numberOfElementsToBe(FILAS, 2));
        visible(By.id("procesos-etiqueta")).sendKeys("logistics");
        espera.until(ExpectedConditions.numberOfElementsToBe(FILAS, 1));
        List<WebElement> filas = navegador.findElements(FILAS);
        assertEquals(1, filas.size());
    }

    @Test
    @DisplayName("una carpeta con procesos no se puede borrar, y se dice por que")
    void noSeBorraConProcesos() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Carpeta", nitDe("carpeta"), correoDe("carpeta"),
                "puentes-de-piedra");
        long proceso = api.crearProceso("Gift wrapping", "Wrap an order as a gift.", "Fulfillment");
        long carpeta = api.post("/carpetas", "{\"nombre\":\"Operations\"}").get("id").asLong();
        long version = api.get("/procesos/" + proceso).get("proceso").get("version").asLong();
        api.put("/procesos/" + proceso + "/ficha", "{\"carpetaId\":" + carpeta
                + ",\"clasificacion\":\"INTERNA\",\"etiquetas\":[],\"version\":" + version + "}");
        entrar(tienda);

        ir("/procesos");
        pulsable(By.cssSelector("[data-carpeta='Operations']")).click();
        pulsable(By.id("carpetas-borrar")).click();

        visible(By.id("carpetas-error"));
    }
}
