package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Funcion responsable de una actividad (Analista, Supervisor, Auditor...),
 * no la persona. Se usa para nombrar Lanes. El campo activo permite la
 * eliminacion logica exigida por HU-19.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "roles_proceso")
public class RolProceso extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
