package com.facimus.procesos.gestion.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A registered store")
public record EmpresaResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Acme Store") String nombre,
        @Schema(description = "Colombian tax id (NIT)", example = "901234567-8") String nit,
        @Schema(example = "contact@acme.com") String correoContacto,
        @Schema(example = "2026-09-21") LocalDate fechaRegistro,
        @Schema(description = "When an administrator asked to close the store; empty while it is open",
                example = "2026-10-06T09:30:00") LocalDateTime bajaSolicitadaEn,
        @Schema(description = "When every row of the closing store will be deleted, 30 days after the request; "
                + "until then it is read-only and the closing can be cancelled", example = "2026-11-05T09:30:00")
        LocalDateTime borradoProgramadoPara) {
}
