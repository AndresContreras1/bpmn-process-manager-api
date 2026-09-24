package com.facimus.procesos.gestion.repository;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * Las consultas de procesos contra PostgreSQL: las busquedas sin distinguir mayusculas, la paginacion y el orden
 * los resuelve el motor, y cada uno lo hace a su manera.
 */
@Import({AuditoriaConfig.class, PostgresDePrueba.class})
@ConPostgresReal
class ProcesoRepositoryEnPostgresTest extends ProcesoRepositoryTest {
}
