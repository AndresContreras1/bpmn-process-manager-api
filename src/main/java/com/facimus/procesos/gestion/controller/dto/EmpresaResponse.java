package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDate;

import com.facimus.procesos.gestion.model.Empresa;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A registered store")
public record EmpresaResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "Acme Store") String nombre,
        @Schema(description = "Colombian tax id (NIT)", example = "901234567-8") String nit,
        @Schema(example = "contact@acme.com") String correoContacto,
        @Schema(example = "2026-09-21") LocalDate fechaRegistro) {

    public static EmpresaResponse of(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getNombre(), e.getNit(), e.getCorreoContacto(), e.getFechaRegistro());
    }
}
