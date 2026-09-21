package com.facimus.procesos.common;

import com.facimus.procesos.gestion.model.Empresa;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Toda entidad que pertenece a una empresa extiende esta clase.
 * La columna empresa_id acota el aislamiento multiempresa en cada consulta.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class EntidadEmpresa {

    @ManyToOne(optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, updatable = false)
    protected Empresa empresa;
}
