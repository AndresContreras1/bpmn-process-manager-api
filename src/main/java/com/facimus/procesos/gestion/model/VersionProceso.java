package com.facimus.procesos.gestion.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * D2: una publicacion del proceso. Guarda el diagrama entero tal como se publico, asi que no cambia cuando cambia el
 * modelo vivo, y su huella, que es lo que permite saber si el borrador tiene algo nuevo. Lo unico que se le edita es
 * el estado, para retirarla; por eso no lleva version optimista ni auditoria: quien y cuando publico son columnas
 * suyas.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "versiones_proceso")
public class VersionProceso extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    /** Empieza en 1 y sube de uno en uno dentro del proceso. Un numero no se reusa, ni siquiera si se retira. */
    @Column(nullable = false, updatable = false)
    private int numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVersion estado;

    @Column(name = "fecha_publicacion", nullable = false, updatable = false)
    private LocalDateTime fechaPublicacion;

    /** El usuario que publico; vacio si publico el sistema, como la tienda demo. */
    @Column(name = "publicado_por", updatable = false)
    private Long publicadoPor;

    /** SHA-256 del diagrama canonico: dos versiones con la misma huella tendrian el mismo diagrama. */
    @Column(nullable = false, length = 64, updatable = false)
    private String huella;

    /**
     * El diagrama completo en JSON, igual que lo devuelve GET /procesos/{id}/diagrama el dia que se publico.
     * <p>
     * No lleva {@code @Lob}: en PostgreSQL eso serian objetos grandes (un {@code oid} que apunta fuera de la fila,
     * con su propia vida y su propia limpieza), y esto es un documento de texto que quiere estar en la fila. El
     * tipo se pide a mano para que sea el {@code text} de la V13 y el {@code clob} de H2.
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String definicion;
}
