package com.facimus.procesos.gestion.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A page of the audit trail, newest first")
public record PaginaDeBitacoraResponse(
        List<RegistroDeBitacoraResponse> registros,
        @Schema(description = "Pass it as antes for the next page; empty on the last one", example = "17")
        Long siguiente) {
}
