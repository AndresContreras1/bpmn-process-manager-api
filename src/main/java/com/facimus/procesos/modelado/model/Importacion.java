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

/** PR 54: un archivo BPMN que se importo en un proceso, con lo que cambio y el informe de lo que paso con cada cosa. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "importaciones")
public class Importacion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false)
    private Long procesoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false)
    private int bytes;

    /** SHA-256 del archivo: dice si dos importaciones fueron del mismo. */
    @Column(name = "huella_archivo", nullable = false, length = 64)
    private String huellaArchivo;

    @Column(nullable = false)
    private int creados;

    @Column(nullable = false)
    private int editados;

    @Column(name = "dados_de_baja", nullable = false)
    private int dadosDeBaja;

    /** El informe que recibio quien importo, en JSON. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String informe;
}
