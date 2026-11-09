package com.facimus.procesos.gestion.dto.request;

import com.facimus.procesos.common.model.RolAcceso;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "An e-mail domain of the store and the role of whoever joins from it")
public record DominioRequest(
        @Schema(example = "acme.com")
        @NotBlank(message = "El dominio es obligatorio.")
        @Size(max = 253, message = "El dominio no puede superar 253 caracteres.")
        @Pattern(regexp = "(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)++[a-z]{2,63}$",
                message = "El dominio no es válido: escríbelo como acme.com, sin @ ni https://.") String dominio,
        @Schema(example = "EDITOR")
        @NotNull(message = "El rol por defecto es obligatorio.") RolAcceso rolPorDefecto) {
}
