package com.facimus.procesos.modelado.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.api.Paginacion;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.modelado.dto.response.ResultadoDeBusquedaResponse;
import com.facimus.procesos.modelado.model.TipoDeResultado;
import com.facimus.procesos.modelado.service.BusquedaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * PR 65: la busqueda de la tienda. Cualquier rol busca, y encuentra solo lo que puede ver: no hay recurso en la ruta,
 * asi que el filtro de permisos no la decide; lo hace el servicio, con el mismo evaluador, en la misma consulta.
 */
@Tag(name = "Search", description = "Full-text search over the processes, elements, documents and comments a person "
        + "can view in the store")
@RestController
@RequestMapping("/api/v1/busqueda")
@RequiredArgsConstructor
public class BusquedaController {

    private final BusquedaService busquedaService;

    @Operation(summary = "Search the store",
            description = "Finds the text in processes (name, description, category, tags and documentation), "
                    + "elements of the draft and of the published version (name, description and documentation), "
                    + "documents (file name and the text read from the file once the antivirus cleared it) and "
                    + "comments that were not deleted, among what the person can view. The text follows web search "
                    + "syntax: \"quoted phrase\", -excluded, or. Accents and case do not matter, and words are "
                    + "matched by their stem in the store's language. Most relevant first, up to 50 a page; an empty "
                    + "text, or one of only common words, finds nothing. Each hit carries a fragment in plain-text "
                    + "segments, with the matching words highlighted.")
    @ApiResponse(responseCode = "200", description = "A page of hits, most relevant first")
    @ApiResponse(responseCode = "400", ref = "BadRequest")
    @ApiResponse(responseCode = "401", ref = "Unauthorized")
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
    @GetMapping
    public ResponseEntity<PageResponse<ResultadoDeBusquedaResponse>> buscar(
            @Parameter(description = "What to search for, up to 200 characters", example = "\"credit approval\" -card")
            @RequestParam(defaultValue = "") @Size(max = BusquedaService.LARGO_MAXIMO,
                    message = "La búsqueda no puede pasar de 200 caracteres.") String q,
            @Parameter(description = "Only hits of this kind: PROCESO, ELEMENTO, DOCUMENTO or COMENTARIO")
            @RequestParam(required = false) TipoDeResultado tipo,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = Paginacion.PAGINA_INVALIDA) int pagina,
            @Parameter(description = "Hits per page, from 1 to 50")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = Paginacion.TAMANO_INVALIDO)
            @Max(value = Paginacion.TAMANO_MAXIMO, message = Paginacion.TAMANO_INVALIDO) int tamano,
            @AuthenticationPrincipal ApiPrincipal principal) {
        return ResponseEntity.ok(busquedaService.buscar(
                new Actor(principal.usuarioId(), principal.empresaId(), principal.rol()), q, tipo,
                Paginacion.de(pagina, tamano)));
    }
}
