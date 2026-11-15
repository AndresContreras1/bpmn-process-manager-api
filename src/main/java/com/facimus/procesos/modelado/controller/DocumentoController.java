package com.facimus.procesos.modelado.controller;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.DocumentoDetalleResponse;
import com.facimus.procesos.modelado.dto.response.DocumentoResponse;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.service.DocumentoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PR 61: los documentos de un proceso. El cuerpo de una subida es el archivo tal cual, sin multipart: se lee como
 * flujo a un temporal y se corta al pasar el tope, asi que un cuerpo enorme no llega entero a memoria.
 */
@Tag(name = "Documents", description = "Files attached to a process or to one of its elements, with versions, an "
        + "antivirus check and signed download links")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentoController {

    private static final String CUERPO = "The file itself, up to 25 MB. Its type is detected from the content and has "
            + "to be one of: PDF, PNG, JPEG, GIF, WebP, SVG, plain text, CSV, Markdown, BPMN, Word, Excel or "
            + "PowerPoint (OOXML), or OpenDocument text or spreadsheet.";

    private final DocumentoService documentoService;

    @Operation(summary = "List the documents of a process",
            description = "Each with its latest version. With elemento, only the ones attached to that element.")
    @ApiResponse(responseCode = "200", description = "The documents")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/documentos")
    public ResponseEntity<List<DocumentoResponse>> listar(@PathVariable Long procesoId,
            @Parameter(description = "bpmnId of an element", example = "Activity_0b3k9xq")
            @RequestParam(required = false) String elemento,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentoService.listar(principal.empresaId(), procesoId, elemento));
    }

    @Operation(summary = "Upload a document",
            description = "Attaches a new document to the process, or to one of its elements with elemento. It "
                    + "starts PENDIENTE: the antivirus checks it in the background, and only then can it be "
                    + "downloaded. The name is cleaned and takes the extension of the real type.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = CUERPO,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))))
    @ApiResponse(responseCode = "201", description = "Document created, waiting for its check",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new document"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "413", ref = "PayloadTooLarge")
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType")
    @PostMapping(value = "/procesos/{procesoId}/documentos", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<DocumentoResponse> subir(@PathVariable Long procesoId,
            @Parameter(description = "File name", example = "packing-instructions.pdf")
            @RequestParam String nombre,
            @Parameter(description = "bpmnId of the element it is attached to", example = "Activity_0b3k9xq")
            @RequestParam(required = false) String elemento,
            InputStream archivo, @AuthenticationPrincipal ApiPrincipal principal) {
        DocumentoResponse documento = documentoService.subir(principal.empresaId(), principal.usuarioId(), procesoId,
                elemento, nombre, archivo);
        return ResponseEntity.created(URI.create("/api/v1/documentos/" + documento.id())).body(documento);
    }

    @Operation(summary = "Get a document with all its versions")
    @ApiResponse(responseCode = "200", description = "The document and its versions, newest first")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/documentos/{id}")
    public ResponseEntity<DocumentoDetalleResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentoService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Upload a new version of a document",
            description = "The document keeps its history: earlier versions stay listed and downloadable.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = CUERPO,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))))
    @ApiResponse(responseCode = "201", description = "Version created, waiting for its check")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "413", ref = "PayloadTooLarge")
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType")
    @PostMapping(value = "/documentos/{id}/versiones", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<DocumentoResponse> subirVersion(@PathVariable Long id,
            @Parameter(description = "File name", example = "packing-instructions-v2.pdf") @RequestParam String nombre,
            InputStream archivo, @AuthenticationPrincipal ApiPrincipal principal) {
        DocumentoResponse documento = documentoService.subirVersion(principal.empresaId(), principal.usuarioId(), id,
                nombre, archivo);
        return ResponseEntity.created(URI.create("/api/v1/documentos/" + id)).body(documento);
    }

    @Operation(summary = "Get a link to download a version",
            description = "A link that works without a session for five minutes, for the latest version or the one "
                    + "in version. The file always downloads as an attachment, never shown in the page. A version "
                    + "still being checked, or blocked by the check, has no link.")
    @ApiResponse(responseCode = "200", description = "The signed link and when it expires")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @GetMapping("/documentos/{id}/enlace")
    public ResponseEntity<EnlaceDeDescargaResponse> enlace(@PathVariable Long id,
            @Parameter(description = "Version number; the latest if left out", example = "1")
            @RequestParam(required = false) Integer version,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentoService.enlace(principal.empresaId(), id, version));
    }

    @Operation(summary = "Delete a document", description = "It leaves the process with all its versions.")
    @ApiResponse(responseCode = "204", description = "Document deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/documentos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        documentoService.eliminar(principal.empresaId(), principal.usuarioId(), id);
        return ResponseEntity.noContent().build();
    }
}
