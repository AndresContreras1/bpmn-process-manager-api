package com.facimus.procesos.ejecucion.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.ejecucion.dto.request.MensajeEntranteRequest;
import com.facimus.procesos.ejecucion.dto.response.MensajeEntranteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajeSalienteResponse;
import com.facimus.procesos.ejecucion.dto.response.MensajesDelCasoResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.ResultadoCorrelacion;
import com.facimus.procesos.ejecucion.service.DatosDelEntrante;
import com.facimus.procesos.ejecucion.service.MensajeriaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/** Las dos bandejas de una tienda: lo que sus procesos mandan y lo que les llega. */
@Tag(name = "Messaging", description = "What the processes send to other participants and what reaches them back")
@RestController
@RequiredArgsConstructor
public class MensajeriaController {

    /** Lo que se responde cuando el mismo mensaje ya habia entrado con esa misma clave externa. */
    private static final String REPETIDO = "Idempotent-Replayed";

    private final MensajeriaService mensajeriaService;

    @Operation(summary = "Send a message to the process",
            description = "The message the process was waiting for, or the one that opens a case. It is matched to "
                    + "a case by its key: the one given here, or the correlation field taken from the body. The "
                    + "answer says what was done with it, and sending it twice with the same claveExterna answers "
                    + "the first time again, marked with Idempotent-Replayed. Administrators and editors.")
    @ApiResponse(responseCode = "200", description = "What was done with the message",
            headers = @Header(name = REPETIDO, description = "Present when this message had already been received"))
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @ApiResponse(responseCode = "409", ref = "Conflict")
    @PostMapping("/api/v1/procesos/{procesoId}/mensajes-entrantes")
    public ResponseEntity<MensajeEntranteResponse> recibir(@PathVariable Long procesoId,
            @Validated @RequestBody MensajeEntranteRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        MensajeEntranteResponse entrante = mensajeriaService.recibir(principal.empresaId(), procesoId,
                DatosDelEntrante.aMano(request.nombre(), request.clave(), request.cuerpo(),
                        request.claveExterna()));
        HttpHeaders cabeceras = new HttpHeaders();
        if (entrante.repetido()) {
            cabeceras.add(REPETIDO, "true");
        }
        return ResponseEntity.ok().headers(cabeceras).body(entrante);
    }

    @Operation(summary = "The outbox of a process",
            description = "What its cases have sent, oldest first. A message waits here until the clock reaches "
                    + "its delivery tick. Any role.")
    @ApiResponse(responseCode = "200", description = "One page of sent messages")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/api/v1/procesos/{procesoId}/bandeja-salida")
    public ResponseEntity<PageResponse<MensajeSalienteResponse>> bandejaDeSalida(@PathVariable Long procesoId,
            @Parameter(description = "Only the ones in this state") @RequestParam(required = false)
            EstadoMensajeSaliente estado,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(mensajeriaService.bandejaDeSalida(principal.empresaId(), procesoId, estado,
                Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "The inbox of a process",
            description = "What has reached it and what was done with each one: delivered to a case, opened a new "
                    + "one, still waiting for somebody to expect it, or discarded. Any role.")
    @ApiResponse(responseCode = "200", description = "One page of received messages")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/api/v1/procesos/{procesoId}/bandeja-entrada")
    public ResponseEntity<PageResponse<MensajeEntranteResponse>> bandejaDeEntrada(@PathVariable Long procesoId,
            @Parameter(description = "Only the ones that ended this way") @RequestParam(required = false)
            ResultadoCorrelacion resultado,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Items per page, from 1 to 50")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(mensajeriaService.bandejaDeEntrada(principal.empresaId(), procesoId, resultado,
                Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "The messages of one case",
            description = "What this case sent and what reached it, which is what explains a case that is waiting. "
                    + "Any role.")
    @ApiResponse(responseCode = "200", description = "The messages of the case")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @GetMapping("/api/v1/casos/{casoId}/mensajes")
    public ResponseEntity<MensajesDelCasoResponse> mensajesDelCaso(@PathVariable Long casoId,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(new MensajesDelCasoResponse(
                mensajeriaService.salientesDelCaso(principal.empresaId(), casoId),
                mensajeriaService.entrantesDelCaso(principal.empresaId(), casoId)));
    }
}
