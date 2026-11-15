package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 61: un documento adjunto a un proceso, o a uno de sus elementos por su {@code bpmnId}, que no cambia aunque el
 * elemento se mueva o se renombre. Cada archivo subido es una {@link VersionDocumento}; el documento dice cual es la
 * ultima. Borrarlo lo retira de la vista; sus archivos siguen en el almacen hasta que se borre la tienda.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "documentos")
@SQLDelete(sql = "update documentos set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
public class Documento extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    /** El elemento al que va pegado, o {@code null} si es del proceso entero. */
    @Column(name = "bpmn_id", updatable = false, length = 64)
    private String bpmnId;

    /** El nombre de su ultima version. */
    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "version_actual", nullable = false)
    private int versionActual;
}
