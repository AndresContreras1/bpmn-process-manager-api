package com.facimus.procesos.modelado.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una tarea del proceso. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("ACTIVIDAD")
public class Actividad extends NodoFlujo {

    @Column
    private String descripcion;
}
