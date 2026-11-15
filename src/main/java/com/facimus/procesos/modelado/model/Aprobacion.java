package com.facimus.procesos.modelado.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 64: lo que un aprobador decidio sobre una solicitud, con su comentario en Markdown y en HTML saneado. Una por
 * persona y solicitud, y no se edita: una decision es lo que se dijo ese dia.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "aprobaciones")
public class Aprobacion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_id", nullable = false, updatable = false)
    private Long solicitudId;

    @Column(name = "aprobador_id", nullable = false, updatable = false)
    private Long aprobadorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Decision decision;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    @Builder.Default
    private String markdown = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    @Builder.Default
    private String html = "";

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
