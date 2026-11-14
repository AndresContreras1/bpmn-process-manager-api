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
import com.facimus.procesos.modelado.dto.request.FormularioRequest;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.service.FormularioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** PR 69: el formulario tipado de una actividad de usuario o de un evento de inicio del borrador. */
@Tag(name = "Forms", description = "Typed forms of user tasks and start events: fields, validations and conditions")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FormularioController {

    private static final String DESCRIPCION = "Replaces the whole form; an empty list of fields removes it. Field "
            + "types: TEXTO, TEXTO_LARGO, NUMERO, MONEDA, FECHA, FECHA_HORA, SI_NO, LISTA, LISTA_MULTIPLE, PERSONA, "
            + "ARCHIVO and SECCION. Patterns are RE2 expressions; visibleSi and requeridoSi are written in the "
            + "condition grammar of the gateways and read the fields above and the case variables. The answer brings "
            + "each condition compiled (visibleSiArbol, requeridoSiArbol) for the web to evaluate. A form that is not "
            + "well built answers 400 with each property in errors. Administrators and editors.";

    private final FormularioService formularioService;

    @Operation(summary = "Save the form of a user task", description = DESCRIPCION)
    @ApiResponse(responseCode = "200", description = "The activity, with its form")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/actividades/{id}/formulario")
    public ResponseEntity<ActividadResponse> deLaActividad(@PathVariable Long id,
            @Validated @RequestBody FormularioRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(formularioService.deLaActividad(principal.empresaId(), principal.usuarioId(), id,
                request.formulario(), request.version()));
    }

    @Operation(summary = "Save the form of a start event", description = DESCRIPCION)
    @ApiResponse(responseCode = "200", description = "The event, with its form")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/eventos/{id}/formulario")
    public ResponseEntity<EventoResponse> delEvento(@PathVariable Long id,
            @Validated @RequestBody FormularioRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(formularioService.delEvento(principal.empresaId(), principal.usuarioId(), id,
                request.formulario(), request.version()));
    }
}
