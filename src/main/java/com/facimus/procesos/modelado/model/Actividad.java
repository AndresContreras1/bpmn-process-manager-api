package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;

import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    /** PR 56: el proceso que corre una actividad de llamada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_llamado_id")
    private Proceso procesoLlamado;

    /**
     * PR 70: a quien le llega la tarea; nula es la bandeja del rol de la lane, lo de siempre. Solo la lleva una
     * actividad de usuario, como lo exige el check ck_nodos_flujo_asignacion_de_usuario.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "asignacion", length = 20)
    private TipoAsignacion tipoAsignacion;

    /** PR 70: la persona fija de una asignacion PERSONA. */
    @Column(name = "asignacion_usuario_id")
    private Long asignacionUsuarioId;

    /** PR 70: la variable del caso que dice la persona, en una asignacion CAMPO. */
    @Column(name = "asignacion_campo", length = 200)
    private String asignacionCampo;

    @Override
    public boolean puedeEnviarMensajes() {
        return tipoActividad.puedeEnviar();
    }

    @Override
    public boolean puedeRecibirMensajes() {
        return tipoActividad.puedeRecibir();
    }
}
