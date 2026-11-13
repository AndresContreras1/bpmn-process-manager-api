package com.facimus.procesos.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.facimus.procesos.common.model.Accion;

/** PR 60: la tabla de que accion pide cada ruta. */
class ReglaDeRecursoTest {

    private static final String P = "/api/v1/procesos/{procesoId}";

    static Stream<Arguments> rutasDeProceso() {
        return Stream.of(
                Arguments.of("GET", "/api/v1/procesos/{id}", null, Accion.VER),
                Arguments.of("PUT", "/api/v1/procesos/{id}", null, Accion.MODELAR),
                Arguments.of("PATCH", "/api/v1/procesos/{id}", null, Accion.PUBLICAR),
                Arguments.of("DELETE", "/api/v1/procesos/{id}", null, Accion.ADMINISTRAR),
                Arguments.of("PUT", "/api/v1/procesos/{id}/ficha", null, Accion.MODELAR),
                Arguments.of("GET", P + "/diagrama", null, Accion.VER),
                Arguments.of("GET", P + "/modelo", null, Accion.EXPORTAR),
                Arguments.of("GET", P + "/modelo", "dibujar", Accion.VER),
                Arguments.of("GET", P + "/versiones/{numero}/modelo", null, Accion.EXPORTAR),
                Arguments.of("GET", P + "/versiones/{numero}/modelo", "dibujar", Accion.VER),
                Arguments.of("GET", P + "/revisiones/{revisionId}/modelo", null, Accion.EXPORTAR),
                Arguments.of("PUT", P + "/modelo", null, Accion.MODELAR),
                Arguments.of("PUT", P + "/modo", null, Accion.MODELAR),
                Arguments.of("POST", P + "/pools", null, Accion.MODELAR),
                Arguments.of("POST", P + "/comparar", null, Accion.VER),
                Arguments.of("POST", P + "/casos", null, Accion.VER),
                Arguments.of("POST", P + "/mensajes-entrantes", null, Accion.VER),
                Arguments.of("POST", P + "/revision", null, Accion.REVISAR),
                Arguments.of("PATCH", P + "/versiones/{numero}", null, Accion.PUBLICAR),
                Arguments.of("GET", P + "/compartidos", null, Accion.VER),
                Arguments.of("POST", P + "/compartidos", null, Accion.ADMINISTRAR),
                Arguments.of("GET", P + "/concesiones", null, Accion.ADMINISTRAR),
                Arguments.of("POST", P + "/concesiones", null, Accion.ADMINISTRAR),
                Arguments.of("POST", P + "/bloqueo/tomar-control", null, Accion.MODELAR),
                Arguments.of("GET", P + "/en-vivo", null, Accion.VER),
                Arguments.of("GET", P + "/documentos", null, Accion.VER),
                Arguments.of("POST", P + "/documentos", null, Accion.MODELAR),
                Arguments.of("GET", P + "/documentacion", null, Accion.VER),
                Arguments.of("PUT", P + "/documentacion", null, Accion.MODELAR),
                Arguments.of("GET", P + "/caracterizacion", null, Accion.VER),
                Arguments.of("PUT", P + "/caracterizacion", null, Accion.MODELAR),
                Arguments.of("PUT", P + "/versiones/{numero}/dibujo", null, Accion.PUBLICAR),
                Arguments.of("GET", P + "/manuales", null, Accion.VER),
                Arguments.of("POST", P + "/manuales", null, Accion.EXPORTAR),
                // PR 68: probar el borrador es modelarlo; mirar los inicios temporizados, verlo
                Arguments.of("POST", P + "/pruebas", null, Accion.MODELAR),
                Arguments.of("GET", P + "/temporizadores", null, Accion.VER),
                // PR 69: el formulario de inicio de la version vigente se lee como el proceso
                Arguments.of("GET", P + "/formulario-de-inicio", null, Accion.VER),
                // PR 63: comentar no es modelar: tambien lo hace quien solo lee
                Arguments.of("GET", P + "/comentarios", null, Accion.VER),
                Arguments.of("POST", P + "/comentarios", null, Accion.COMENTAR));
    }

    @ParameterizedTest(name = "{0} {1} {2} pide {3}")
    @MethodSource("rutasDeProceso")
    void rutaDeProceso(String metodo, String patron, String para, Accion esperada) {
        ReglaDeRecurso regla = ReglaDeRecurso.de(metodo, patron, para).orElseThrow();

        assertThat(regla.accion()).isEqualTo(esperada);
        assertThat(regla.clase()).isEqualTo(ReglaDeRecurso.Clase.PROCESO);
    }

    static Stream<Arguments> rutasDeElementos() {
        return Stream.of("pools", "lanes", "actividades", "gateways", "eventos", "arcos", "mensajes", "artefactos",
                "asociaciones", "nodos", "documentos", "manuales").flatMap(tipo -> Stream.of(
                        Arguments.of("GET", "/api/v1/" + tipo + "/{id}", Accion.VER, tipo),
                        Arguments.of("PUT", "/api/v1/" + tipo + "/{id}", Accion.MODELAR, tipo),
                        Arguments.of("DELETE", "/api/v1/" + tipo + "/{id}", Accion.MODELAR, tipo)));
    }

    @ParameterizedTest(name = "{0} {1} pide {2}")
    @MethodSource("rutasDeElementos")
    void rutaDeElemento(String metodo, String patron, Accion esperada, String tipo) {
        ReglaDeRecurso regla = ReglaDeRecurso.de(metodo, patron, null).orElseThrow();

        assertThat(regla.accion()).isEqualTo(esperada);
        assertThat(regla.clase()).isEqualTo(ReglaDeRecurso.Clase.ELEMENTO);
        assertThat(regla.tipo()).isEqualTo(tipo);
    }

    static Stream<Arguments> rutasDeComentarios() {
        return Stream.of(Arguments.of("PUT", "/api/v1/comentarios/{id}"),
                Arguments.of("DELETE", "/api/v1/comentarios/{id}"),
                Arguments.of("POST", "/api/v1/comentarios/{id}/respuestas"),
                Arguments.of("POST", "/api/v1/comentarios/{id}/resolver"),
                Arguments.of("POST", "/api/v1/comentarios/{id}/reabrir"));
    }

    @ParameterizedTest(name = "{0} {1} pide COMENTAR sobre el proceso del comentario")
    @MethodSource("rutasDeComentarios")
    void rutaDeComentario(String metodo, String patron) {
        assertThat(ReglaDeRecurso.de(metodo, patron, null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.COMENTAR, ReglaDeRecurso.Clase.ELEMENTO, "comentarios"));
    }

    @Test
    @DisplayName("lo que cuelga de un elemento se decide por ese elemento")
    void anidado() {
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/lanes/{laneId}/actividades", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "lanes"));
    }

    @Test
    @DisplayName("PR 69: guardar el formulario de una tarea o de un inicio es modelar ese elemento")
    void formularios() {
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/actividades/{id}/formulario", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "actividades"));
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/eventos/{id}/formulario", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "eventos"));
        // El de una tarea es una ruta de operacion, como el resto de la bandeja y /casos: la acota la tienda, sin
        // permiso por proceso
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/tareas/{id}/formulario", null)).isEmpty();
    }

    @Test
    @DisplayName("PR 70: decidir a quien le llega una tarea es modelar la actividad; operar las tareas no pide permiso "
            + "por proceso")
    void asignacion() {
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/actividades/{id}/asignacion", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "actividades"));
        for (String accion : new String[] {"reclamar", "soltar", "reasignar", "delegar"}) {
            assertThat(ReglaDeRecurso.de("POST", "/api/v1/tareas/{id}/" + accion, null)).isEmpty();
        }
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/tareas/carga", null)).isEmpty();
    }

    @Test
    @DisplayName("PR 71: el plazo de una tarea o de un caso y lo que escala un borde es modelar el elemento; el tablero "
            + "se lee con ver el proceso")
    void plazos() {
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/actividades/{id}/plazo", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "actividades"));
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/eventos/{id}/plazo", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "eventos"));
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/eventos/{id}/escalamiento", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.MODELAR, ReglaDeRecurso.Clase.ELEMENTO, "eventos"));
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/procesos/{id}/tablero", null).orElseThrow())
                .isEqualTo(new ReglaDeRecurso(Accion.VER, ReglaDeRecurso.Clase.PROCESO, null));
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/empresas/actual/tablero", null)).isEmpty();
    }

    @Test
    @DisplayName("en una carpeta, editarla es modelar y conceder es administrar")
    void carpetas() {
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/carpetas/{id}", null).orElseThrow().accion())
                .isEqualTo(Accion.MODELAR);
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/carpetas/{carpetaId}/concesiones", null).orElseThrow().accion())
                .isEqualTo(Accion.ADMINISTRAR);
    }

    @Test
    @DisplayName("lo que no tiene recurso en la ruta no se decide aqui")
    void sinRecurso() {
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/procesos", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/procesos", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/procesos/importar", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/procesos/etiquetas", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/arcos", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/usuarios/{id}", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("GET", null, null)).isEmpty();
        // PR 61: bajar con un enlace firmado no tiene sesion: la firma es el permiso
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/descargas/documentos/{documentoId}/{numero}", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/descargas/manuales/{manualId}/{formato}", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/documentacion/vista-previa", null)).isEmpty();
        // PR 68: el calendario es de la tienda entera, y un caso se comprueba en su service
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/organizacion/calendario", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("DELETE", "/api/v1/organizacion/calendario/festivos/{fecha}", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/casos/{id}/temporizadores", null)).isEmpty();
        // PR 63: las notificaciones son de cada persona, no de un proceso: las filtra su servicio
        assertThat(ReglaDeRecurso.de("GET", "/api/v1/notificaciones", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("POST", "/api/v1/notificaciones/{id}/leida", null)).isEmpty();
        assertThat(ReglaDeRecurso.de("PUT", "/api/v1/notificaciones/preferencias", null)).isEmpty();
    }
}
