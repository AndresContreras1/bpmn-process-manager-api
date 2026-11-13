package com.facimus.procesos.common.almacen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PR 61: el almacen en el disco, y que ninguna clave salga de su carpeta. */
class AlmacenEnDiscoTest {

    @TempDir
    Path raiz;

    @TempDir
    Path fuera;

    private Path archivo(String contenido) throws IOException {
        return Files.writeString(fuera.resolve("subida-" + System.nanoTime()), contenido);
    }

    @Test
    @DisplayName("guarda, abre y reemplaza por clave")
    void guardarYAbrir() throws IOException {
        AlmacenEnDisco almacen = new AlmacenEnDisco(raiz);
        String clave = ClaveDeAlmacen.deDocumento(1, 12, 1, "a1b2");

        almacen.guardar(clave, archivo("primera"), "text/plain");
        almacen.guardar(clave, archivo("segunda"), "text/plain");

        try (InputStream contenido = almacen.abrir(clave)) {
            assertThat(new String(contenido.readAllBytes())).isEqualTo("segunda");
        }
        assertThat(almacen.descripcion()).contains(raiz.toString());
    }

    @Test
    @DisplayName("abrir una clave que no esta dice cual falta")
    void noEsta() {
        AlmacenEnDisco almacen = new AlmacenEnDisco(raiz);

        assertThatThrownBy(() -> almacen.abrir("empresas/1/documentos/9/1-x"))
                .isInstanceOf(ArchivoNoEncontradoException.class).hasMessageContaining("empresas/1/documentos/9/1-x");
    }

    @Test
    @DisplayName("borrar el prefijo de una tienda se lleva lo suyo y nada de otra; si no habia nada, no falla")
    void borrarPrefijo() throws IOException {
        AlmacenEnDisco almacen = new AlmacenEnDisco(raiz);
        almacen.guardar(ClaveDeAlmacen.deDocumento(1, 12, 1, "a"), archivo("uno"), "text/plain");
        almacen.guardar(ClaveDeAlmacen.deDocumento(1, 13, 1, "b"), archivo("dos"), "text/plain");
        almacen.guardar(ClaveDeAlmacen.deDocumento(11, 14, 1, "c"), archivo("de otra"), "text/plain");

        assertThat(almacen.borrarPrefijo(ClaveDeAlmacen.deEmpresa(1))).as("PR 66: los archivos, no las carpetas")
                .isEqualTo(2);
        assertThat(almacen.borrarPrefijo(ClaveDeAlmacen.deEmpresa(5))).isZero();

        assertThat(raiz.resolve("empresas/1")).doesNotExist();
        assertThat(raiz.resolve("empresas/11/documentos/14/1-c")).exists();
    }

    @Test
    @DisplayName("lo inmutable queda escrito y de solo lectura, pero el disco no puede prometerlo")
    void inmutable() throws IOException {
        AlmacenEnDisco almacen = new AlmacenEnDisco(raiz);

        boolean bloqueado = almacen.guardarInmutable("anclas/1/2026-11-12", "{}".getBytes(), Instant.now());

        assertThat(bloqueado).isFalse();
        assertThat(Files.isWritable(raiz.resolve("anclas/1/2026-11-12"))).isFalse();
        try (InputStream contenido = almacen.abrir("anclas/1/2026-11-12")) {
            assertThat(contenido.readAllBytes()).containsExactly('{', '}');
        }
        // PR 66: en el disco, lo inmutable se lee del mismo sitio que lo demas
        try (InputStream contenido = almacen.abrirInmutable("anclas/1/2026-11-12")) {
            assertThat(contenido.readAllBytes()).containsExactly('{', '}');
        }
        assertThatThrownBy(() -> almacen.abrirInmutable("anclas/1/2026-11-13"))
                .isInstanceOf(ArchivoNoEncontradoException.class);
    }

    @Test
    @DisplayName("una clave con .. o que no es de las del almacen no se acepta")
    void clavesInvalidas() throws IOException {
        AlmacenEnDisco almacen = new AlmacenEnDisco(raiz);
        Path subida = archivo("x");

        assertThatThrownBy(() -> almacen.guardar("../fuera", subida, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.abrir("empresas/1/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.abrir("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.abrir("Empresas/1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.abrir("empresas//1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ClaveDeAlmacen.comprobar(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("las claves empiezan por la tienda y no dicen nada del archivo")
    void claves() {
        assertThat(ClaveDeAlmacen.deEmpresa(7)).isEqualTo("empresas/7/");
        assertThat(ClaveDeAlmacen.deDocumento(7, 12, 3, "9f1c")).isEqualTo("empresas/7/documentos/12/3-9f1c");
        assertThat(ClaveDeAlmacen.deAncla(7, "2026-11-12")).isEqualTo("anclas/7/2026-11-12");
    }
}
