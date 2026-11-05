package com.facimus.procesos.modelado.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Lo que respondio el modelo sobre un diagrama, y cuando. Se guarda en la base y no en la memoria de una instancia
 * (D34): con varias, cualquiera la devuelve otra vez mientras el diagrama no cambie, y todas cuentan las mismas
 * para el limite de la tienda.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "revisiones_ia")
public class RevisionIa extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false)
    private Long procesoId;

    /** SHA-256 del diagrama contado en texto, el mismo que se le mando al modelo. */
    @Column(nullable = false, length = 64)
    private String huella;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String resumen;

    /** Los hallazgos de la respuesta, en JSON. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String hallazgos;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
