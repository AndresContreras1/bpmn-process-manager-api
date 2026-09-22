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

/** La comunicacion entre pools: un participante envia (throw) y otro recibe (catch). */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "mensajes")
public class Mensaje extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 2000)
    private String contenido;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_origen_id", nullable = false)
    private Pool poolOrigen;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_destino_id", nullable = false)
    private Pool poolDestino;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;
}
