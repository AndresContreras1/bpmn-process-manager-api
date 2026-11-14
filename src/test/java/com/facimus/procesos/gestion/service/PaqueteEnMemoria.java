package com.facimus.procesos.gestion.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PR 66: un paquete de mentira para las pruebas de cada parte de la exportacion: guarda lo que le escriben, por ruta y
 * en orden, sin ZIP. Una ruta repetida falla, como en el de verdad.
 */
public class PaqueteEnMemoria implements Paquete {

    private final Map<String, Object> archivos = new LinkedHashMap<>();

    /** Lo escrito: el valor de un JSON, el texto, los bytes copiados, o la lista de filas (el CSV, con su cabecera). */
    public Map<String, Object> archivos() {
        return archivos;
    }

    @SuppressWarnings("unchecked")
    public List<Object> filas(String ruta) {
        return (List<Object>) archivos.get(ruta);
    }

    @Override
    public void json(String ruta, Object valor) {
        poner(ruta, valor);
    }

    @Override
    public void texto(String ruta, String contenido) {
        poner(ruta, contenido);
    }

    @Override
    public void copiar(String ruta, InputStream contenido) {
        try {
            poner(ruta, contenido.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public FilasJson filasJson(String ruta) {
        List<Object> filas = new ArrayList<>();
        poner(ruta, filas);
        return new FilasJson() {
            @Override
            public void agregar(Object fila) {
                filas.add(fila);
            }

            @Override
            public void close() {
                // Nada que cerrar: las filas ya estan en su lista
            }
        };
    }

    @Override
    public FilasCsv filasCsv(String ruta, List<String> cabecera) {
        List<Object> filas = new ArrayList<>();
        filas.add(cabecera);
        poner(ruta, filas);
        return new FilasCsv() {
            @Override
            public void agregar(List<?> celdas) {
                filas.add(celdas);
            }

            @Override
            public void close() {
                // Nada que cerrar: las filas ya estan en su lista
            }
        };
    }

    private void poner(String ruta, Object valor) {
        if (archivos.putIfAbsent(ruta, valor) != null) {
            throw new IllegalArgumentException("Ruta repetida: " + ruta);
        }
    }
}
