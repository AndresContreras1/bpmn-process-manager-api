package com.facimus.procesos.gestion.dto.request;

import java.util.List;

import com.facimus.procesos.gestion.model.ModoDePublicacion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "How the store publishes its processes")
public record PoliticaDePublicacionRequest(
        @Schema(description = "DIRECTA or CON_APROBACION", example = "CON_APROBACION")
        @NotNull(message = "El modo de publicación es obligatorio.") ModoDePublicacion modo,
        @Schema(description = "Approvals a request needs, from 1 to 10; 1 if left out", example = "2")
        @Min(value = 1, message = "Una solicitud necesita de 1 a 10 aprobaciones.")
        @Max(value = 10, message = "Una solicitud necesita de 1 a 10 aprobaciones.") Integer aprobacionesNecesarias,
        @Schema(description = "The process role whose people approve; leave it out when a list approves",
                example = "3") Long rolProcesoId,
        @Schema(description = "The people who approve, up to 50; leave it empty when a role approves",
                example = "[4, 5]")
        @Size(max = 50, message = "La lista de aprobadores no puede tener más de 50 personas.")
        List<@NotNull(message = "Un aprobador no puede ir vacío.") Long> aprobadores,
        @Schema(description = "Version read with the last GET; leave it out while the store never saved one",
                example = "0") Long version) {
}
