package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * PR 62: lo que se escribe de un proceso, o de uno de sus elementos por su {@code bpmnId}, en Markdown. Al lado queda
 * el HTML ya convertido y saneado, que es lo unico que se muestra: nadie lee el Markdown como HTML.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "documentacion")
public class Documentacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    /** El elemento que documenta, o {@code null} si es el proceso entero. */
    @Column(name = "bpmn_id", updatable = false, length = 64)
    private String bpmnId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String markdown;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String html;
}
