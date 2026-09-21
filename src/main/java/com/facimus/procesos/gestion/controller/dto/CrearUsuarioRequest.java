package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;

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
        @Schema(description = "Initial password, at least 6 characters", example = "secret123", format = "password")
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres.")
        @Size(max = 72, message = "La contrasena no puede superar 72 caracteres.") String password,
        @Schema(description = "Access role inside the store", example = "EDITOR")
        @NotNull(message = "Debe seleccionar un rol de acceso.") RolAcceso rolAcceso) {
}
