package com.facimus.procesos.gestion.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/**
 * PR 68 (D35): el calendario laboral de una tienda. Su pais dice que festivos se calculan; la zona, la jornada y los
 * dias habiles dicen cuando corre el tiempo habil con el que se cuentan los plazos. Uno por tienda, y solo existe
 * desde que alguien lo edita: hasta entonces vale el de su pais.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "calendarios")
public class Calendario extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Pais pais;

    @Column(name = "zona_horaria", nullable = false, length = 40)
    private String zonaHoraria;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Convert(converter = DiasDeLaSemanaConverter.class)
    @Column(name = "dias_habiles", nullable = false, length = 70)
    private Set<DayOfWeek> diasHabiles;
}
