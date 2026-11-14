package com.facimus.procesos.gestion.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.response.ExportacionResponse;
import com.facimus.procesos.gestion.service.ExportacionService;
import com.facimus.procesos.gestion.service.ExportacionService.Descarga;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * PR 66: la exportacion completa de la tienda, para su administrador, y su descarga con el enlace firmado del correo.
 * Pedirla pide ademas haber confirmado la identidad hace poco: lo resuelve la matriz de rutas, como la baja.
 */
@Tag(name = "Organization export", description = "A full export of the store in one ZIP, with a manifest of SHA-256 "
        + "fingerprints, delivered by a signed link that works for 24 hours. Administrators only.")
@RestController
@RequiredArgsConstructor
public class ExportacionController {

    private static final String EXPORTACIONES = "/api/v1/organizacion/exportaciones";

    private final ExportacionService exportacionService;

    @Operation(summary = "Ask for a full export of the store",
            description = "The job queue builds a ZIP with every process (each version as BPMN and JSON, the draft, "
                    + "the documentation, the characterization sheet and the manuals), the documents with every "
                    + "version, the comments, the history, the audit trail with its chain hashes and anchors, the "
                    + "people without any secret, and the cases as JSON and CSV, plus a manifest.json with the size "
                    + "and SHA-256 of every file. When it is ready, a link that works for 24 hours goes by e-mail to "
                    + "whoever asked, in their language. One export at a time per store; it also works while the "
                    + "store is closing. Asks for a recent verification of the identity, like closing the store.")
    @ApiResponse(responseCode = "202", description = "Export asked for; it starts PENDIENTE",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the export"))
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @PostMapping(EXPORTACIONES)
    public ResponseEntity<ExportacionResponse> pedir(@AuthenticationPrincipal ApiPrincipal principal) {
        ExportacionResponse exportacion = exportacionService.pedir(principal.empresaId(), principal.usuarioId());
        return ResponseEntity.accepted().location(URI.create(EXPORTACIONES + "/" + exportacion.id()))
                .body(exportacion);
    }

    @Operation(summary = "List the exports of the store", description = "Newest first, up to 50 a page, with their "
            + "state, size, SHA-256 and until when their link works. The link itself only goes by e-mail.")
    @ApiResponse(responseCode = "200", description = "A page of exports")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping(EXPORTACIONES)
    public ResponseEntity<PageResponse<ExportacionResponse>> listar(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(exportacionService.listar(principal.empresaId(), Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Get an export and whether it is ready")
    @ApiResponse(responseCode = "200", description = "The export")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping(EXPORTACIONES + "/{id}")
    public ResponseEntity<ExportacionResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(exportacionService.obtener(principal.empresaId(), id));
    }

    @Operation(summary = "Download an export with its signed link",
            description = "The link comes in the e-mail that says the export is ready, and works until the export "
                    + "expires, 24 hours later. A changed or expired link, one of an export already deleted, or one "
                    + "whose requester is no longer an active administrator answers 404. Every download goes to the "
                    + "audit trail.")
    @ApiResponse(responseCode = "200", description = "The ZIP, as an attachment")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @SecurityRequirements()
    @GetMapping("/api/v1/descargas/exportaciones/{id}")
    public ResponseEntity<InputStreamResource> descargar(@PathVariable Long id,
            @Parameter(description = "The organization of the export") @RequestParam Long empresa,
            @Parameter(description = "Expiry, in seconds since 1970") @RequestParam long vence,
            @Parameter(description = "The signature") @RequestParam String firma) {
        Descarga descarga = exportacionService.descargar(empresa, id, vence, firma);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(descarga.tamano())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(descarga.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(descarga.contenido()));
    }
}
