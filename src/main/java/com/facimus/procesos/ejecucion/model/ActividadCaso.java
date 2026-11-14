package com.facimus.procesos.ejecucion.model;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * D4: un paso del caso por un nodo. Es lo que en otros motores seria un token, pero con nombre y estado, para que
 * mirar un caso sea leer por donde ha pasado y no descifrar una tabla de marcas.
 *
 * <p>El nodo se guarda por id y por nombre, copiados de la instantanea y sin clave foranea: el id es el del nodo
 * dentro de la version, y el modelo vivo puede haber borrado ese nodo despues sin que lo que el caso recorrio deje
 * de ser cierto.
 *
 * <p>PR 71: las bandejas van de la que vence antes a la que vence despues; las de produccion por su hora, las de prueba
 * por su tick, y las que no tienen plazo al final, de la mas vieja a la mas nueva.
 */
@NamedQuery(name = "ActividadCaso.bandejaPorRol", query = """
        select a from ActividadCaso a
        where a.empresa.id = :empresaId
          and a.tipoNodo = com.facimus.procesos.ejecucion.model.TipoNodoCaso.ACTIVIDAD
          and a.subtipo = 'USUARIO'
          and a.estado = :estado
          and (:rolProcesoId is null or a.rolProcesoId = :rolProcesoId)
          and (:procesoId is null or a.caso.proceso.id = :procesoId)
          and (:asignadoA is null or a.asignadoA = :asignadoA)
          and (:sinAsignar = false or a.asignadoA is null)
        order by a.vencimiento.venceEn asc nulls last, a.vencimiento.venceTick asc nulls last, a.id
        """)
@NamedQuery(name = "ActividadCaso.bandejaPorRol.count", query = """
        select count(a) from ActividadCaso a
        where a.empresa.id = :empresaId
          and a.tipoNodo = com.facimus.procesos.ejecucion.model.TipoNodoCaso.ACTIVIDAD
          and a.subtipo = 'USUARIO'
          and a.estado = :estado
          and (:rolProcesoId is null or a.rolProcesoId = :rolProcesoId)
          and (:procesoId is null or a.caso.proceso.id = :procesoId)
          and (:asignadoA is null or a.asignadoA = :asignadoA)
          and (:sinAsignar = false or a.asignadoA is null)
        """)
@NamedQuery(name = "ActividadCaso.bandejaDeMisRoles", query = """
        select a from ActividadCaso a
        where a.empresa.id = :empresaId
          and a.tipoNodo = com.facimus.procesos.ejecucion.model.TipoNodoCaso.ACTIVIDAD
          and a.subtipo = 'USUARIO'
          and a.estado = :estado
          and a.rolProcesoId in :roles
          and (:procesoId is null or a.caso.proceso.id = :procesoId)
        order by a.vencimiento.venceEn asc nulls last, a.vencimiento.venceTick asc nulls last, a.id
        """)
@NamedQuery(name = "ActividadCaso.bandejaDeMisRoles.count", query = """
        select count(a) from ActividadCaso a
        where a.empresa.id = :empresaId
          and a.tipoNodo = com.facimus.procesos.ejecucion.model.TipoNodoCaso.ACTIVIDAD
          and a.subtipo = 'USUARIO'
          and a.estado = :estado
          and a.rolProcesoId in :roles
          and (:procesoId is null or a.caso.proceso.id = :procesoId)
        """)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@DynamicUpdate
@Table(name = "actividades_caso")
public class ActividadCaso extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id", nullable = false, updatable = false)
    private Caso caso;

    /** El id del nodo dentro de la instantanea de la version. */
    @Column(name = "nodo_id", nullable = false, updatable = false)
    private Long nodoId;

    @Column(name = "nodo_nombre", nullable = false, length = 120, updatable = false)
    private String nodoNombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_nodo", nullable = false, length = 20, updatable = false)
    private TipoNodoCaso tipoNodo;

    /**
     * El tipo de actividad, de gateway o de evento del nodo, como texto: son tres enumerados distintos segun el
     * tipo de nodo, y aqui lo que importa es lo que decia la version.
     */
    @Column(nullable = false, length = 30, updatable = false)
    private String subtipo;

    /** El rol de proceso de la lane del nodo: el rol en cuya bandeja aparece la tarea. */
    @Column(name = "rol_proceso_id", updatable = false)
    private Long rolProcesoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoActividadCaso estado;

    /** Cuantos tokens han llegado a este join. Solo lo usan los gateways convergentes. */
    @Column(nullable = false)
    private int llegadas;

    /** PR 70: quien tiene la tarea, el unico que la completa; vacio mientras espera en la bandeja de su rol. */
    @Column(name = "asignado_a")
    private Long asignadoA;

    /** PR 70: quien la delego en quien la tiene ahora; le vuelve si el delegado la suelta. */
    @Column(name = "delegada_por")
    private Long delegadaPor;

    @Column(name = "tick_inicio", nullable = false)
    private int tickInicio;

    @Column(name = "tick_fin")
    private Integer tickFin;

    /**
     * PR 71: cuando vence la tarea y como va, si su actividad tiene plazo. Las escrituras de la entidad solo llevan las
     * columnas que cambiaron ({@code DynamicUpdate}): el repaso de los plazos mueve el estado con una escritura
     * propia, y quien guarde la tarea despues no se lo pisa con el que leyo antes.
     */
    @Embedded
    private Vencimiento vencimiento;

    /** Los datos con que se completo la tarea, en JSON; entran a las variables del caso bajo tarea.nombre. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "datos_salida")
    private String datosSalida;

    /** Una tarea es una actividad de usuario: lo demas lo hace el motor sin que nadie la toque. */
    public boolean esTarea() {
        return tipoNodo == TipoNodoCaso.ACTIVIDAD && "USUARIO".equals(subtipo);
    }
}
