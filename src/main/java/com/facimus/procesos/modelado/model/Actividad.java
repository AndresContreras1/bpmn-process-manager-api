package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Sin nullable = false: con SINGLE_TABLE los demas nodos comparten esta columna y no tienen tipo de
    // actividad. Que toda actividad tenga tipo lo exige el check ck_nodos_flujo_actividad_con_tipo, y el
    // service pone USUARIO cuando la peticion no lo manda.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", length = 20)
    private TipoActividad tipoActividad;
}
