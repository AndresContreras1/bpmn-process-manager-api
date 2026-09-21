package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** El criterio que indica a que caso concreto del proceso corresponde un mensaje. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "correlaciones")
public class Correlacion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String criterio;

    @OneToOne(optional = false)
    @JoinColumn(name = "mensaje_id", nullable = false, unique = true)
    private Mensaje mensaje;
}
