package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio.") String nombre,
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no es valido.") String email,
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres.") String password,
        @NotNull(message = "Debe seleccionar un rol de acceso.") RolAcceso rolAcceso) {
}
