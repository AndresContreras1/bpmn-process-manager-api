package com.facimus.procesos.gestion.controller.dto;

import java.time.LocalDate;

import com.facimus.procesos.gestion.model.Empresa;

public record EmpresaResponse(Long id, String nombre, String nit, String correoContacto, LocalDate fechaRegistro) {

    public static EmpresaResponse of(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getNombre(), e.getNit(), e.getCorreoContacto(), e.getFechaRegistro());
    }
}
