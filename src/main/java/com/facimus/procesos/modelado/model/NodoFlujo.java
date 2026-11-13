package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.common.formularios.Formulario;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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

    /** PR 52: su id en el XML de BPMN, estable de una exportacion a la siguiente. Se asigna al guardarse. */
    @Column(name = "bpmn_id", nullable = false, updatable = false, length = 64)
    private String bpmnId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "posicion_x", nullable = false)
    private int posicionX;

    @Column(name = "posicion_y", nullable = false)
    private int posicionY;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;

    /** PR 56: el subproceso que lo contiene; vacio si esta en el flujo principal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "padre_id")
    private NodoFlujo padre;

    /**
     * PR 69: el formulario de una actividad de usuario o de un evento de inicio; nulo en cualquier otro nodo, como lo
     * exige el check ck_nodos_flujo_formulario. Va aqui y no en cada subtipo porque las dos clases de nodo que lo
     * llevan comparten la columna, y el servicio decide en cual se puede poner.
     */
    @Convert(converter = FormularioConverter.class)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private Formulario formulario;

    /**
     * PR 71: el plazo de la tarea, en una actividad de usuario, o del caso entero, en un evento de inicio; nulo en
     * cualquier otro nodo, como lo exige el check ck_nodos_flujo_plazo_de_tarea_o_inicio. Va aqui por lo mismo que el
     * formulario: las dos clases de nodo que lo llevan comparten las columnas.
     */
    @Column(name = "plazo_cantidad")
    private Integer plazoCantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "plazo_unidad", length = 20)
    private UnidadDePlazo plazoUnidad;

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

    /** El id BPMN, si nadie le dio uno: el importador (PR 54) trae el suyo. */
    @PrePersist
    void asignarBpmnId() {
        if (bpmnId == null) {
            bpmnId = BpmnId.nuevo(prefijoBpmn());
        }
    }

    /** El tipo de nodo en BPMN: Activity, Gateway o Event, segun la subclase. */
    public String prefijoBpmn() {
        if (this instanceof Gateway) {
            return "Gateway";
        }
        return this instanceof Evento ? "Event" : "Activity";
    }
}
