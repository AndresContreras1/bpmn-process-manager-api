package com.facimus.procesos.modelado.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 63: un comentario sobre un proceso. Sin padre es el primero de un hilo, y solo el lleva el ancla (un elemento por
 * su {@code bpmnId}, una version publicada) y el estado: resuelto, por quien y cuando. Con padre es una respuesta.
 * Guarda el Markdown y el HTML ya saneado, como la documentacion; el nombre de quien escribe y de quien se menciona se
 * lee al mostrarlo, asi que anonimizar a alguien lo cambia aqui tambien.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "comentarios")
public class Comentario extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false, updatable = false)
    private Long procesoId;

    /** El primero del hilo; vacio si este es el primero. */
    @Column(name = "padre_id", updatable = false)
    private Long padreId;

    @Column(name = "bpmn_id", updatable = false, length = 64)
    private String bpmnId;

    /** El numero de la version publicada de la que habla; vacio si habla del borrador. */
    @Column(name = "version_numero", updatable = false)
    private Integer versionNumero;

    @Column(name = "autor_id", nullable = false, updatable = false)
    private Long autorId;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String markdown;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String html;

    /** Las personas que nombra, por su id. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> menciones = new ArrayList<>();

    @Column(name = "resuelto_por")
    private Long resueltoPor;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    /** Cuando se cambio su texto por ultima vez; vacio si nunca. */
    @Column(name = "editado_en")
    private LocalDateTime editadoEn;

    @Column(name = "eliminado_por")
    private Long eliminadoPor;

    /** Borrado: sin texto ni menciones. Si es el primero de un hilo con respuestas, el hilo sigue en pie. */
    @Column(name = "eliminado_en")
    private LocalDateTime eliminadoEn;

    public boolean esRespuesta() {
        return padreId != null;
    }

    public boolean resuelto() {
        return resueltoEn != null;
    }

    public boolean eliminado() {
        return eliminadoEn != null;
    }
}
