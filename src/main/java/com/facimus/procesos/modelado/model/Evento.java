package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;

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
    @Column(name = "tipo_evento", length = 30)
    private TipoEvento tipoEvento;

    /** PR 55: como se dice cuando salta, si es de temporizador. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_temporizador", length = 20)
    private TipoTemporizador tipoTemporizador;

    /** PR 55: la expresion ISO 8601 del temporizador: una duracion, una fecha o un ciclo. */
    @Column(length = 120)
    private String temporizador;

    /** PR 55: la actividad a la que va pegado un evento de borde. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjunto_a_id")
    private NodoFlujo adjuntoA;

    /** PR 55: si un evento de borde, al saltar, corta la actividad o la deja seguir. */
    @Column
    private Boolean interrumpe;

    /**
     * PR 71: lo que hace, ademas de abrir su camino, un temporizador de borde que no interrumpe: avisar o reasignar al
     * supervisor. Nulo en cualquier otro evento, como lo exige el check ck_nodos_flujo_escalamiento_de_borde.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "escalamiento", length = 20)
    private AccionDeEscalamiento escalamiento;

    /** PR 71: el supervisor de un escalamiento; nulo es el dueno del proceso. */
    @Column(name = "escalar_a")
    private Long escalarA;

    /** R-31: el proceso empieza en un evento de inicio, asi que ningun arco llega a el. */
    @Override
    public boolean aceptaArcosEntrantes() {
        // PR 55: a un evento de borde no le llega nada: se activa desde la actividad a la que va pegado
        return !tipoEvento.empiezaElProceso() && !tipoEvento.esDeBorde();
    }

    /** R-32: un evento de fin cierra su camino, asi que ningun arco sale de el. */
    @Override
    public boolean aceptaArcosSalientes() {
        return !tipoEvento.terminaElProceso();
    }

    /** El throw de mensaje manda al salir del proceso. */
    @Override
    public boolean puedeEnviarMensajes() {
        return tipoEvento.puedeEnviar();
    }

    /** Los catch de mensaje esperan: el de inicio abre el caso y el intermedio lo despierta. */
    @Override
    public boolean puedeRecibirMensajes() {
        return tipoEvento.puedeRecibir();
    }
}
