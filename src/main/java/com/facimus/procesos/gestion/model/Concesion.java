package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.common.model.Accion;

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

/** PR 60: una accion concedida sobre una carpeta o un proceso, a una persona, un rol de proceso o toda la tienda. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "concesiones")
public class Concesion extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carpeta_id")
    private Long carpetaId;

    @Column(name = "proceso_id")
    private Long procesoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SujetoConcesion sujeto;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "rol_proceso_id")
    private Long rolProcesoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Accion accion;
}
