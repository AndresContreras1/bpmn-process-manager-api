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

/** PR 50: la identidad de una persona en un proveedor de OpenID Connect, por su par (proveedor, sub). */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "identidades_externas")
public class IdentidadExterna extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 30)
    private String proveedor;

    @Column(nullable = false, length = 255)
    private String sujeto;

    /** El correo con que el proveedor la presento al vincularla; solo para mostrarla. */
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "vinculada_en", nullable = false)
    private LocalDateTime vinculadaEn;
}
