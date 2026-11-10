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

/** Un participante del proceso: la empresa duena, un cliente, un proveedor o un sistema externo. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "pools")
// Baja logica: borrar marca la fila como inactiva, y ninguna consulta ve las filas inactivas.
@SQLDelete(sql = "update pools set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
public class Pool extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PR 52: su id en el XML de BPMN, estable de una exportacion a la siguiente. Se asigna al guardarse. */
    @Column(name = "bpmn_id", nullable = false, updatable = false, length = 64)
    private String bpmnId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_participante", nullable = false, length = 20)
    private TipoParticipante tipoParticipante;

    @Column(name = "caja_negra", nullable = false)
    @Builder.Default
    private boolean cajaNegra = false;

    /** Con que clase de socio habla este participante; la tienda y los pools sin sistema son NINGUNA. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Integracion integracion = Integracion.NINGUNA;

    @Column(nullable = false)
    private int orden;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    /** El id BPMN, si nadie le dio uno: el importador (PR 54) trae el suyo. */
    @PrePersist
    void asignarBpmnId() {
        if (bpmnId == null) {
            bpmnId = BpmnId.nuevo("Participant");
        }
    }
}
