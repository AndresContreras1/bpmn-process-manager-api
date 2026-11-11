package com.facimus.procesos.modelado.controller;

import java.io.InputStream;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PR 54: un archivo BPMN 2.0 al modelo. El cuerpo es el archivo tal cual; se lee como flujo y {@code SecureXml} corta
 * al pasar el limite, asi que un cuerpo enorme sin {@code Content-Length} tampoco llega entero a memoria.
 */
@Tag(name = "Diagrams", description = "A whole BPMN diagram in one request, ready to draw")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ImportacionBpmnController {

    private static final String BPMN = "application/bpmn+xml";

    private final ImportacionBpmnService importacionBpmnService;
    private final GuardadoDelModeloService guardadoDelModeloService;

    @Operation(summary = "Import a BPMN file as a new process",
            description = "The body is a BPMN 2.0 file (up to 5 MB) from this product, Camunda Modeler, bpmn.io, "
                    + "Signavio, Bizagi or any other tool. It becomes a new draft process: the participant with its "
                    + "own process is the organization, black boxes are partners, lanes take the role with their "
                    + "name. What the model lacks is converted to the closest thing or left out, and the report says "
                    + "which and why; what another tool wrote in extensionElements is kept and exported again. A "
                    + "file that breaks the model's rules (a flow into a start event or out of an end event, one "
                    + "between pools, a message inside one pool) answers 409 with every problem; what the model "
                    + "stores but does not publish, like gateway exits without condition, is imported and the "
                    + "diagnosis flags it. Lanes asking for roles that do not exist need an administrator, who "
                    + "creates them while importing.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE,
                            schema = @Schema(type = "string", description = "A BPMN 2.0 document"))))
    @ApiResponse(responseCode = "201", description = "The process and the import report")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping(value = "/procesos/importar", consumes = {MediaType.APPLICATION_XML_VALUE,
        MediaType.TEXT_XML_VALUE, BPMN, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<ImportacionResponse> importar(@RequestParam(required = false) String nombre,
            InputStream archivo, @AuthenticationPrincipal ApiPrincipal principal) {
        ImportacionResponse importacion = importacionBpmnService.importar(principal.empresaId(),
                principal.usuarioId(), principal.rol(), archivo, nombre);
        return ResponseEntity.created(URI.create("/api/v1/procesos/" + importacion.procesoId())).body(importacion);
    }

    @Operation(summary = "Replace a process draft with a BPMN file",
            description = "The draft becomes what the file says, element by element by bpmnId: what is new is "
                    + "created, what changed is edited and what the file no longer has is deleted, in one "
                    + "transaction with one history line. Importing the file the process exports changes nothing. "
                    + "Deleting needs an administrator, and pools and lanes follow the store's structure policy. "
                    + "The process name, description and category stay as they are. This is also the editor's "
                    + "autosave: with If-Match it answers 412 if the draft is no longer what was read, saying "
                    + "what changed; an Idempotency-Key makes a retry safe; and if the file changes nothing, nothing "
                    + "is written. The response carries the new ETag.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE,
                            schema = @Schema(type = "string", description = "A BPMN 2.0 document"))))
    @ApiResponse(responseCode = "200", description = "What changed and the import report")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @ApiResponse(responseCode = "412", ref = "PreconditionFailed")
    @PutMapping(value = "/procesos/{procesoId}/modelo", consumes = {MediaType.APPLICATION_XML_VALUE,
        MediaType.TEXT_XML_VALUE, BPMN, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<ImportacionResponse> reemplazar(@PathVariable Long procesoId, InputStream archivo,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal ApiPrincipal principal) {
        ImportacionResponse guardado = guardadoDelModeloService.guardar(principal.empresaId(), principal.usuarioId(),
                principal.rol(), procesoId, archivo, ifMatch);
        return ResponseEntity.ok().eTag(guardado.huella()).body(guardado);
    }
}
