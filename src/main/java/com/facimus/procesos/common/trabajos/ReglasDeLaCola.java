package com.facimus.procesos.common.trabajos;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Como se comporta la cola de trabajos ({@code trabajos.*}).
 *
 * @param trabajar si esta instancia toma trabajos; en las pruebas no, salvo la que lo prueba
 * @param trabajadores cuantos trabajos corre esta instancia a la vez
 * @param pausa cuanto espera un trabajador que no encontro nada antes de volver a mirar
 * @param maximoIntentos los intentos de un trabajo que no dice otra cosa
 * @param esperaBase la espera tras el primer fallo; se duplica en cada intento
 * @param esperaMaxima el techo de esa espera
 * @param maximoEnCursoPorTienda cuantos trabajos de una misma tienda pueden correr a la vez
 * @param atascadoTras cuando un trabajo en curso se da por abandonado, si su instancia murio a medias
 * @param retencion cuanto se guarda un trabajo terminado, hecho o fallido, antes de que la purga lo borre
 */
@ConfigurationProperties("trabajos")
public record ReglasDeLaCola(boolean trabajar, int trabajadores, Duration pausa, int maximoIntentos,
        Duration esperaBase, Duration esperaMaxima, int maximoEnCursoPorTienda, Duration atascadoTras,
        Duration retencion) {
}
