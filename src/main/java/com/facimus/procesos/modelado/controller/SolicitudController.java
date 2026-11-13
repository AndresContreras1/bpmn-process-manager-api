package com.facimus.procesos.modelado.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.DecisionRequest;
import com.facimus.procesos.modelado.dto.request.SolicitudRequest;
import com.facimus.procesos.modelado.dto.response.SolicitudResponse;
import com.facimus.procesos.modelado.model.Decision;
import com.facimus.procesos.modelado.model.EstadoSolicitud;
import com.facimus.procesos.modelado.service.SolicitudService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * PR 64: las solicitudes de revision. Pedir una pide {@code PUBLICAR} sobre el proceso; decidir, {@code APROBAR} y ser
 * uno de sus aprobadores; leerlas, {@code VER}. Lo decide la regla de recursos con el evaluador de permisos (D31).
 */
@Tag(name = "Review and approval", description = "Publishing through a review: the store's policy, the requests to "
        + "approve and the periodic recertification of each process")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SolicitudController {

    private static final String DECISION = "One decision per approver, while the request is open; whoever asked "
            + "never decides. The text is Markdown, cleaned like comments, up to 10 000 characters. ";

    private final SolicitudService solicitudService;

    @Operation(summary = "Ask for a review before publishing",
            description = "Only when the store publishes with approval. The draft is frozen in a draft revision and "
                    + "in the snapshot that the last approval publishes, so editing the draft afterwards does not "
                    + "change what is reviewed. Same conditions as publishing: no diagnosis errors and something "
                    + "different from the version in force; one open request per process; and at least as many "
                    + "people who can approve the process as the policy asks, without counting who asks. The "
                    + "approvers are fixed now and notified. The answer carries the difference against the version "
                    + "in force.")
    @ApiResponse(responseCode = "201", description = "The open request",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL of the request"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/procesos/{procesoId}/solicitudes")
    public ResponseEntity<SolicitudResponse> pedir(@PathVariable Long procesoId,
            @Validated @RequestBody(required = false) SolicitudRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        SolicitudResponse solicitud = solicitudService.pedir(principal.empresaId(), principal.usuarioId(), procesoId,
                request == null ? null : request.markdown());
        return ResponseEntity.created(URI.create("/api/v1/solicitudes/" + solicitud.id())).body(solicitud);
    }

    @Operation(summary = "List the review requests of a process",
            description = "Newest first, up to 50 a page, without the difference.")
    @ApiResponse(responseCode = "200", description = "A page of requests")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/solicitudes")
    public ResponseEntity<PageResponse<SolicitudResponse>> listarDelProceso(@PathVariable Long procesoId,
            @Parameter(description = "Only requests in this state", example = "ABIERTA")
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(solicitudService.listarDelProceso(principal.empresaId(), procesoId, estado,
                Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "List the store's review requests",
            description = "Those of the processes the person can view, newest first, up to 50 a page, without the "
                    + "difference. With pendientesDeMi=true, only the open ones waiting for the person's decision: "
                    + "the inbox of requests to approve.")
    @ApiResponse(responseCode = "200", description = "A page of requests")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/solicitudes")
    public ResponseEntity<PageResponse<SolicitudResponse>> listar(
            @Parameter(description = "Only the open ones waiting for my decision", example = "true")
            @RequestParam(defaultValue = "false") boolean pendientesDeMi,
            @Parameter(description = "Only requests in this state", example = "ABIERTA")
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(solicitudService.listar(principal.empresaId(), principal.usuarioId(),
                principal.rol(), new SolicitudService.Filtro(pendientesDeMi, estado), Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Get a review request",
            description = "With its approvers, their decisions and the difference against the version in force when "
                    + "it was asked for (the same comparison as between two versions).")
    @ApiResponse(responseCode = "200", description = "The request")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/solicitudes/{id}")
    public ResponseEntity<SolicitudResponse> obtener(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(solicitudService.obtener(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Approve a review request",
            description = DECISION + "The approval that completes the ones the request needs publishes the frozen "
                    + "snapshot through the usual publishing path; if it can no longer be published (the version in "
                    + "force changed, or the diagnosis is no longer clean) the request ends APROBADA_SIN_PUBLICAR "
                    + "and says why. The requester is notified.")
    @ApiResponse(responseCode = "200", description = "The request after the decision")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/solicitudes/{id}/aprobar")
    public ResponseEntity<SolicitudResponse> aprobar(@PathVariable Long id,
            @Validated @RequestBody(required = false) DecisionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(decidir(id, Decision.APROBADA, request, principal));
    }

    @Operation(summary = "Ask for changes in a review request",
            description = DECISION + "It needs the text, closes the request and notifies the requester, who asks "
                    + "again when the draft is ready.")
    @ApiResponse(responseCode = "200", description = "The closed request")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/solicitudes/{id}/pedir-cambios")
    public ResponseEntity<SolicitudResponse> pedirCambios(@PathVariable Long id,
            @Validated @RequestBody(required = false) DecisionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(decidir(id, Decision.CAMBIOS_PEDIDOS, request, principal));
    }

    @Operation(summary = "Reject a review request",
            description = DECISION + "It needs the text, closes the request and notifies the requester.")
    @ApiResponse(responseCode = "200", description = "The closed request")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<SolicitudResponse> rechazar(@PathVariable Long id,
            @Validated @RequestBody(required = false) DecisionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(decidir(id, Decision.RECHAZADA, request, principal));
    }

    @Operation(summary = "Withdraw a review request",
            description = "While it is open, by whoever asked for it, or by whoever can administer the process when "
                    + "the requester is no longer there to follow it.")
    @ApiResponse(responseCode = "200", description = "The withdrawn request")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/solicitudes/{id}/retirar")
    public ResponseEntity<SolicitudResponse> retirar(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(solicitudService.retirar(principal.empresaId(), principal.usuarioId(), id));
    }

    private SolicitudResponse decidir(Long id, Decision decision, DecisionRequest request, ApiPrincipal principal) {
        return solicitudService.decidir(principal.empresaId(), principal.usuarioId(), id, decision,
                request == null ? null : request.markdown());
    }
}
