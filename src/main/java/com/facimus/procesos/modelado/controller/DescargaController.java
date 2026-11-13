package com.facimus.procesos.modelado.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.service.Descarga;
import com.facimus.procesos.modelado.service.DocumentoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PR 61: bajar un documento con su enlace firmado, sin sesion: la firma es el permiso. Siempre como adjunto, con
 * {@code nosniff} y una politica que no deja correr nada: un SVG o un HTML se bajan, nunca se abren en la pagina.
 */
@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1/descargas")
@RequiredArgsConstructor
public class DescargaController {

    private final DocumentoService documentoService;

    @Operation(summary = "Download a document version with a signed link",
            description = "The link comes from GET /api/v1/documentos/{id}/enlace and works for five minutes. A "
                    + "changed or expired link answers 404.")
    @ApiResponse(responseCode = "200", description = "The file, as an attachment")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @SecurityRequirements()
    @GetMapping("/documentos/{documentoId}/{numero}")
    public ResponseEntity<InputStreamResource> descargar(@PathVariable Long documentoId, @PathVariable int numero,
            @Parameter(description = "The organization of the document") @RequestParam Long empresa,
            @Parameter(description = "Expiry, in seconds since 1970") @RequestParam long vence,
            @Parameter(description = "The signature") @RequestParam String firma) {
        Descarga descarga = documentoService.descargar(empresa, documentoId, numero, vence, firma);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.tipo()))
                .contentLength(descarga.tamano())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(descarga.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(descarga.contenido()));
    }
}
