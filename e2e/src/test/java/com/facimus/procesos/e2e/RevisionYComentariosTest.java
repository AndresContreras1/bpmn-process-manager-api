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
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * F21: la revision, los comentarios y la campana desde la web. Con la politica de aprobacion, el proceso no se publica
 * directo: se pide la revision, el aprobador la encuentra en su bandeja y en su campana, ve la diferencia y aprueba, y
 * eso publica. Un comentario que menciona a alguien llega a su campana en vivo, sin recargar, y su aviso lleva al hilo.
 * Las pantallas nuevas pasan por axe en los dos temas.
 */
class RevisionYComentariosTest extends PruebaE2E {

    private static final By CONTEO = By.id("campana-conteo");
    private static final By AVISO = By.cssSelector("[data-testid='campana-aviso']");
    private static final By HILO = By.cssSelector("[data-testid='hilo']");
    private static final List<String> REGLAS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa");
    private static final List<String> GRAVES = List.of("serious", "critical");

    @Test
    @DisplayName("con aprobacion se pide la revision, el aprobador ve la diferencia, aprueba y eso publica")
    void pedirRevisionYAprobar() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Revision", nitDe("f21-revision"),
                correoDe("f21-revision"), "sellos-del-notario");
        long beto = api.crearUsuario("Beto Aprobador", correoDe("f21-beto"), "sello-azul-del-martes", "EDITOR");
        api.put("/empresas/actual/politica-de-publicacion", """
                {"modo":"CON_APROBACION","aprobacionesNecesarias":1,"aprobadores":[%d]}
                """.formatted(beto));
        long proceso = procesoPublicable("Order fulfillment");
        entrar(tienda);

        // La politica se ve en su pantalla, y pasa por axe
        ir("/politica-de-publicacion");
        assertTrue(visible(By.id("politica-con-aprobacion")).isSelected(), "la politica guardada es con aprobacion");
        assertTrue(visible(By.cssSelector("[data-persona='Beto Aprobador']")).isSelected(), "Beto aprueba");
        revisarAccesibilidad("la politica de publicacion");

        // En el proceso no hay Publicar: se pide la revision, con su texto
        ir("/procesos/" + proceso);
        pulsar(By.id("proceso-detalle-pedir-revision"));
        escribir(By.id("pedir-revision-texto"), "Adds the **packing** step.");
        revisarAccesibilidad("el dialogo de pedir la revision");
        pulsable(By.id("pedir-revision-enviar")).click();
        esperarUrl("/solicitudes/");
        assertEquals("ABIERTA", visible(By.id("solicitud-estado")).getDomAttribute("data-estado"));
        esperarTexto(By.id("solicitud-html"), "packing");
        // Contra un diagrama vacio, cada elemento es nuevo: en la lista y pintado en el dibujo
        espera.until(navegador -> !navegador.findElements(By.cssSelector("[data-testid='comparacion-cambio']"
                + "[data-tipo='agregado']")).isEmpty());
        visible(By.cssSelector("#solicitud-diagrama .djs-element.marca-agregado"));
        assertTrue(navegador.findElements(By.id("solicitud-aprobar")).isEmpty(), "quien la pide no la aprueba");
        revisarAccesibilidad("la solicitud");

        // Beto la encuentra en su campana y en su bandeja
        salir();
        entrarCon(correoDe("f21-beto"), "sello-azul-del-martes");
        esperarTexto(CONTEO, "1");
        pulsable(By.id("navbar-campana")).click();
        esperarTexto(AVISO, "Order fulfillment");
        assertEquals("APROBACION_PEDIDA", visible(AVISO).getDomAttribute("data-tipo"));
        revisarAccesibilidad("la campana abierta");
        pulsable(By.id("navbar-campana")).click();
        ir("/solicitudes");
        esperarTexto(By.cssSelector("[data-testid='solicitud-fila']"), "Order fulfillment");
        revisarAccesibilidad("la bandeja por aprobar");
        pulsable(By.cssSelector("[data-testid='solicitud-abrir']")).click();

        // Aprueba sin texto, y la ultima aprobacion publica
        pulsar(By.id("solicitud-aprobar"));
        pulsar(By.id("solicitud-confirmar"));
        esperarTexto(By.id("solicitud-aviso"), "1");
        assertEquals("PUBLICADA", visible(By.id("solicitud-estado")).getDomAttribute("data-estado"));
        JsonNode publicado = api.get("/procesos/" + proceso).get("proceso");
        assertEquals("PUBLICADO", publicado.get("estado").asText());
        assertEquals(1, publicado.get("versionPublicada").asInt());
    }

    @Test
    @DisplayName("un comentario con una mencion llega en vivo a la campana de la mencionada y su aviso lleva al hilo")
    void comentarConMencion() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Comentarios", nitDe("f21-comentarios"),
                correoDe("f21-comentarios"), "notas-al-margen-largas");
        api.crearUsuario("Carla Lectora", correoDe("f21-carla"), "lupa-roja-del-jueves", "SOLO_LECTURA");
        long proceso = procesoPublicable("Returns");
        String tarea = api.get("/procesos/" + proceso + "/diagrama").get("actividades").get(0).get("bpmnId")
                .asText();

        // Carla mira sus avisos mientras tanto, en otro navegador de la misma prueba: aqui, su sesion
        entrarCon(correoDe("f21-carla"), "lupa-roja-del-jueves");
        // La campana llega cuando el navegador queda libre, y abre su flujo: se espera a que este abierto
        espera.until(navegador -> "true".equals(visible(By.id("navbar-campana")).getDomAttribute("data-conectada")));
        assertTrue(navegador.findElements(CONTEO).isEmpty(), "Carla empieza sin avisos");

        // El administrador comenta en la tarea por la API y la menciona: la campana de Carla cuenta uno sin recargar
        long carla = api.get("/procesos/" + proceso + "/mencionables?q=Carla").get(0).get("id").asLong();
        api.post("/procesos/" + proceso + "/comentarios", """
                {"markdown":"Who checks the **label**?","bpmnId":"%s","menciones":[%d]}
                """.formatted(tarea, carla));
        esperarTexto(CONTEO, "1");
        esperarContenido(By.id("campana-anuncio"), "Returns");

        // Su aviso lleva al proceso, con la tarea elegida y el hilo resaltado; leerlo lo baja de la cuenta
        pulsable(By.id("navbar-campana")).click();
        pulsable(AVISO).findElement(By.tagName("a")).click();
        esperarUrl("comentario=");
        visible(By.cssSelector("[data-testid='hilo'].hilo-destacado"));
        esperarTexto(By.id("comentarios-titulo"), "Pack the order");
        espera.until(ExpectedConditions.invisibilityOfElementLocated(CONTEO));
        visible(By.cssSelector("[data-testid='insignia-comentarios'][data-elemento='" + tarea + "']"));

        // Quien solo lee tambien comenta: contesta en el hilo y menciona a quien empezo, eligiendolo con el teclado
        pulsar(By.cssSelector("[data-testid='hilo-responder']"));
        escribir(By.cssSelector("[data-testid='respuesta-texto']"), "The packer, every morning.");
        WebElement mencion = visible(By.cssSelector("[data-testid='hilo'] [data-testid='menciones-campo']"));
        mencion.sendKeys("Admin");
        esperarTexto(By.cssSelector("[data-testid='mencion-opcion']"), "Tienda Comentarios Admin");
        mencion.sendKeys(Keys.ENTER);
        esperarTexto(By.cssSelector("[data-testid='menciones-elegidas']"), "Tienda Comentarios Admin");
        revisarAccesibilidad("el panel de comentarios");
        pulsar(By.cssSelector("[data-testid='respuesta-enviar']"));
        esperarTexto(By.cssSelector("[data-testid='respuesta']"), "every morning");
        esperarContenido(By.id("comentarios-estado"), "Respuesta agregada");
        JsonNode hilo = api.get("/procesos/" + proceso + "/comentarios").get("content").get(0);
        assertEquals("Tienda Comentarios Admin",
                hilo.get("respuestas").get(0).get("menciones").get(0).get("nombre").asText());

        // Y el administrador lo resuelve desde la web; el hilo queda resuelto y el lienzo deja de contarlo
        salir();
        entrar(tienda);
        // La mencion y la respuesta de Carla
        visible(CONTEO);
        // La insignia sobre la tarea es un boton: la elige, y el panel muestra sus hilos
        ir("/procesos/" + proceso);
        pulsar(By.cssSelector("[data-testid='insignia-comentarios'][data-elemento='" + tarea + "']"));
        esperarTexto(By.id("comentarios-titulo"), "Pack the order");
        esperarTexto(HILO, "every morning");
        pulsar(By.cssSelector("[data-testid='hilo-resolver']"));
        espera.until(navegador -> "RESUELTO".equals(navegador.findElement(HILO).getDomAttribute("data-estado")));
        espera.until(ExpectedConditions.numberOfElementsToBe(
                By.cssSelector("[data-testid='insignia-comentarios']"), 0));

        // Las preferencias de los avisos: un tipo deja de llegar por correo
        ir("/cuenta/notificaciones");
        pulsar(By.id("envio-HILO_RESUELTO-NUNCA"));
        revisarAccesibilidad("las preferencias de los avisos");
        pulsar(By.id("preferencias-avisos-guardar"));
        visible(By.id("preferencias-avisos-aviso"));
        boolean guardada = false;
        for (JsonNode una : api.get("/notificaciones/preferencias")) {
            guardada |= "HILO_RESUELTO".equals(una.get("tipo").asText()) && "NUNCA".equals(una.get("envio").asText());
        }
        assertTrue(guardada, "la preferencia quedo guardada");
    }

    /**
     * Un clic en algo que puede quedar mas abajo del borde de la ventana: el panel de una solicitud o de los comentarios
     * es largo, y el navegador sin pantalla no desplaza la pagina por su cuenta. Se lleva al centro sin animar, porque
     * el desplazamiento suave de Bootstrap terminaria despues del clic, y se pulsa.
     */
    private void pulsar(By donde) {
        WebElement boton = pulsable(donde);
        ((JavascriptExecutor) navegador)
                .executeScript("arguments[0].scrollIntoView({block: 'center', behavior: 'instant'})", boton);
        pulsable(donde).click();
    }

    /** Lo que dice una region que solo oye un lector de pantalla: getText no lee lo que no se ve. */
    private void esperarContenido(By donde, String texto) {
        espera.until(navegador -> String.valueOf(navegador.findElement(donde).getDomProperty("textContent"))
                .contains(texto));
    }

    /** Un proceso de la tienda con inicio, una tarea y fin, en una lane con su rol: el diagnostico lo deja publicar. */
    private long procesoPublicable(String nombre) {
        long proceso = api.crearProceso(nombre, "From the order to its delivery.", "Fulfillment");
        long lane = api.crearLane(api.poolDeLaTienda(proceso), "Warehouse", api.crearRol("Warehouse " + nombre));
        long inicio = api.crearEvento(lane, "Order received", "INICIO", 20, 80);
        long tarea = api.crearActividad(lane, "Pack the order", "USUARIO", 180, 80);
        long fin = api.crearEvento(lane, "Order shipped", "FIN", 340, 80);
        api.conectar(inicio, tarea);
        api.conectar(tarea, fin);
        return proceso;
    }

    private void revisarAccesibilidad(String donde) {
        for (int vuelta = 0; vuelta < 2; vuelta++) {
            quieta();
            List<String> graves = new AxeBuilder().withTags(REGLAS).analyze(navegador).getViolations().stream()
                    .filter(violacion -> GRAVES.contains(violacion.getImpact()))
                    .map(RevisionYComentariosTest::describir).toList();
            String tema = navegador.findElement(By.tagName("html")).getDomAttribute("data-bs-theme");
            assertEquals(List.of(), graves, "Violaciones serias de accesibilidad en " + donde + " con el tema " + tema);
            cambiarDeTema(tema);
        }
    }

    /** Como en RepositorioYPermisosTest: el tema cambia sin cerrar lo que este abierto. */
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
