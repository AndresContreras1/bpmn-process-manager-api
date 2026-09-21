package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

@Schema(description = "Partial update of a user: send the access role, the status or both")
public record ActualizarUsuarioRequest(
        @Schema(description = "New access role", example = "EDITOR") RolAcceso rolAcceso,
        @Schema(description = "false deactivates the user and true reactivates them", example = "true")
        Boolean activo) {

    // Regla de validacion, no un campo del JSON: sin esto viajaria como "actualizacionPresente" y saldria en OpenAPI.
    @JsonIgnore
    @AssertTrue(message = "Debe enviar al menos rolAcceso o activo.")
    public boolean isActualizacionPresente() {
        return rolAcceso != null || activo != null;
    }
}
