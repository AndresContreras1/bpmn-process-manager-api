package com.facimus.procesos.modelado.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.CaracterizacionRequest;
import com.facimus.procesos.modelado.dto.request.DocumentacionRequest;
import com.facimus.procesos.modelado.dto.request.VistaPreviaRequest;
import com.facimus.procesos.modelado.dto.response.CaracterizacionResponse;
import com.facimus.procesos.modelado.dto.response.DocumentacionResponse;
import com.facimus.procesos.modelado.dto.response.VistaPreviaResponse;
import com.facimus.procesos.modelado.service.DatosDeCaracterizacion;
import com.facimus.procesos.modelado.service.DocumentacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 62: la documentacion de un proceso y de sus elementos, y su ficha de caracterizacion. */
@Tag(name = "Documentation", description = "Markdown documentation of a process and its elements, shown as cleaned "
        + "HTML, and its characterization sheet")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentacionController {

    private final DocumentacionService documentacionService;

    @Operation(summary = "List the documentation of a process",
            description = "The text of the whole process (bpmnId empty) and of each element that has one.")
    @ApiResponse(responseCode = "200", description = "The documentation")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/documentacion")
    public ResponseEntity<List<DocumentacionResponse>> listar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentacionService.listar(principal.empresaId(), procesoId));
    }

    @Operation(summary = "Write the documentation of a process or element",
            description = "In Markdown. It is converted on the server and cleaned against a whitelist: HTML written "
                    + "inside the Markdown comes out as text, and there are no images, styles or scripts. An empty "
                    + "text removes it. Send the version read, or none for a new one.")
    @ApiResponse(responseCode = "200", description = "The documentation as it was saved")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/procesos/{procesoId}/documentacion")
    public ResponseEntity<DocumentacionResponse> guardar(@PathVariable Long procesoId,
            @Validated @RequestBody DocumentacionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentacionService.guardar(principal.empresaId(), principal.usuarioId(), procesoId,
                request.elemento(), request.markdown(), request.version()));
    }

    @Operation(summary = "Preview Markdown as the cleaned HTML that would be saved")
    @ApiResponse(responseCode = "200", description = "The cleaned HTML")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @PostMapping("/documentacion/vista-previa")
    public ResponseEntity<VistaPreviaResponse> vistaPrevia(@Validated @RequestBody VistaPreviaRequest request) {
        return ResponseEntity.ok(documentacionService.vistaPrevia(request.markdown()));
    }

    @Operation(summary = "Get the characterization sheet of a process",
            description = "Objective, scope, inputs, outputs, suppliers, customers, indicators, risks and regulation. "
                    + "While nobody wrote it, every field and the version are empty.")
    @ApiResponse(responseCode = "200", description = "The sheet")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/caracterizacion")
    public ResponseEntity<CaracterizacionResponse> caracterizacion(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentacionService.caracterizacion(principal.empresaId(), procesoId));
    }

    @Operation(summary = "Write the characterization sheet of a process",
            description = "The whole sheet at once, with the version read, or none the first time.")
    @ApiResponse(responseCode = "200", description = "The sheet as it was saved")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/procesos/{procesoId}/caracterizacion")
    public ResponseEntity<CaracterizacionResponse> guardarCaracterizacion(@PathVariable Long procesoId,
            @Validated @RequestBody CaracterizacionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(documentacionService.guardarCaracterizacion(principal.empresaId(),
                principal.usuarioId(), procesoId, new DatosDeCaracterizacion(request.objetivo(), request.alcance(),
                        request.entradas(), request.salidas(), request.proveedores(), request.clientes(),
                        request.indicadores(), request.riesgos(), request.normativa(), request.version())));
    }
}
