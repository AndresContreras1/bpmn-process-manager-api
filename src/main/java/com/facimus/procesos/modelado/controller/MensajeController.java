package com.facimus.procesos.modelado.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.modelado.dto.request.EditarMensajeRequest;
import com.facimus.procesos.modelado.dto.request.MensajeRequest;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.service.MensajeService;
import com.facimus.procesos.security.ApiPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HU-25 a HU-27: mensajes (comunicacion entre pools). */
@Tag(name = "Message flows", description = "Messages exchanged between two participants of a process")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MensajeController {

    private final MensajeService mensajeService;

    @Operation(summary = "List the message flows of a process")
    @ApiResponse(responseCode = "200", description = "Message flows of the process")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/mensajes")
    public ResponseEntity<List<MensajeResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(mensajeService.listarPorProceso(empresaId, procesoId));
    }

    @Operation(summary = "Get a message flow")
    @ApiResponse(responseCode = "200", description = "The message flow")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/mensajes/{id}")
    public ResponseEntity<MensajeResponse> detalle(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(mensajeService.obtener(empresaId, id));
    }

    @Operation(summary = "Add a message flow to a process",
            description = "The message goes from one pool to a different pool.")
    @ApiResponse(responseCode = "201", description = "Message flow created",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new message flow"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/procesos/{procesoId}/mensajes")
    public ResponseEntity<MensajeResponse> crear(@PathVariable Long procesoId,
            @Validated @RequestBody MensajeRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        MensajeResponse mensaje = mensajeService.crear(empresaId, procesoId, request.nombre(), request.contenido(),
                request.poolOrigenId(), request.poolDestinoId());
        return ResponseEntity.created(URI.create("/api/v1/mensajes/" + mensaje.id())).body(mensaje);
    }

    @Operation(summary = "Edit a message flow", description = "Replaces its name and content.")
    @ApiResponse(responseCode = "200", description = "Message flow updated")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/mensajes/{id}")
    public ResponseEntity<MensajeResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EditarMensajeRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        return ResponseEntity.ok(mensajeService.editar(empresaId, id, request.nombre(), request.contenido(),
                request.version()));
    }

    @Operation(summary = "Delete a message flow",
            description = "Also deletes its correlation key. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Message flow deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/mensajes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        Long empresaId = principal.empresaId();
        mensajeService.eliminar(empresaId, id);
        return ResponseEntity.noContent().build();
    }
}
