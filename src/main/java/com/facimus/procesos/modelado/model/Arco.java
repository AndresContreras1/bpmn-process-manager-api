package com.facimus.procesos.modelado.model;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;

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

/** La secuencia del flujo entre actividades y gateways dentro de un mismo pool. */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "arcos")
// Baja logica: borrar marca la fila como inactiva, y ninguna consulta ve las filas inactivas.
@SQLDelete(sql = "update arcos set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
public class Arco extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String etiqueta;

    @Column(length = 500)
    private String condicion;

    /** La salida que toma un gateway que decide cuando ninguna de sus condiciones se cumple. */
    @Column(name = "por_defecto", nullable = false)
    private boolean porDefecto;

    /** En que orden evalua el gateway sus salidas; entre dos con el mismo orden decide el id. */
    @Column(nullable = false)
    private int orden;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "origen_id", nullable = false)
    private NodoFlujo origen;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "destino_id", nullable = false)
    private NodoFlujo destino;

    /** Redundante respecto a origen/destino: sirve para validar que ambos comparten el mismo pool. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;
}
