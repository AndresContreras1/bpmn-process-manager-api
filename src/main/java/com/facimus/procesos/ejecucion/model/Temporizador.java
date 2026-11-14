package com.facimus.procesos.ejecucion.model;

import java.time.LocalDateTime;

import com.facimus.procesos.common.EntidadEmpresa;
import com.facimus.procesos.modelado.model.TipoTemporizador;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * PR 68: un temporizador armado. Lo arma el motor cuando un token llega a una espera o a una actividad con bordes, y
 * la publicacion cuando la version vigente empieza por uno; lo dispara el planificador (produccion) o el tick del
 * reloj de la tienda (prueba).
 *
 * <p>Se guarda lo que hace falta para saltar sin volver a leer el diagrama, copiado de la version como en
 * {@link ActividadCaso}: el nodo por id y por nombre, la expresion y cuando vence. El caso, el paso y la version van
 * por id y sin entidad, como el nodo: quien dispara bloquea el caso por su cuenta (D3) y lo demas lo lee del grafo.
 *
 * <p>Las horas se guardan en UTC, como las de la cola de trabajos.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "temporizadores")
public class Temporizador extends EntidadEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proceso_id", nullable = false, updatable = false)
    private Long procesoId;

    /** La version cuyo diagrama dice que hacer al saltar. */
    @Column(name = "version_proceso_id", nullable = false, updatable = false)
    private Long versionProcesoId;

    /** El caso al que pertenece; vacio en un inicio temporizado, que es el que abre casos. */
    @Column(name = "caso_id", updatable = false)
    private Long casoId;

    /**
     * El paso del caso que lo armo: el token que espera en un intermedio, o la actividad de la que cuelga un borde.
     * Mientras ese paso no espere, el temporizador sobra.
     */
    @Column(name = "actividad_caso_id", updatable = false)
    private Long actividadCasoId;

    /** El evento de temporizador dentro de la instantanea de la version. */
    @Column(name = "nodo_id", nullable = false, updatable = false)
    private Long nodoId;

    @Column(name = "nodo_nombre", nullable = false, length = 120, updatable = false)
    private String nodoNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ClaseTemporizador clase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ModoCaso modo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_temporizador", nullable = false, length = 20, updatable = false)
    private TipoTemporizador tipoTemporizador;

    @Column(nullable = false, length = 120, updatable = false)
    private String expresion;

    /** De un borde, si saca a la actividad al saltar; vacio en los demas. */
    @Column(updatable = false)
    private Boolean interrumpe;

    /** Cuando vence, en UTC: lo que mira el planificador en produccion. */
    @Column(name = "vence_en")
    private LocalDateTime venceEn;

    /** En que tick del reloj de la tienda vence: lo que mira el tick en prueba. */
    @Column(name = "vence_tick")
    private Integer venceTick;

    /** De un ciclo, cuantas veces salta en total; vacio si no tiene fin o no es un ciclo. */
    @Column(updatable = false)
    private Integer repeticiones;

    /** Cuantas veces ha saltado. */
    @Column(nullable = false)
    private int disparos;

    /** Cuantas veces fallo al saltar; al tercer fallo queda FALLIDO. */
    @Column(nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTemporizador estado;

    /** Cuando se armo, en UTC; un ciclo sin inicio cuenta sus vueltas desde aqui. */
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** La ultima vez que salto, en UTC. */
    @Column(name = "disparado_en")
    private LocalDateTime disparadoEn;
}
