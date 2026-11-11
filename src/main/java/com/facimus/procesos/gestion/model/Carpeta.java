package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEditable;

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

/** PR 59: una carpeta del repositorio de procesos. El arbol se arma con el padre de cada una. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "carpetas")
public class Carpeta extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "padre_id")
    private Long padreId;

    @Column(nullable = false, length = 120)
    private String nombre;
}
