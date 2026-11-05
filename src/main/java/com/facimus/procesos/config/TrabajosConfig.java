package com.facimus.procesos.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.facimus.procesos.common.trabajos.ReglasDeLaCola;

/** Las reglas de la cola de trabajos, leidas de {@code trabajos.*}. */
@Configuration
@EnableConfigurationProperties(ReglasDeLaCola.class)
public class TrabajosConfig {
}
