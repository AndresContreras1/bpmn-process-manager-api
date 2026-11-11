package com.facimus.procesos.common.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * PR 53: un documento que el navegador guarda como archivo en vez de mostrarlo. El tipo va en la respuesta y no en el
 * mapeo del endpoint: un {@code produces} haria que un 404 pedido con {@code Accept: application/xml} terminara en un
 * 406, porque el error se escribe en JSON. El nombre del archivo llega ya en ASCII ({@code ModeloBpmn}), asi que va
 * sin la forma {@code filename*}.
 */
public final class Descargas {

    public static final MediaType XML_UTF8 = new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8);

    private Descargas() {
    }

    public static ResponseEntity<byte[]> xml(String archivo, String contenido) {
        return xml(archivo, contenido, null);
    }

    /** PR 40: con su {@code ETag}: un {@code If-None-Match} que coincide responde 304. */
    public static ResponseEntity<byte[]> xml(String archivo, String contenido, String etiqueta) {
        return ResponseEntity.ok()
                .eTag(etiqueta)
                .contentType(XML_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo).build().toString())
                .body(contenido.getBytes(StandardCharsets.UTF_8));
    }
}
