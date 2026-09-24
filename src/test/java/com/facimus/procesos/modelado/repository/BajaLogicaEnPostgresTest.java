package com.facimus.procesos.modelado.repository;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * La baja logica del modelado contra PostgreSQL, donde el par de nodos de un arco lo reserva el indice parcial de
 * la V8 y no la columna calculada de H2.
 */
@Import({AuditoriaConfig.class, PostgresDePrueba.class})
@ConPostgresReal
class BajaLogicaEnPostgresTest extends BajaLogicaRepositoryTest {
}
