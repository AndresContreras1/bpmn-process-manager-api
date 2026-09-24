package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Partial update of a user: send the name, the access role, the status or any of them")
public record ActualizarUsuarioRequest(
        @Schema(description = "New full name", example = "Luis Gomez Perez")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "New access role", example = "EDITOR") RolAcceso rolAcceso,
        @Schema(description = "false deactivates the user and true reactivates them", example = "true")
        Boolean activo,
        @Schema(description = "Version read with the last GET; if someone saved a change since, the edit answers 409",
                example = "0")
        @NotNull(message = "La versión es obligatoria.") Long version) {

    // Regla de validacion, no un campo del JSON: sin esto viajaria como "actualizacionPresente" y saldria en OpenAPI.
    @JsonIgnore
    @AssertTrue(message = "Debe enviar al menos nombre, rolAcceso o activo.")
    public boolean isActualizacionPresente() {
        return nombre != null || rolAcceso != null || activo != null;
    }
}
