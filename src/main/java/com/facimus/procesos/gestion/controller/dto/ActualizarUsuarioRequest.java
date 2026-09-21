package com.facimus.procesos.gestion.controller.dto;

import com.facimus.procesos.gestion.model.RolAcceso;

import jakarta.validation.constraints.AssertTrue;

public record ActualizarUsuarioRequest(RolAcceso rolAcceso, Boolean activo) {

    @AssertTrue(message = "Debe enviar al menos rolAcceso o activo.")
    public boolean isActualizacionPresente() {
        return rolAcceso != null || activo != null;
    }
}
