package com.facimus.procesos.gestion.model;

import java.time.LocalDate;

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

/**
 * PR 68: lo que una tienda cambio de los festivos de su pais en un dia: lo vuelve festivo con un nombre (agrega uno, o
 * renombra el calculado) o le quita el festivo calculado. Los festivos calculados no se guardan: salen de la ley.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "festivos")
public class FestivoEditado extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    /** El nombre con que queda; vacio si el dia deja de ser festivo. */
    @Column(length = 120)
    private String nombre;

    /** Si el dia queda festivo (agregado o renombrado) o deja de serlo. */
    @Column(nullable = false)
    private boolean festivo;
}
