package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

/**
 * Un pedido de punta a punta desde la web: se abre, se ve donde esta parado, alguien lo atiende y el tablero lo
 * cuenta. Es el escenario que antes de estas pantallas solo se podia hacer con Postman.
 */
class OperarUnPedidoTest extends PruebaE2E {

    @Test
    @DisplayName("abre un caso, lo atiende desde la bandeja y el tablero lo cuenta")
    void abreUnCasoLoAtiendeYLoCuenta() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Operacion", nitDe("operar"),
                correoDe("operar"), "operar12345");
        long proceso = api.crearProceso("Order fulfillment", "From checkout to delivery.", "Fulfillment");
        long pool = api.poolDeLaTienda(proceso);
        long rol = api.crearRol("Warehouse");
        long lane = api.crearLane(pool, "Warehouse", rol);
        long inicio = api.crearEvento(lane, "Order received", "INICIO", 20, 80);
        long empacar = api.crearActividad(lane, "Pick and pack", "USUARIO", 180, 80);
        long fin = api.crearEvento(lane, "Order shipped", "FIN", 340, 80);
        api.conectar(inicio, empacar);
        api.conectar(empacar, fin);
        api.publicar(proceso);
        // Sin roles de proceso no hay bandeja propia, y la bandeja propia es lo que la pantalla ensena primero
        api.rolesDeUsuario(api.usuarioId(), rol);

        entrar(tienda);

        // Abrir el caso desde la lista, que es como se arranca un proceso que empieza con un inicio normal
        ir("/casos");
        visible(Paginas.Casos.VACIO);
        pulsable(Paginas.Casos.ABRIR_PANEL).click();
        elegir(Paginas.Casos.PROCESO, "Order fulfillment");
        escribir(Paginas.Casos.REFERENCIA, "ORD-9001");
        pulsable(Paginas.Casos.ENVIAR).click();

        // Y la pagina del caso dice donde se detuvo: en la tarea, con el token encima
        esperarTexto(Paginas.Caso.REFERENCIA, "ORD-9001");
        assertEquals("Open", textoDe(Paginas.Caso.ESTADO));
        // El titulo de un nodo del SVG no se "ve", asi que se lee su texto del DOM y no con getText
        assertEquals("Pick and pack",
                visible(Paginas.Caso.ACTIVO).findElement(By.tagName("title")).getDomProperty("textContent"),
                "el token tiene que quedar sobre la tarea que espera a alguien");
        assertFalse(navegador.findElements(Paginas.Caso.EVENTO).isEmpty(),
                "la linea de tiempo no puede estar vacia: el caso ya hizo cosas");
        assertEquals(2, navegador.findElements(Paginas.Caso.PASO).size(),
                "paso por el inicio y se quedo en la tarea: dos pasos, y el fin todavia no");

        // La bandeja propia la tiene porque se le dio el rol: no hace falta destildar nada
        ir("/tareas");
        esperarTexto(Paginas.Tareas.NODO, "Pick and pack");
        assertTrue(visible(Paginas.Tareas.MIAS).isSelected(), "la bandeja empieza por la de los roles propios");

        pulsable(Paginas.Tareas.TOMAR).click();
        esperarTexto(Paginas.Tareas.AVISO, "Pick and pack");
        pulsable(Paginas.Tareas.COMPLETAR).click();
        pulsable(Paginas.Tareas.DATO_NUEVO).click();
        escribir(Paginas.Tareas.DATO_CLAVE, "packedItems");
        escribir(Paginas.Tareas.DATO_VALOR, "3");
        pulsable(Paginas.Tareas.CONFIRMAR).click();

        // Completar la tarea es lo que mueve el caso: la bandeja queda vacia y el caso termina
        visible(Paginas.Tareas.VACIO);
        ir("/casos");
        esperarTexto(Paginas.Casos.FILA, "ORD-9001");
        pulsable(Paginas.Casos.FILA).click();
        esperarTexto(Paginas.Caso.ESTADO, "Finished");
        assertTrue(navegador.findElements(Paginas.Caso.ACTIVO).isEmpty(),
                "un caso terminado no tiene ningun token vivo");
        assertEquals(3, navegador.findElements(Paginas.Caso.RECORRIDO).size(),
                "y los tres nodos por los que paso tienen que quedar marcados");

        // Y el tablero lo cuenta, que es la ultima pantalla de la demo
        ir("/tablero");
        esperarTexto(Paginas.Tablero.CASOS, "1");
        assertEquals("1", textoDe(Paginas.Tablero.TERMINADOS), "el tablero tiene que contar el pedido terminado");
        assertTrue(visible(Paginas.Tablero.TODO_BIEN).isDisplayed(),
                "y decir que no fallo nada, porque no fallo nada");
    }

    @Test
    @DisplayName("mueve el reloj de la tienda y despues cambia como responden los socios")
    void mueveElRelojYAjustaLosSocios() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Reloj", nitDe("reloj"),
                correoDe("reloj"), "reloj12345");
        entrar(tienda);

        ir("/simulacion");
        assertEquals("0", textoDe(Paginas.Simulacion.RELOJ), "una tienda nueva empieza en el tick cero");
        escribir(Paginas.Simulacion.TICKS, "3");
        pulsable(Paginas.Simulacion.AVANZAR).click();
        esperarTexto(Paginas.Simulacion.AVISO, "tick 3");
        assertEquals("3", textoDe(Paginas.Simulacion.RELOJ), "el reloj tiene que quedar donde lo dejaron");

        // El reloj vive en la misma fila que la configuracion, asi que guardar los socios justo despues es lo que
        // descubre si la pantalla volvio a leer su version: si no lo hizo, esto responde 409
        escribir(Paginas.Simulacion.ENTREGA, "4");
        enviarConEnter(Paginas.Simulacion.ENTREGA);
        esperarTexto(Paginas.Simulacion.AVISO, "partners answer like this");
        assertEquals("4", visible(Paginas.Simulacion.ENTREGA).getDomProperty("value"),
                "y lo que se guardo es lo que la API devolvio");
    }
}
