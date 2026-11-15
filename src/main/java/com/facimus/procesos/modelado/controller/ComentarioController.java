package com.facimus.procesos.modelado.controller;

import java.net.URI;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.ComentarioRequest;
import com.facimus.procesos.modelado.dto.request.EdicionDeComentarioRequest;
import com.facimus.procesos.modelado.dto.request.RespuestaRequest;
import com.facimus.procesos.modelado.dto.response.ComentarioResponse;
import com.facimus.procesos.modelado.dto.response.HiloResponse;
import com.facimus.procesos.modelado.model.EstadoHilo;
import com.facimus.procesos.modelado.service.ComentarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * PR 63: los comentarios de un proceso, en hilos. Cualquier rol comenta, tambien quien solo lee: lo decide la accion
 * {@code COMENTAR} sobre el proceso, y una concesion puede reservarla.
 */
@Tag(name = "Comments", description = "Comment threads on a process, one of its elements or a published version, "
        + "with mentions, replies, and threads that are resolved and reopened")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ComentarioController {

    private static final String CUERPO = "The text is Markdown, converted on the server and cleaned against the same "
            + "whitelist as the documentation, up to 10 000 characters. Mentions are ids of people of the store who "
            + "can view the process, up to 20; any other id answers 400 and says which one, with the same reason for "
            + "all. ";

    private final ComentarioService comentarioService;

    @Operation(summary = "List the comment threads of a process",
            description = "Newest thread first, each with its replies oldest first, up to 50 threads a page. "
                    + "Filter by element (bpmnId), published version and state. A deleted first comment keeps its "
                    + "thread while it has replies.")
    @ApiResponse(responseCode = "200", description = "A page of threads")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/comentarios")
    public ResponseEntity<PageResponse<HiloResponse>> listar(@PathVariable Long procesoId,
            @Parameter(description = "Only threads on this element", example = "Activity_0b3k9xq")
            @RequestParam(required = false) String bpmnId,
            @Parameter(description = "Only threads on this published version", example = "2")
            @RequestParam(required = false) Integer version,
            @Parameter(description = "ABIERTO or RESUELTO", example = "ABIERTO")
            @RequestParam(required = false) EstadoHilo estado,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(comentarioService.listar(principal.empresaId(), procesoId,
                new ComentarioService.Filtro(bpmnId, version, estado), Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Start a comment thread",
            description = CUERPO + "With bpmnId the thread is about that element of the draft, or with "
                    + "versionDelProceso about that published version and an element of it. Those mentioned are "
                    + "notified.")
    @ApiResponse(responseCode = "201", description = "The first comment of the new thread",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the new comment"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/procesos/{procesoId}/comentarios")
    public ResponseEntity<ComentarioResponse> comentar(@PathVariable Long procesoId,
            @Validated @RequestBody ComentarioRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        ComentarioResponse comentario = comentarioService.comentar(principal.empresaId(), principal.usuarioId(),
                procesoId, new ComentarioService.Nuevo(request.markdown(), request.bpmnId(),
                        request.versionDelProceso(), request.menciones()));
        return ResponseEntity.created(URI.create("/api/v1/comentarios/" + comentario.id())).body(comentario);
    }

    @Operation(summary = "Reply in a thread",
            description = CUERPO + "Replying to a reply answers in its thread. Those mentioned are notified, and "
                    + "so is everyone else who wrote in the thread.")
    @ApiResponse(responseCode = "201", description = "The reply",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the reply"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/comentarios/{id}/respuestas")
    public ResponseEntity<ComentarioResponse> responder(@PathVariable Long id,
            @Validated @RequestBody RespuestaRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        ComentarioResponse respuesta = comentarioService.responder(principal.empresaId(), principal.usuarioId(), id,
                request.markdown(), request.menciones());
        return ResponseEntity.created(URI.create("/api/v1/comentarios/" + respuesta.id())).body(respuesta);
    }

    @Operation(summary = "Edit one's own comment",
            description = CUERPO + "Only its author edits it, with the version read. Only the people newly "
                    + "mentioned are notified.")
    @ApiResponse(responseCode = "200", description = "The comment as it was saved")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/comentarios/{id}")
    public ResponseEntity<ComentarioResponse> editar(@PathVariable Long id,
            @Validated @RequestBody EdicionDeComentarioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(comentarioService.editar(principal.empresaId(), principal.usuarioId(), id,
                request.markdown(), request.menciones(), request.version()));
    }

    @Operation(summary = "Resolve a thread",
            description = "From its first comment. Everyone else who wrote in it is notified, and it is audited.")
    @ApiResponse(responseCode = "200", description = "The first comment, resolved")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/comentarios/{id}/resolver")
    public ResponseEntity<ComentarioResponse> resolver(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(comentarioService.resolver(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Reopen a resolved thread", description = "From its first comment. It is audited.")
    @ApiResponse(responseCode = "200", description = "The first comment, open again")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/comentarios/{id}/reabrir")
    public ResponseEntity<ComentarioResponse> reabrir(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(comentarioService.reabrir(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Delete a comment",
            description = "Its author or an administrator. It loses its text; a first comment with replies keeps "
                    + "its thread. It is audited.")
    @ApiResponse(responseCode = "204", description = "Comment deleted")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/comentarios/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal ApiPrincipal principal) {
        comentarioService.eliminar(principal.empresaId(), principal.usuarioId(), principal.rol(), id);
        return ResponseEntity.noContent().build();
    }
}
