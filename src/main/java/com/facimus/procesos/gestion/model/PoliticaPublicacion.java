package com.facimus.procesos.gestion.model;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * PR 64: como publica una tienda. Sin fila publica directo. Con aprobacion, los aprobadores salen de una lista de
 * personas o de un rol de proceso, nunca de los dos; con la politica apagada lo demas se guarda igual.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "politicas_publicacion")
public class PoliticaPublicacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ModoDePublicacion modo = ModoDePublicacion.DIRECTA;

    @Column(name = "aprobaciones_necesarias", nullable = false)
    @Builder.Default
    private int aprobacionesNecesarias = 1;

    /** El rol de proceso cuyas personas aprueban; vacio si aprueba una lista. */
    @Column(name = "rol_proceso_id")
    private Long rolProcesoId;

    /** Las personas que aprueban, por su id; vacia si aprueba un rol. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "bigint[]")
    @Builder.Default
    private List<Long> aprobadores = new ArrayList<>();

    public boolean conAprobacion() {
        return modo == ModoDePublicacion.CON_APROBACION;
    }
}
