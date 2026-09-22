package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

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
 * Un refresh token de una sesion. Se guarda solo su hash SHA-256. Sirve una vez: al renovar queda marcado como usado
 * y la sesion recibe uno nuevo.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_id", nullable = false)
    private Sesion sesion;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;
}
