package com.facimus.procesos.modelado.model;

import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.gestion.model.Proceso;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * La comunicacion entre pools: un participante envia (throw) y otro recibe (catch). Cuando el pool de un
 * lado modela su flujo, el mensaje se ancla al nodo concreto que lo manda o lo espera; un pool de caja
 * negra no ancla nada, porque por dentro no se sabe que hace.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "mensajes")
// Baja logica: borrar marca la fila como inactiva, y ninguna consulta ve las filas inactivas.
@SQLDelete(sql = "update mensajes set activo = false where id = ? and version = ?")
@SQLRestriction("activo = true")
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

    /** El nodo desde el que sale, si el pool de origen modela su flujo (un throw de mensaje). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodo_origen_id")
    private NodoFlujo nodoOrigen;

    /** El nodo en el que entra, si el pool de destino modela su flujo (un catch de mensaje). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodo_destino_id")
    private NodoFlujo nodoDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destino", length = 20)
    private TipoDestino tipoDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "si_falla", nullable = false, length = 20)
    @Builder.Default
    private AccionSiFalla siFalla = AccionSiFalla.CONTINUAR;

    /** La actividad que atiende el fallo del envio; solo cuando siFalla es MANEJAR_ERROR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodo_manejo_error_id")
    private NodoFlujo nodoManejoError;

    /** El mensaje llega de fuera del diagrama: no hay throw que lo mande. */
    @Column(name = "origen_externo", nullable = false)
    @Builder.Default
    private boolean origenExterno = false;

    /** Los datos que viajan dentro, guardados como JSON en esta misma fila. */
    @Convert(converter = CamposConverter.class)
    @Column(length = 4000)
    @Builder.Default
    private List<CampoDeMensaje> campos = List.of();

    @Column(name = "uso_de_los_datos", length = 1000)
    private String usoDeLosDatos;

    /** El nombre con el que el cuerpo del mensaje entra a las variables del caso. */
    @Column(length = 60)
    private String variable;

    /** El mensaje que contesta a este, si lo hay: el par peticion y respuesta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "respuesta_id")
    private Mensaje respuestaEsperada;
}
