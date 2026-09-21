package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEmpresa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** La secuencia del flujo entre actividades y gateways dentro de un mismo pool. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "arcos")
public class Arco extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String etiqueta;

    @Column
    private String condicion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    private NodoFlujo origen;

    @ManyToOne(optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    private NodoFlujo destino;

    /** Redundante respecto a origen/destino: sirve para validar que ambos comparten el mismo pool. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;
}
