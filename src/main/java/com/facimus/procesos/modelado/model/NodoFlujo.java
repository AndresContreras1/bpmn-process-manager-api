package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
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

/**
 * Nodo del diagrama BPMN: Actividad o Gateway. SINGLE_TABLE porque solo hay
 * dos subtipos con pocos campos propios; evita joins innecesarios.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "nodos_flujo")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_nodo")
public abstract class NodoFlujo extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "posicion_x", nullable = false)
    private int posicionX;

    @Column(name = "posicion_y", nullable = false)
    private int posicionY;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;
}
