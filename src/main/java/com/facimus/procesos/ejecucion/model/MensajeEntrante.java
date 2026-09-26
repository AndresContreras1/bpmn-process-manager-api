package com.facimus.procesos.ejecucion.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.gestion.model.Proceso;

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
 * D9: un mensaje que llego al proceso, con lo que se hizo con el. Cuelga del proceso y no del caso porque un
 * entrante puede no tener caso: el que abre uno todavia no lo tiene, y el que no corresponde a ninguno se queda
 * escrito igual, que es justo lo que se mira cuando algo no llego a su sitio.
 *
 * <p>La clave externa la pone quien lo manda, asi que solo es unica dentro de su tienda: dos tiendas pueden llamar
 * igual a dos pedidos distintos sin saber la una de la otra. Repetirla no vuelve a procesar el mensaje, responde
 * lo que se contesto la primera vez.
 */
@NamedQuery(name = "MensajeEntrante.bandejaDeEntrada", query = """
        select m from MensajeEntrante m
        where m.empresa.id = :empresaId
          and m.proceso.id = :procesoId
          and (:resultado is null or m.resultado = :resultado)
        order by m.id
        """)
@NamedQuery(name = "MensajeEntrante.bandejaDeEntrada.count", query = """
        select count(m) from MensajeEntrante m
        where m.empresa.id = :empresaId
          and m.proceso.id = :procesoId
          and (:resultado is null or m.resultado = :resultado)
        """)
@NamedQuery(name = "MensajeEntrante.pendientesDeLaTienda", query = """
        select m from MensajeEntrante m
        where m.empresa.id = :empresaId
          and (m.resultado = com.facimus.procesos.ejecucion.model.ResultadoCorrelacion.EN_ESPERA
               or (m.resultado = com.facimus.procesos.ejecucion.model.ResultadoCorrelacion.PROGRAMADO
                   and m.tickDisponible <= :tick))
        order by m.id
        """)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "mensajes_entrantes")
public class MensajeEntrante extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "proceso_id", nullable = false, updatable = false)
    private Proceso proceso;

    /** El caso al que fue a parar, cuando fue a parar a alguno. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id")
    private Caso caso;

    @Column(nullable = false, length = 120, updatable = false)
    private String nombre;

    /** El valor con el que busca su caso: el campo de correlacion del mensaje, tomado de su cuerpo. */
    @Column(length = 120, updatable = false)
    private String clave;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String cuerpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private OrigenMensajeEntrante origen;

    /** Lo que puso quien lo mando para que repetirlo no lo procese dos veces. */
    @Column(name = "clave_externa", length = 120, updatable = false)
    private String claveExterna;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoCorrelacion resultado;

    @Column(nullable = false, updatable = false)
    private int tick;

    /** A partir de que tick se puede mirar. Solo lo usa el que un socio dejo dicho para mas adelante. */
    @Column(name = "tick_disponible", nullable = false, updatable = false)
    private int tickDisponible;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
