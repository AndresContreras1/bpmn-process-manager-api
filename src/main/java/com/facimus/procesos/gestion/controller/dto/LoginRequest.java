package com.facimus.procesos.gestion.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio.") String email,
        @NotBlank(message = "La contrasena es obligatoria.") String password) {
}
