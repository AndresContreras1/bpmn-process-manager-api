package com.facimus.procesos.gestion.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Los roles de proceso de una persona, la lista entera. Se reemplaza lo que tenia: mandar una lista vacia la deja
 * sin ninguno, que es como se quita el ultimo.
 */
@Schema(description = "The whole list of process roles the user belongs to")
public record RolesDeUsuarioRequest(
        @Schema(description = "Ids of the process roles; an empty list leaves the user with none",
                example = "[2, 3]")
        @NotNull(message = "La lista de roles es obligatoria.") List<Long> rolesProcesoIds) {
}
