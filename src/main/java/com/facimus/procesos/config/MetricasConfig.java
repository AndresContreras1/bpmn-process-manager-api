package com.facimus.procesos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.facimus.procesos.ejecucion.service.MedidoresDeLaOperacion;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Los medidores de la operacion en Actuator: pedidos corriendo, tareas esperando y mensajes sin llegar. Los mira
 * quien opera el servidor, que es el mismo que ya miraba la memoria y el pool de conexiones.
 *
 * <p>Son de toda la instalacion y no de una tienda, a proposito. Un medidor con una etiqueta por tienda crearia
 * una serie nueva cada vez que alguien se registra, y eso es como se rompe un sistema de metricas. Lo que cada
 * tienda cuenta de lo suyo es el tablero, que se pide con su token y responde solo lo de ella.
 *
 * <p>Un medidor se lee cuando alguien pide las metricas, no en un bucle: calcularlos cada pocos segundos seria
 * trabajo constante para unas cifras que casi nadie mira.
 */
@Configuration
public class MetricasConfig {

    @Bean
    public MeterBinder medidoresDeLaOperacion(MedidoresDeLaOperacion operacion) {
        return registro -> {
            Gauge.builder("casos.abiertos", operacion, MedidoresDeLaOperacion::casosAbiertos)
                    .description("Pedidos que siguen corriendo")
                    .register(registro);
            Gauge.builder("tareas.pendientes", operacion, MedidoresDeLaOperacion::tareasPendientes)
                    .description("Tareas esperando en alguna bandeja")
                    .register(registro);
            Gauge.builder("mensajes.salientes.pendientes", operacion, MedidoresDeLaOperacion::salientesPendientes)
                    .description("Mensajes mandados que todavia no han llegado")
                    .register(registro);
            Gauge.builder("mensajes.entrantes.pendientes", operacion, MedidoresDeLaOperacion::entrantesPendientes)
                    .description("Mensajes que llegaron antes de tiempo o que un socio dejo para mas adelante")
                    .register(registro);
        };
    }
}
