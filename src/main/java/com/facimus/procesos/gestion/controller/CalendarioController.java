package com.facimus.procesos.gestion.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.CalendarioRequest;
import com.facimus.procesos.gestion.dto.request.FestivoRequest;
import com.facimus.procesos.gestion.dto.response.CalendarioResponse;
import com.facimus.procesos.gestion.dto.response.EdicionFestivoResponse;
import com.facimus.procesos.gestion.dto.response.FestivoResponse;
import com.facimus.procesos.gestion.service.CalendarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * PR 68 (D35): el calendario laboral de la organizacion. Lo lee cualquiera, porque con el se cuentan los plazos que
 * todos ven; cambiarlo es gobierno de la tienda, del administrador.
 */
@Tag(name = "Business calendar", description = "Time zone, working hours, working days and public holidays of the "
        + "organization: what business time is counted with")
@RestController
@RequestMapping("/api/v1/organizacion/calendario")
@RequiredArgsConstructor
public class CalendarioController {

    private static final String FECHA = "Day, as yyyy-MM-dd";

    private final CalendarioService calendarioService;

    @Operation(summary = "Get the business calendar",
            description = "Until an administrator saves it, it is the default of the organization's country: the "
                    + "country follows the organization's language, the zone its settings, and the working day runs "
                    + "Monday to Friday from 08:00 to 17:00. That default comes without a version.")
    @ApiResponse(responseCode = "200", description = "The calendar")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<CalendarioResponse> obtener(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(calendarioService.obtener(principal.empresaId()));
    }

    @Operation(summary = "Change the business calendar",
            description = "Replaces it whole. The first time, send it without a version. Changing the country "
                    + "changes which holidays are computed; the organization's own changes to holidays stay. "
                    + "Timers already armed keep their time; the new calendar counts from the next one. "
                    + "Administrators only.")
    @ApiResponse(responseCode = "200", description = "The calendar")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping
    public ResponseEntity<CalendarioResponse> editar(@Validated @RequestBody CalendarioRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(calendarioService.editar(principal.empresaId(), principal.usuarioId(),
                new CalendarioService.Jornada(request.pais(), request.zonaHoraria(), request.horaInicio(),
                        request.horaFin(), request.diasHabiles()), request.version()));
    }

    @Operation(summary = "List the public holidays of a year",
            description = "The ones the country's law gives (Colombia: Ley 51 de 1983, with Easter; United States: "
                    + "federal, on the observed day; France: national; Australia: national, with the additional day "
                    + "when one falls on a weekend), with the organization's changes applied, in order.")
    @ApiResponse(responseCode = "200", description = "The holidays")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/festivos")
    public ResponseEntity<List<FestivoResponse>> festivos(
            @Parameter(description = "Year, from 1900 to 2199", example = "2026") @RequestParam int anio,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(calendarioService.festivos(principal.empresaId(), anio));
    }

    @Operation(summary = "List the organization's changes to the holidays",
            description = "Days it made holidays, holidays it renamed and holidays it removed, in order. Not paged: "
                    + "an organization changes at most 500 days.")
    @ApiResponse(responseCode = "200", description = "The changes")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/ediciones")
    public ResponseEntity<List<EdicionFestivoResponse>> ediciones(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(calendarioService.ediciones(principal.empresaId()));
    }

    @Operation(summary = "Make a day a holiday, or rename its holiday",
            description = "For a state holiday of Australia or the United States, a local one, or a day the "
                    + "organization closes. An organization changes at most 500 days. Administrators only.")
    @ApiResponse(responseCode = "200", description = "The holiday")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PutMapping("/festivos/{fecha}")
    public ResponseEntity<FestivoResponse> nombrar(
            @Parameter(description = FECHA, example = "2026-06-08") @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @Validated @RequestBody FestivoRequest request, @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(calendarioService.nombrarFestivo(principal.empresaId(), principal.usuarioId(), fecha,
                request.nombre()));
    }

    @Operation(summary = "Remove a holiday",
            description = "The day becomes a working day if it is one of the working days of the week. "
                    + "Administrators only.")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @DeleteMapping("/festivos/{fecha}")
    public ResponseEntity<Void> quitar(
            @Parameter(description = FECHA, example = "2026-08-17") @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal ApiPrincipal principal) {
        calendarioService.quitarFestivo(principal.empresaId(), principal.usuarioId(), fecha);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Undo the organization's change to a day",
            description = "The day goes back to what the country's law says. Administrators only.")
    @ApiResponse(responseCode = "204", description = "Undone")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @DeleteMapping("/ediciones/{fecha}")
    public ResponseEntity<Void> restaurar(
            @Parameter(description = FECHA, example = "2026-08-17") @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @AuthenticationPrincipal ApiPrincipal principal) {
        calendarioService.restaurarFestivo(principal.empresaId(), principal.usuarioId(), fecha);
        return ResponseEntity.noContent().build();
    }
}
