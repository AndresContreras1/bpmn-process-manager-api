package com.facimus.procesos.gestion.controller;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.dto.request.PreferenciasDeNotificacionRequest;
import com.facimus.procesos.gestion.dto.response.ConteoDeNotificacionesResponse;
import com.facimus.procesos.gestion.dto.response.NotificacionResponse;
import com.facimus.procesos.gestion.dto.response.PreferenciaDeNotificacionResponse;
import com.facimus.procesos.gestion.service.NotificacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * PR 63: el centro de notificaciones de quien entro. Cada ruta es de la persona y de nadie mas: una notificacion de
 * otra responde 404, sea de su tienda o de otra.
 */
@Tag(name = "Notifications", description = "The notification center of the signed-in person: mentions, replies and "
        + "resolved threads, live over server-sent events, and how each type arrives by e-mail")
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @Operation(summary = "List your notifications", description = "Newest first, up to 50 a page. With noLeidas, only "
            + "the unread ones.")
    @ApiResponse(responseCode = "200", description = "A page of notifications")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping
    public ResponseEntity<PageResponse<NotificacionResponse>> listar(
            @Parameter(description = "Only the unread ones", example = "true")
            @RequestParam(defaultValue = "false") boolean noLeidas,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(notificacionService.listar(principal.empresaId(), principal.usuarioId(), noLeidas,
                Paginacion.de(pagina, tamano)));
    }

    @Operation(summary = "Count your unread notifications")
    @ApiResponse(responseCode = "200", description = "How many are unread")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/conteo")
    public ResponseEntity<ConteoDeNotificacionesResponse> contar(@AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(notificacionService.contar(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Mark a notification as read")
    @ApiResponse(responseCode = "200", description = "The notification, read")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "404", ref = "NotFound")
    @PostMapping("/{id}/leida")
    public ResponseEntity<NotificacionResponse> marcarLeida(@PathVariable Long id,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(notificacionService.marcarLeida(principal.empresaId(), principal.usuarioId(), id));
    }

    @Operation(summary = "Mark all your notifications as read")
    @ApiResponse(responseCode = "200", description = "How many are left unread: none")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PostMapping("/leidas")
    public ResponseEntity<ConteoDeNotificacionesResponse> marcarTodasLeidas(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(notificacionService.marcarTodasLeidas(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Follow your notifications",
            description = "Server-sent events: conteo with the unread count when it opens and whenever it changes, "
                    + "and notificacion with each new one, with a heartbeat comment every 25 seconds. At most five "
                    + "streams per person on each instance.")
    @ApiResponse(responseCode = "200", description = "The event stream (text/event-stream)", content = @Content)
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @GetMapping("/flujo")
    public SseEmitter flujo(@AuthenticationPrincipal ApiPrincipal principal, HttpServletResponse respuesta) {
        // Sin esto, un proxy guardaria los eventos hasta juntar un bloque
        respuesta.setHeader("X-Accel-Buffering", "no");
        return notificacionService.conectar(principal.empresaId(), principal.usuarioId());
    }

    @Operation(summary = "See how each type of notification reaches you by e-mail",
            description = "Every type, with INMEDIATO, RESUMEN_DIARIO or NUNCA. In the app they always arrive.")
    @ApiResponse(responseCode = "200", description = "One per type")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @GetMapping("/preferencias")
    public ResponseEntity<List<PreferenciaDeNotificacionResponse>> preferencias(
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(notificacionService.preferencias(principal.empresaId(), principal.usuarioId()));
    }

    @Operation(summary = "Choose how each type of notification reaches you by e-mail",
            description = "The types left out keep what they had.")
    @ApiResponse(responseCode = "200", description = "Every type, as it is now")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @PutMapping("/preferencias")
    public ResponseEntity<List<PreferenciaDeNotificacionResponse>> guardarPreferencias(
            @Validated @RequestBody PreferenciasDeNotificacionRequest request,
            @AuthenticationPrincipal ApiPrincipal principal) {
        Map<TipoDeNotificacion, EnvioPorCorreo> preferencias = new EnumMap<>(TipoDeNotificacion.class);
        request.preferencias().forEach(una -> preferencias.put(una.tipo(), una.envio()));
        return ResponseEntity.ok(notificacionService.guardarPreferencias(principal.empresaId(), principal.usuarioId(),
                preferencias));
    }
}
