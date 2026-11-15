package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.modelado.dto.request.AsignacionRequest;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.service.ReglaDeAsignacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 70: a quien le llega una tarea de usuario del borrador. */
@Tag(name = "Assignment", description = "Who a user task goes to when a case reaches it")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AsignacionController {

    private final ReglaDeAsignacionService reglaDeAsignacionService;

    @Operation(summary = "Save who a user task goes to",
            description = "ROL leaves it in the tray of its lane's role, as before, on any activity. On a user task, "
                    + "PERSONA gives it to a fixed person (an active administrator or editor), INICIADOR to whoever "
                    + "opened the case and CAMPO to the person a case variable names, the one a PERSONA field of an "
                    + "earlier form wrote. It is frozen with the version when it is published; when a case cannot "
                    + "tell the person, or that person can no longer complete tasks, the task waits in the role tray "
                    + "and the case timeline says why. A wrong person or variable answers 400 with the property in "
                    + "errors. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "The activity, with its assignment")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/actividades/{id}/asignacion")
    public ResponseEntity<ActividadResponse> deLaActividad(@PathVariable Long id,
            @Validated @RequestBody AsignacionRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(reglaDeAsignacionService.deLaActividad(principal.empresaId(), principal.usuarioId(),
                id, request.tipo(), request.usuarioId(), request.campo(), request.version()));
    }
}
