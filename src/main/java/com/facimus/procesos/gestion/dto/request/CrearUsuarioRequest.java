package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.common.model.RolAcceso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New collaborator of the store")
public record CrearUsuarioRequest(
        @Schema(description = "Full name", example = "Luis Gomez")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres.") String nombre,
        @Schema(description = "Login email; unique across all stores and case-insensitive", example = "luis@acme.com")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no es valido.")
        @Size(max = 254, message = "El correo no puede superar 254 caracteres.") String email,
        @Schema(description = "Initial password. At least 15 characters, with no other rule about what they are: a few words make a good one. It cannot be a known leaked password, a repeated character or a series, or carry the name, the e-mail or the store. Leaving it out generates a temporary one, "
                + "which the answer carries once in claveTemporal and the user has to change before doing anything "
                + "else", example = "harbor lights at dusk", format = "password")
        @Size(min = 15, message = "La contraseña debe tener al menos 15 caracteres.")
        @Size(max = 72, message = "La contrasena no puede superar 72 caracteres.") String password,
        @Schema(description = "Access role inside the store", example = "EDITOR")
        @NotNull(message = "Debe seleccionar un rol de acceso.") RolAcceso rolAcceso) {
}
