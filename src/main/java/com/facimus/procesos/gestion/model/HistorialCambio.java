package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;

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
 * Bitacora de trazabilidad de la tienda. Solo se inserta, nunca se edita ni se elimina. Casi todo lo que se anota
 * cuelga de un proceso, pero un usuario nuevo o un rol no cuelgan de ninguno: por eso el proceso es opcional y cada
 * linea dice de que recurso habla (D15).
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "historial_cambios")
public class HistorialCambio extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Vacio cuando el cambio no es de un proceso, como el alta de un usuario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id")
    private Proceso proceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurso_tipo", nullable = false, length = 30)
    private RecursoDeHistorial recursoTipo;

    @Column(name = "recurso_id", nullable = false)
    private Long recursoId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "fecha_cambio", nullable = false)
    private LocalDateTime fechaCambio;

    @Column(name = "descripcion_cambio", nullable = false, length = 500)
    private String descripcionCambio;
}
