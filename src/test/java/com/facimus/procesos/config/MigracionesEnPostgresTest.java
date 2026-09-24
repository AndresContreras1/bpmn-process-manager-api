package com.facimus.procesos.config;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * Las mismas comprobaciones de {@link MigracionesTest}, pero contra PostgreSQL.
 * <p>
 * Aqui la unicidad de nombres por tienda la hacen cumplir los indices parciales de la V2, que H2 no puede tener:
 * si alguien quitara el {@code where activo} de la migracion del motor, la suite de todos los dias seguiria verde
 * y esta se pondria roja.
 */
@Import(PostgresDePrueba.class)
@ConPostgresReal
class MigracionesEnPostgresTest extends MigracionesTest {
}
