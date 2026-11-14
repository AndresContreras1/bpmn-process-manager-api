package com.facimus.procesos.ejecucion.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PR 71: cuando vence una tarea o un caso, y como va. En produccion son horas UTC del reloj de pared; en prueba (D8),
 * ticks del reloj de la tienda, como los temporizadores. El aviso es el comienzo del ultimo cuarto del plazo: desde ahi
 * esta por vencer. Lo llevan igual una tarea y un caso; el check de la base no deja un modo a medias.
 */
@Getter
@Setter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vencimiento {

    @Column(name = "vence_en")
    private LocalDateTime venceEn;

    @Column(name = "aviso_en")
    private LocalDateTime avisoEn;

    @Column(name = "vence_tick")
    private Integer venceTick;

    @Column(name = "aviso_tick")
    private Integer avisoTick;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_plazo", length = 20)
    private EstadoPlazo estado;

    /** Uno de produccion, recien armado: a tiempo hasta la hora del aviso. */
    public static Vencimiento enProduccion(LocalDateTime avisoEn, LocalDateTime venceEn) {
        Vencimiento vencimiento = new Vencimiento();
        vencimiento.avisoEn = avisoEn;
        vencimiento.venceEn = venceEn;
        vencimiento.estado = EstadoPlazo.A_TIEMPO;
        return vencimiento;
    }

    /** Uno de prueba, recien armado: a tiempo hasta el tick del aviso. */
    public static Vencimiento enPrueba(int avisoTick, int venceTick) {
        Vencimiento vencimiento = new Vencimiento();
        vencimiento.avisoTick = avisoTick;
        vencimiento.venceTick = venceTick;
        vencimiento.estado = EstadoPlazo.A_TIEMPO;
        return vencimiento;
    }

    /** Si se cuenta en ticks, como un caso de prueba. */
    public boolean enTicks() {
        return venceTick != null;
    }

    /**
     * El estado que le toca en ese momento: en produccion con la hora, en prueba con el tick. Nunca uno anterior al que
     * ya tiene.
     */
    public EstadoPlazo estadoEn(Instant ahora, int tick) {
        return estado.yA(enTicks() ? segunElTick(tick) : segunLaHora(LocalDateTime.ofInstant(ahora, ZoneOffset.UTC)));
    }

    private EstadoPlazo segunElTick(int tick) {
        if (tick >= venceTick) {
            return EstadoPlazo.VENCIDA;
        }
        if (tick >= avisoTick) {
            return EstadoPlazo.POR_VENCER;
        }
        return EstadoPlazo.A_TIEMPO;
    }

    private EstadoPlazo segunLaHora(LocalDateTime hora) {
        if (hora.compareTo(venceEn) >= 0) {
            return EstadoPlazo.VENCIDA;
        }
        if (hora.compareTo(avisoEn) >= 0) {
            return EstadoPlazo.POR_VENCER;
        }
        return EstadoPlazo.A_TIEMPO;
    }
}
