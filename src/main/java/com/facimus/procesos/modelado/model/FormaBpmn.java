package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 52: un BPMNShape. Donde esta un participante, una lane o un nodo del diagrama de un proceso, en coordenadas
 * absolutas, y donde va su etiqueta si la tiene fuera. La calcula {@link DisposicionBpmn} con cada cambio del proceso.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "formas_bpmn")
public class FormaBpmn extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false)
    private Long procesoId;

    /** El bpmn_id del elemento: el bpmnElement del BPMNShape. */
    @Column(nullable = false, length = 64)
    private String elemento;

    /** PARTICIPANTE, LANE, TAREA, COMPUERTA o EVENTO. */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double ancho;

    @Column(nullable = false)
    private double alto;

    @Column(name = "etiqueta_x")
    private Double etiquetaX;

    @Column(name = "etiqueta_y")
    private Double etiquetaY;

    @Column(name = "etiqueta_ancho")
    private Double etiquetaAncho;

    @Column(name = "etiqueta_alto")
    private Double etiquetaAlto;
}
