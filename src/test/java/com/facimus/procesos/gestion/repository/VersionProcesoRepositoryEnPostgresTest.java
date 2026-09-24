package com.facimus.procesos.gestion.repository;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * Las versiones contra PostgreSQL. Lo que se prueba aqui y no en H2 es la columna {@code text} de la V13: un
 * {@code @Lob} de Hibernate puede terminar en un objeto grande (un oid aparte) en vez de en la propia fila, y eso
 * solo se ve contra el motor de verdad, cuando el diagrama entero va y vuelve.
 */
@Import({AuditoriaConfig.class, PostgresDePrueba.class})
@ConPostgresReal
class VersionProcesoRepositoryEnPostgresTest extends VersionProcesoRepositoryTest {
}
