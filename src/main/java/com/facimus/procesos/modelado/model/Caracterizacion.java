package com.facimus.procesos.modelado.model;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.gestion.model.Proceso;

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

/**
 * PR 62: la ficha de caracterizacion de un proceso: para que existe, hasta donde llega, que recibe y que entrega, de
 * quien y para quien, como se mide, que puede salir mal y que norma lo pide. Una por proceso.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "caracterizaciones")
public class Caracterizacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    @Column(length = 4000)
    private String objetivo;

    @Column(length = 4000)
    private String alcance;

    @Column(length = 4000)
    private String entradas;

    @Column(length = 4000)
    private String salidas;

    @Column(length = 4000)
    private String proveedores;

    @Column(length = 4000)
    private String clientes;

    @Column(length = 4000)
    private String indicadores;

    @Column(length = 4000)
    private String riesgos;

    @Column(length = 4000)
    private String normativa;
}
