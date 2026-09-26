package com.facimus.procesos.ejecucion.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.TipoDestino;

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
 * D9: un mensaje que el proceso mando, en la bandeja de salida hasta que el reloj lo entregue. No hay colas ni
 * llamadas de red: hay una fila que dice a quien va, que lleva dentro y en que tick le toca llegar.
 *
 * <p>El mensaje de la version se guarda por id y por nombre, sin clave foranea, igual que los nodos de
 * {@link ActividadCaso}: el id es el de la instantanea, y el modelo vivo puede haber borrado ese mensaje despues
 * sin que lo que el caso envio deje de ser cierto. Por eso tambien viaja el nombre del participante destino.
 *
 * <p>Sin version ni auditoria de usuario: no es un documento que alguien edita, lo escribe el motor dentro del
 * bloqueo del caso y lo unico que cambia despues es si llego. Quien lo provoco ya esta en la bitacora del caso.
 */
@NamedQuery(name = "MensajeSaliente.bandejaDeSalida", query = """
        select m from MensajeSaliente m
        where m.empresa.id = :empresaId
          and m.caso.proceso.id = :procesoId
          and (:estado is null or m.estado = :estado)
        order by m.id
        """)
@NamedQuery(name = "MensajeSaliente.bandejaDeSalida.count", query = """
        select count(m) from MensajeSaliente m
        where m.empresa.id = :empresaId
          and m.caso.proceso.id = :procesoId
          and (:estado is null or m.estado = :estado)
        """)
@NamedQuery(name = "MensajeSaliente.vencidos", query = """
        select m from MensajeSaliente m
        where m.empresa.id = :empresaId
          and m.estado = com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente.PENDIENTE
          and m.tickEntrega <= :tick
        order by m.tickEntrega, m.id
        """)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "mensajes_salientes")
public class MensajeSaliente extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id", nullable = false, updatable = false)
    private Caso caso;

    /** El id del mensaje dentro de la instantanea de la version. */
    @Column(name = "mensaje_id", nullable = false, updatable = false)
    private Long mensajeId;

    @Column(nullable = false, length = 120, updatable = false)
    private String nombre;

    @Column(name = "pool_destino_nombre", nullable = false, length = 120, updatable = false)
    private String poolDestinoNombre;

    /** Con que clase de socio habla el participante destino: es lo que decide que simulador lo atiende. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Integracion integracion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destino", length = 20, updatable = false)
    private TipoDestino tipoDestino;

    /** El valor con el que la respuesta encontrara su caso, normalmente el numero de pedido. */
    @Column(length = 120, updatable = false)
    private String clave;

    /** Los campos declarados del mensaje, tomados de las variables del caso, en JSON. */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoMensajeSaliente estado;

    @Column(name = "tick_creacion", nullable = false, updatable = false)
    private int tickCreacion;

    /** En que tick le toca llegar: el de creacion mas lo que tarde el socio del otro lado. */
    @Column(name = "tick_entrega", nullable = false)
    private int tickEntrega;

    @Column(nullable = false)
    private int intentos;

    /** Por que no llego, cuando no llego. */
    @Column(length = 500)
    private String error;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
