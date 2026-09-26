package com.facimus.procesos.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo primero que hace cualquiera: entrar, y crear un proceso. */
class EntrarYCrearProcesoTest extends PruebaE2E {

    private static final String NIT = nitDe("entrar-y-crear");

    @Test
    @DisplayName("una clave equivocada no deja entrar y lo dice")
    void unaClaveEquivocadaNoDejaEntrar() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Entrar", NIT, correoDe("entrar"), "entrar12345");

        ir("/login");
        escribir(Paginas.Login.EMAIL, tienda.correo());
        escribir(Paginas.Login.CLAVE, "esta-no-es");
        pulsable(Paginas.Login.ENTRAR).click();

        // El mensaje exacto, no "hay un error": la primera version de esta prueba pasaba con un 403 de CORS,
        // que no tiene nada que ver con la clave, y por eso no se entero de que el login estaba roto.
        assertEquals("Wrong email or password.", textoDe(Paginas.Login.ERROR),
                "una clave equivocada es un 401, y la pagina tiene que decir eso y no otra cosa");
        assertTrue(navegador.getCurrentUrl().contains("/login"), "y quedarse en el login");
    }

    @Test
    @DisplayName("entra, crea un proceso y lo ve en su lista")
    void entraYCreaUnProceso() {
        ApiDeDatos.Tienda tienda = api.registrarTienda("Tienda Crear", nitDe("entrar-y-crear-2"),
                correoDe("crear"), "crear12345");

        ir("/login");
        escribir(Paginas.Login.EMAIL, tienda.correo());
        escribir(Paginas.Login.CLAVE, tienda.clave());
        pulsable(Paginas.Login.ENTRAR).click();
        esperarUrl("/procesos");

        pulsable(Paginas.Procesos.NUEVO).click();
        esperarUrl("/procesos/nuevo");
        escribir(Paginas.Formulario.NOMBRE, "Returns and refunds");
        escribir(Paginas.Formulario.CATEGORIA, "After-sales");
        escribir(Paginas.Formulario.DESCRIPCION, "What happens when a customer sends an order back.");
        pulsable(Paginas.Formulario.GUARDAR).click();

        // Crear lleva al detalle del proceso recien creado, y nace como borrador
        esperarTexto(Paginas.Detalle.NOMBRE, "Returns and refunds");
        assertEquals("Draft", textoDe(Paginas.Detalle.ESTADO), "un proceso nuevo nace como borrador");

        ir("/procesos");
        esperarTexto(Paginas.Procesos.FILA, "Returns and refunds");
    }
}
