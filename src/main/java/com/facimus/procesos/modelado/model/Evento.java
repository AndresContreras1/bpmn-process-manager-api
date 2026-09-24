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

/** Donde empieza el proceso, donde termina y donde espera o manda un mensaje. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@DiscriminatorValue("EVENTO")
// El SQL de borrado no se hereda: cada tipo de nodo marca su fila como inactiva.
@SQLDelete(sql = "update nodos_flujo set activo = false where id = ? and version = ?")
public class Evento extends NodoFlujo {

    // Sin nullable = false: con SINGLE_TABLE los demas nodos comparten esta columna y no tienen tipo de evento.
    // Que todo evento tenga tipo lo exigen EventoRequest (@NotNull) y el check ck_nodos_flujo_evento_con_tipo.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", length = 20)
    private TipoEvento tipoEvento;

    /** R-31: el proceso empieza en un evento de inicio, asi que ningun arco llega a el. */
    @Override
    public boolean aceptaArcosEntrantes() {
        return !tipoEvento.empiezaElProceso();
    }

    /** R-32: un evento de fin cierra su camino, asi que ningun arco sale de el. */
    @Override
    public boolean aceptaArcosSalientes() {
        return !tipoEvento.terminaElProceso();
    }

    /** El throw de mensaje manda al salir del proceso. */
    @Override
    public boolean puedeEnviarMensajes() {
        return tipoEvento == TipoEvento.MENSAJE_FIN;
    }

    /** Los catch de mensaje esperan: el de inicio abre el caso y el intermedio lo despierta. */
    @Override
    public boolean puedeRecibirMensajes() {
        return tipoEvento == TipoEvento.MENSAJE_INICIO || tipoEvento == TipoEvento.MENSAJE_INTERMEDIO;
    }
}
