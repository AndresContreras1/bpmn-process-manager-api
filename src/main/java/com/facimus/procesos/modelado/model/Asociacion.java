package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 55: lo que une un artefacto con otro elemento. Los extremos se nombran por su bpmn_id porque pueden ser nodos,
 * flujos o artefactos; uno que ya no existe deja la asociacion sin dibujar, y el diagnostico lo avisa (A-15).
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "asociaciones")
@SQLDelete(sql = "update asociaciones set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
public class Asociacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bpmn_id", nullable = false, updatable = false, length = 64)
    private String bpmnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAsociacion tipo;

    @Column(nullable = false, length = 64)
    private String origen;

    @Column(nullable = false, length = 64)
    private String destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DireccionAsociacion direccion = DireccionAsociacion.NINGUNA;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @PrePersist
    void asignarBpmnId() {
        if (bpmnId == null) {
            bpmnId = BpmnId.nuevo(tipo == TipoAsociacion.ASOCIACION ? "Association" : "DataAssociation");
        }
    }
}
