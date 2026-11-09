package com.facimus.procesos.gestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A new store and its first administrator")
public record RegistroEmpresaRequest(
        @Schema(example = "Acme Store")
        @NotBlank(message = "El nombre de la empresa es obligatorio.")
        @Size(max = 120, message = "El nombre de la empresa no puede superar 120 caracteres.") String nombreEmpresa,
        @Schema(description = "Colombian tax id (NIT), unique on the platform", example = "901234567-8")
        @NotBlank(message = "El NIT es obligatorio.")
        @Size(max = 20, message = "El NIT no puede superar 20 caracteres.") String nit,
        @Schema(example = "contact@acme.com")
        @NotBlank(message = "El correo de contacto es obligatorio.")
        @Email(message = "El correo de contacto no es valido.")
        @Size(max = 254, message = "El correo de contacto no puede superar 254 caracteres.") String correoContacto,
        @Schema(example = "Ana Torres")
        @NotBlank(message = "El nombre del administrador es obligatorio.")
        @Size(max = 120, message = "El nombre del administrador no puede superar 120 caracteres.") String nombreAdmin,
        @Schema(description = "The administrator's login; unique across all stores", example = "ana@acme.com")
        @NotBlank(message = "El correo del administrador es obligatorio.")
        @Email(message = "El correo del administrador no es valido.")
        @Size(max = 254, message = "El correo del administrador no puede superar 254 caracteres.") String emailAdmin,
        @Schema(description = "At least 15 characters, with no other rule about what they are: a few words make a good one. It cannot be a known leaked password, a repeated character or a series, or carry the name, the e-mail or the store", example = "harbor lights at dusk", format = "password")
        @NotBlank(message = "La contrasena es obligatoria.")
        @Size(min = 15, message = "La contraseña debe tener al menos 15 caracteres.")
        @Size(max = 72, message = "La contrasena no puede superar 72 caracteres.") String passwordAdmin) {
}
