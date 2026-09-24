package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEditable;

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

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "procesos")
public class Proceso extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 4000)
    private String descripcion;

    @Column(nullable = false, length = 80)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoProceso estado = EstadoProceso.BORRADOR;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    /** Numero de la version vigente. Vacio si nunca se publico, o si se retiraron todas las que tenia. */
    @Column(name = "version_publicada")
    private Integer versionPublicada;

    /** Huella de esa version. Comparada con la del modelo vivo dice si el borrador tiene cambios sin publicar. */
    @Column(name = "huella_publicada", length = 64)
    private String huellaPublicada;
}
