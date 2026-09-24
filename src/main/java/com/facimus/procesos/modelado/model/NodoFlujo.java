package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Nodo del diagrama BPMN: Actividad, Gateway o Evento. SINGLE_TABLE porque son
 * tres subtipos con pocos campos propios; evita joins innecesarios.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "nodos_flujo")
// Baja logica: ninguna consulta ve los nodos inactivos. Cada tipo de nodo (Actividad, Gateway) declara su borrado.
@SQLRestriction("activo = true")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_nodo")
public abstract class NodoFlujo extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "posicion_x", nullable = false)
    private int posicionX;

    @Column(name = "posicion_y", nullable = false)
    private int posicionY;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;

    /**
     * Si cada arco que sale de este nodo necesita condicion. Es un metodo y no un instanceof porque el origen de un
     * arco llega como proxy perezoso de NodoFlujo, y el proxy delega el metodo en el nodo real.
     */
    public boolean exigeCondicionAlSalir() {
        return false;
    }

    /**
     * Si un arco puede terminar en este nodo. Como exigeCondicionAlSalir, es un metodo y no un instanceof:
     * el destino de un arco llega como proxy perezoso de NodoFlujo y el proxy delega en el nodo real.
     */
    public boolean aceptaArcosEntrantes() {
        return true;
    }

    /** Si un arco puede salir de este nodo. */
    public boolean aceptaArcosSalientes() {
        return true;
    }

    /** Si desde este nodo se puede mandar un mensaje a otro participante. */
    public boolean puedeEnviarMensajes() {
        return false;
    }

    /** Si este nodo puede esperar un mensaje de otro participante. */
    public boolean puedeRecibirMensajes() {
        return false;
    }
}
