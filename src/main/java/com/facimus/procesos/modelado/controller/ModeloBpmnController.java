package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.Descargas;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.service.ModeloBpmn;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 53: el proceso en BPMN 2.0.2, para abrirlo en Camunda Modeler, bpmn.io u otra herramienta. */
@Tag(name = "Diagrams", description = "A whole BPMN diagram in one request, ready to draw")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModeloBpmnController {

    private final ModeloBpmnService modeloBpmnService;

    @Operation(summary = "Download a process as BPMN 2.0.2",
            description = "The process as a .bpmn file: a collaboration with a participant per pool and a message "
                    + "flow per message, a process per modeled pool with its lanes, tasks, gateways, events and "
                    + "sequence flows, and the diagram with its geometry. Conditions are formal expressions in the "
                    + "language urn:bpmn-process-manager:condiciones:1; what BPMN has no element for goes in "
                    + "extensionElements under urn:bpmn-process-manager:bpm:1. The owning store gets its draft; a "
                    + "store the process is shared with gets the version in force. Any role can download it.")
    @ApiResponse(responseCode = "200", description = "The process as a BPMN 2.0.2 file (application/xml)")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/procesos/{procesoId}/modelo")
    public ResponseEntity<byte[]> exportar(@PathVariable Long procesoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        ModeloBpmn modelo = modeloBpmnService.exportar(principal.empresaId(), procesoId);
        return Descargas.xml(modelo.archivo(), modelo.xml());
    }
}
