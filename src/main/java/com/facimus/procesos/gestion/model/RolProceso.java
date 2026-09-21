package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Funcion responsable de una actividad (Analista, Supervisor, Auditor...),
 * no la persona. Se usa para nombrar Lanes. El campo activo permite la
 * eliminacion logica exigida por HU-19.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "roles_proceso")
public class RolProceso extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column
    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;
}
