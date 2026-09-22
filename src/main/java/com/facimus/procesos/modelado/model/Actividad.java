package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Una tarea del proceso. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@DiscriminatorValue("ACTIVIDAD")
// El SQL de borrado no se hereda: cada tipo de nodo marca su fila como inactiva.
@SQLDelete(sql = "update nodos_flujo set activo = false where id = ? and version = ?")
public class Actividad extends NodoFlujo {

    @Column(length = 1000)
    private String descripcion;
}
