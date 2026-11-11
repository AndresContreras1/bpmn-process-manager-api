package com.facimus.procesos.modelado.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** PR 40: un punto de guardado del borrador: su XML comprimido y su huella. Una revision no es una version. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "revisiones_borrador")
public class RevisionBorrador extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false)
    private Long procesoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotivoDeRevision motivo;

    @Column(nullable = false, length = 64)
    private String huella;

    @Column(nullable = false)
    private byte[] xml;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
