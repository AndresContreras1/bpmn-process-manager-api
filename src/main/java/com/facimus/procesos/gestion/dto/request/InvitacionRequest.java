package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.common.model.RolAcceso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Someone to invite by e-mail, and the access role they will have")
public record InvitacionRequest(
        @Schema(description = "Unique across all stores and case-insensitive", example = "luis@acme.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no es valido.")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.") String email,
        @Schema(description = "Access role inside the store", example = "EDITOR")
        @NotNull(message = "Debe seleccionar un rol de acceso.") RolAcceso rolAcceso) {
}
