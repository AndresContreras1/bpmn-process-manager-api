package com.facimus.procesos.modelado.model;

import java.time.LocalDateTime;
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
 * PR 64: una solicitud de revision de un proceso. Congela el borrador al pedirse: la revision del borrador de ese
 * momento y la instantanea que se publicara si se aprueba, con el numero que tendra y la version vigente contra la
 * que se comparo. Lo que se edite despues en el borrador no cambia lo que se revisa. Los aprobadores y cuantas
 * aprobaciones hacen falta se fijan al pedirla.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "solicitudes_publicacion")
public class SolicitudPublicacion extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false, updatable = false)
    private Long procesoId;

    /** La revision del borrador que se congelo; vacia si la limpieza de revisiones viejas ya se la llevo. */
    @Column(name = "revision_id")
    private Long revisionId;

    @Column(name = "solicitante_id", nullable = false, updatable = false)
    private Long solicitanteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private EstadoSolicitud estado = EstadoSolicitud.ABIERTA;

    /** Que se cambio y por que, en Markdown; vacio si no se dijo. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    @Builder.Default
    private String markdown = "";

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    @Builder.Default
    private String html = "";

    /** El numero de la version que publicara. */
    @Column(nullable = false, updatable = false)
    private int numero;

    /** La version vigente cuando se pidio; vacia si no habia ninguna. */
    @Column(name = "version_base", updatable = false)
    private Integer versionBase;

    @Column(nullable = false, length = 64, updatable = false)
    private String huella;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String definicion;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "modelo_bpmn", nullable = false, updatable = false)
    private String modeloBpmn;

    @Column(name = "aprobaciones_necesarias", nullable = false, updatable = false)
    private int aprobacionesNecesarias;

    /** Quienes pueden decidir, por su id: los que la politica daba al pedirla, sin quien la pidio. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "bigint[]", updatable = false)
    @Builder.Default
    private List<Long> aprobadores = new ArrayList<>();

    /** Por que una solicitud aprobada no se publico. */
    @Column(length = 2000)
    private String motivo;

    /** Quien la cerro: el aprobador que decidio lo ultimo, o quien la pidio si la retiro. */
    @Column(name = "cerrada_por")
    private Long cerradaPor;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    /** El numero de la version que publico; vacio si no publico. */
    @Column(name = "version_publicada")
    private Integer versionPublicada;

    public boolean abierta() {
        return estado == EstadoSolicitud.ABIERTA;
    }
}
