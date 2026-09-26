package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que una tienda administra: a quien le deja ver un proceso, y quien trabaja en ella. */
class CompartirYAdministrarTest extends PruebaE2E {

    @Test
    @DisplayName("comparte un proceso y la invitada lo ve, sin poder tocarlo")
    void compartirYVerComoInvitada() {
        String nitInvitada = nitDe("compartir-invitada");
        ApiDeDatos.Tienda invitada = api.registrarTienda("Tienda Invitada", nitInvitada,
                correoDe("invitada"), "invitada12345");

        // La duena, con un proceso publicado: sin publicar no hay version vigente que ensenarle a nadie
        ApiDeDatos duenaApi = new ApiDeDatos(API);
        ApiDeDatos.Tienda duena = duenaApi.registrarTienda("Tienda Duena", nitDe("compartir-duena"),
                correoDe("duena"), "duena12345");
        long proceso = duenaApi.crearProceso("Order fulfillment", "From checkout to delivery.", "Fulfillment");
        long pool = duenaApi.poolDeLaTienda(proceso);
        long lane = duenaApi.crearLane(pool, "Sales", duenaApi.crearRol("Sales"));
        long inicio = duenaApi.crearEvento(lane, "Order received", "INICIO", 20, 80);
        long revisar = duenaApi.crearActividad(lane, "Review the order", "USUARIO", 160, 80);
        long fin = duenaApi.crearEvento(lane, "Order handled", "FIN", 320, 80);
        duenaApi.conectar(inicio, revisar);
        duenaApi.conectar(revisar, fin);

        entrar(duena);
        ir("/procesos/" + proceso + "/editar-diagrama");
        pulsable(Paginas.Editor.PUBLICAR).click();
        esperarTexto(Paginas.Editor.PUBLICADO, "version 1");

        // Compartirlo, por el NIT de la otra tienda
        ir("/procesos/" + proceso + "/compartir");
        escribir(Paginas.Compartir.NIT, nitInvitada);
        pulsable(Paginas.Compartir.ENVIAR).click();
        esperarTexto(Paginas.Compartir.INVITADA, "Tienda Invitada");

        // Y ahora, desde la otra tienda
        salir();
        entrar(invitada);
        esperarTexto(Paginas.Procesos.RECIBIDO, "Order fulfillment");
        assertTrue(textoDe(Paginas.Procesos.RECIBIDO).contains("Tienda Duena"),
                "la lista tiene que decir de quien es el proceso");

        ir("/procesos/" + proceso);
        esperarTexto(Paginas.Detalle.NOMBRE, "Order fulfillment");
        assertTrue(visible(Paginas.Detalle.INVITADA).isDisplayed(),
                "la invitada tiene que saber que esta viendo la version vigente de otra tienda");
        assertTrue(navegador.findElements(Paginas.Detalle.EDITAR).isEmpty(),
                "una invitada no puede editar el proceso de otra tienda");
        assertTrue(navegador.findElements(Paginas.Detalle.MODELAR).isEmpty(),
                "ni modelarlo");
    }

    @Test
    @DisplayName("un administrador crea un rol y da de alta a alguien, que recibe una clave de un solo uso")
    void administrarRolesYUsuarios() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Admin", nitDe("administrar"),
                correoDe("admin"), "admin12345");
        entrar(tienda);

        ir("/roles");
        escribir(Paginas.Roles.NOMBRE, "Packing");
        escribir(Paginas.Roles.DESCRIPCION, "Packs what has been sold.");
        pulsable(Paginas.Roles.GUARDAR).click();
        esperarTexto(Paginas.Roles.AVISO, "Packing");
        esperarTexto(Paginas.Roles.FILA, "Packing");

        ir("/usuarios");
        escribir(Paginas.Usuarios.NOMBRE, "Elena Editora");
        escribir(Paginas.Usuarios.EMAIL, correoDe("elena"));
        pulsable(Paginas.Usuarios.CREAR).click();

        // La clave temporal se ensena una sola vez, y esta es esa vez
        String panel = textoDe(Paginas.Usuarios.CLAVE_TEMPORAL);
        assertTrue(panel.contains(correoDe("elena")), "el panel tiene que decir de quien es la clave");
        assertTrue(panel.contains("shown once"), "y avisar de que no se vuelve a ver");
        esperarTexto(Paginas.Usuarios.FILA, "Elena Editora");

        // Recargar la pagina la pierde, que es justo lo que el panel advierte
        ir("/usuarios");
        esperarTexto(Paginas.Usuarios.FILA, "Elena Editora");
        assertTrue(navegador.findElements(Paginas.Usuarios.CLAVE_TEMPORAL).isEmpty(),
                "la clave temporal no puede sobrevivir a una recarga: no se guarda en ningun sitio");
    }

    @Test
    @DisplayName("quien no es administrador no ve la administracion")
    void sinRolNoHayAdministracion() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Lectora", nitDe("solo-lectura"),
                correoDe("lectora"), "lectora12345");
        entrar(tienda);

        // El administrador si la ve: es el contraste que da sentido a la comprobacion de abajo
        assertFalse(navegador.findElements(org.openqa.selenium.By.id("navbar-usuarios")).isEmpty(),
                "el administrador de la tienda tiene que ver la administracion");

        ir("/usuarios");
        assertEquals(0, navegador.findElements(org.openqa.selenium.By.id("usuarios-solo-admin")).size(),
                "y no puede toparse con el aviso de que no puede");
    }

    private void entrar(ApiDeDatos.Tienda tienda) {
        ir("/login");
        escribir(Paginas.Login.EMAIL, tienda.correo());
        escribir(Paginas.Login.CLAVE, tienda.clave());
        pulsable(Paginas.Login.ENTRAR).click();
        esperarUrl("/procesos");
    }

    private void salir() {
        ((org.openqa.selenium.JavascriptExecutor) navegador).executeScript("localStorage.clear()");
    }
}
