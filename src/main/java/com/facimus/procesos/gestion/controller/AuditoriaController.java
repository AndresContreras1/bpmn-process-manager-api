package com.facimus.procesos.gestion.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.response.PaginaDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.RegistroDeBitacoraResponse;
import com.facimus.procesos.gestion.dto.response.VerificacionDeBitacoraResponse;
import com.facimus.procesos.gestion.service.BitacoraService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.json.JsonMapper;

/** PR 31: la bitacora de la tienda, para su administrador: leerla, exportarla y comprobar que nadie la toco. */
@Tag(name = "Audit trail", description = "What happened in the store, chained so that changing or deleting an entry "
        + "shows. Administrators only")
@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {

    /** Lo mas que entra en una exportacion: para mas, se acota por fechas. */
    static final int TOPE_DE_EXPORTACION = 10_000;

    private static final int TAMANO_MAXIMO = 50;

    private final BitacoraService bitacoraService;
    private final JsonMapper json;

    public AuditoriaController(BitacoraService bitacoraService, JsonMapper json) {
        this.bitacoraService = bitacoraService;
        this.json = json;
    }

    @Operation(summary = "Read the audit trail", description = "Newest first, up to 50 a page. The next page is asked "
            + "with antes set to the siguiente of this one. Every filter is optional; desde and hasta are in UTC.")
    @ApiResponse(responseCode = "200", description = "A page of entries")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping
    public ResponseEntity<PaginaDeBitacoraResponse> buscar(@AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(required = false) String accion, @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) Long antes, @RequestParam(defaultValue = "20") int tamano) {
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new SolicitudInvalidaException("El tamaño de página va de 1 a " + TAMANO_MAXIMO + ".");
        }
        return ResponseEntity.ok(bitacoraService.buscar(principal.empresaId(),
                new BitacoraService.Filtro(accion, actorId, desde, hasta), antes, tamano));
    }

    @Operation(summary = "Export the audit trail", description = "Oldest first, as CSV or JSON, with the same filters, "
            + "up to 10,000 entries. In the CSV, a cell that a spreadsheet would run as a formula starts with an "
            + "apostrophe.")
    @ApiResponse(responseCode = "200", description = "The file")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping("/exportacion")
    public ResponseEntity<String> exportar(@AuthenticationPrincipal ApiPrincipal principal,
            @RequestParam(defaultValue = "csv") String formato, @RequestParam(required = false) String accion,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        List<RegistroDeBitacoraResponse> registros = bitacoraService.exportar(principal.empresaId(),
                new BitacoraService.Filtro(accion, actorId, desde, hasta), TOPE_DE_EXPORTACION);
        return switch (formato) {
            case "csv" -> archivo(csv(registros), new MediaType("text", "csv"), "bitacora.csv");
            case "json" -> archivo(json.writeValueAsString(registros), MediaType.APPLICATION_JSON, "bitacora.json");
            default -> throw new SolicitudInvalidaException("El formato es csv o json.");
        };
    }

    @Operation(summary = "Verify the audit trail", description = "Recomputes the store's chain from its first entry "
            + "and says where it breaks: an entry changed, deleted or put out of order.")
    @ApiResponse(responseCode = "200", description = "Whether the chain is whole, and where it breaks if not")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "403", ref = "Forbidden")
    @GetMapping("/verificacion")
    public ResponseEntity<VerificacionDeBitacoraResponse> verificar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(bitacoraService.verificar(principal.empresaId()));
    }

    private static ResponseEntity<String> archivo(String contenido, MediaType tipo, String nombre) {
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build()
                        .toString())
                .body(contenido);
    }

    static String csv(List<RegistroDeBitacoraResponse> registros) {
        String cabecera = "secuencia,momento,actor_tipo,actor_id,accion,recurso,recurso_id,exito,ip,user_agent,"
                + "request_id,trace_id,detalle,hash";
        return Stream.concat(Stream.of(cabecera), registros.stream().map(registro -> Stream.of(
                        registro.secuencia(), registro.momento(), registro.actorTipo(), registro.actorId(),
                        registro.accion(), registro.recurso(), registro.recursoId(), registro.exito(), registro.ip(),
                        registro.userAgent(), registro.requestId(), registro.traceId(), registro.detalle(),
                        registro.hash())
                        .map(AuditoriaController::celda)
                        .collect(Collectors.joining(","))))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    /**
     * Una celda de CSV (RFC 4180) que una hoja de calculo no ejecuta: lo que empieza por =, +, -, @, tabulador o
     * retorno se antepone con un apostrofo (la recomendacion de OWASP), y lo que lleva comas, comillas o saltos va
     * entre comillas.
     */
    static String celda(Object valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.toString();
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
