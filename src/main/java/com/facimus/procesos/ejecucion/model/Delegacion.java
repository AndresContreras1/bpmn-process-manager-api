package com.facimus.procesos.ejecucion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * PR 70: quien le paso una tarea a quien, por que y como termino. Mientras esta ACTIVA, la tarea la tiene el delegado y
 * vuelve a quien la delego si la suelta; despues queda como historia de la tarea.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "delegaciones")
public class Delegacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "actividad_caso_id", nullable = false, updatable = false)
    private ActividadCaso tarea;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id", nullable = false, updatable = false)
    private Caso caso;

    /** Quien la delego: a quien vuelve la tarea si el delegado la suelta. */
    @Column(name = "de_usuario_id", nullable = false, updatable = false)
    private Long delegadaPor;

    /** El delegado: quien tiene la tarea mientras la delegacion esta abierta. */
    @Column(name = "a_usuario_id", nullable = false, updatable = false)
    private Long delegadaA;

    @Column(nullable = false, length = 500, updatable = false)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDelegacion estado;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    /** La cierra como termino; una cerrada ya no cambia. */
    public void cerrar(EstadoDelegacion como, LocalDateTime cuando) {
        estado = como;
        cerradaEn = cuando;
    }
}
