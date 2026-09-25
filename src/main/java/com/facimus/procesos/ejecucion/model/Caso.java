package com.facimus.procesos.ejecucion.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEditable;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.VersionProceso;

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
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Una ejecucion de un proceso: un pedido. Corre sobre la version que estaba vigente el dia que se abrio, no sobre el
 * modelo vivo, asi que lo que recorre no cambia debajo de el aunque alguien siga editando el diagrama.
 *
 * <p>D3: toda operacion que avanza un caso empieza bloqueando su fila y termina en la misma transaccion. Completar
 * una tarea, recibir un mensaje y mover el reloj pueden tocar el mismo caso a la vez, y la version optimista no
 * basta porque cada token es una fila distinta.
 *
 * <p>D22: las consultas con las que la operacion lo busca van como consultas con nombre. Un nombre mal escrito
 * tumba el arranque y la prueba de contexto, no una peticion un martes por la tarde.
 */
@NamedQuery(name = "Caso.abiertosPorProceso", query = """
        select c from Caso c
        where c.empresa.id = :empresaId
          and c.proceso.id = :procesoId
          and c.estado = com.facimus.procesos.ejecucion.model.EstadoCaso.ABIERTO
        order by c.id
        """)
@NamedQuery(name = "Caso.porReferencia", query = """
        select c from Caso c
        where c.empresa.id = :empresaId
          and c.proceso.id = :procesoId
          and c.referencia = :referencia
        order by c.id
        """)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "casos")
public class Caso extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    /** La version publicada sobre la que corre; es la que manda, no el modelo vivo. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "version_proceso_id", nullable = false, updatable = false)
    private VersionProceso versionProceso;

    /**
     * El valor con el que los mensajes encuentran este caso, normalmente el numero de pedido. Puede faltar en un
     * caso abierto a mano que no espera mensajes.
     */
    @Column(length = 120)
    private String referencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCaso estado;

    /**
     * Las variables del caso en JSON: lo que se paso al abrirlo, los cuerpos de los mensajes recibidos y los datos
     * de las tareas completadas. Es lo que las condiciones de los gateways leen.
     *
     * <p>Sin {@code @Lob}: en PostgreSQL eso serian objetos grandes fuera de la fila. El tipo se pide a mano para
     * que sea el {@code text} de PostgreSQL y el {@code clob} de H2.
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String variables;

    /** El tick del reloj de la tienda en que se abrio. Hasta que exista el reloj, todos son 0. */
    @Column(name = "tick_inicio", nullable = false)
    private int tickInicio;

    /** El tick en que se cerro; vacio mientras siga abierto. */
    @Column(name = "tick_fin")
    private Integer tickFin;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;
}
