package com.facimus.procesos.gestion.model;

import com.facimus.procesos.common.EntidadEditable;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * D16: lo que cada tienda decide sobre si misma: quien puede tocar la estructura de los diagramas, cuanto duran sus
 * sesiones y, desde que hay ejecucion, el reloj de su simulacion y quien lo mueve. Una fila por tienda, creada con
 * ella.
 */
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "configuracion_tienda")
public class ConfiguracionTienda extends EntidadEditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_estructura", nullable = false, length = 30)
    @Builder.Default
    private PoliticaEstructura politicaEstructura = PoliticaEstructura.ADMINISTRADOR_Y_EDITOR;

    /**
     * D8: el reloj de la simulacion de esta tienda, en ticks. Empieza en cero y solo sube; lo mueve quien prueba.
     * No es una hora: es el numero de pasos que la tienda ha dado, y con el se fecha todo lo que pasa en un caso.
     */
    @Column(nullable = false)
    @Builder.Default
    private int reloj = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_simulacion", nullable = false, length = 20)
    @Builder.Default
    private ModoSimulacion modoSimulacion = ModoSimulacion.MANUAL;

    /** D7: como se portan los socios de esta tienda. Viven en la misma fila porque son decisiones suyas. */
    @Embedded
    @Builder.Default
    private ParametrosSimulacion simulacion = ParametrosSimulacion.builder().build();

    /**
     * NIST 800-63B (AAL2): minutos que una sesion aguanta sin renovarse, de 30 a 60. La API solo ve la actividad
     * cuando se renueva, y un acceso dura 15 minutos: con menos de 30, quien trabaja sin parar tambien saldria.
     */
    @Column(name = "inactividad_sesion_minutos", nullable = false)
    @Builder.Default
    private int inactividadSesionMinutos = 60;

    /** Horas desde el login tras las que hay que volver a entrar, aunque la sesion no haya parado, de 1 a 24. */
    @Column(name = "duracion_sesion_horas", nullable = false)
    @Builder.Default
    private int duracionSesionHoras = 24;

    /** PR 49: a quien le exige la tienda un segundo factor para trabajar. */
    @Enumerated(EnumType.STRING)
    @Column(name = "politica_segundo_factor", nullable = false, length = 20)
    @Builder.Default
    private PoliticaSegundoFactor politicaSegundoFactor = PoliticaSegundoFactor.OPCIONAL;

    /** F12: el idioma con que trabaja su gente, salvo quien elija otro. */
    @Column(name = "idioma", nullable = false, length = 5)
    @Builder.Default
    private String idioma = "es-CO";

    /** F12: la zona horaria en que se leen sus fechas, salvo quien elija otra. */
    @Column(name = "zona_horaria", nullable = false, length = 40)
    @Builder.Default
    private String zonaHoraria = "America/Bogota";

    /** PR 50: su gente entra solo con Google o Microsoft; los administradores conservan la clave. */
    @Column(name = "solo_entrada_externa", nullable = false)
    @Builder.Default
    private boolean soloEntradaExterna = false;

    /**
     * PR 70: un administrador completa la tarea que tiene otra persona. Apagada, solo la completa quien la tiene; quien
     * la necesite completar por otro primero se la reasigna, y eso queda con su motivo.
     */
    @Column(name = "administrador_completa_por_otro", nullable = false)
    @Builder.Default
    private boolean administradorCompletaPorOtro = false;
}
