package com.facimus.procesos.ejecucion;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * Los cuatro finales de la correlacion contra PostgreSQL. Aqui se ve de verdad que la clave externa es unica por
 * tienda y no por instalacion, que en H2 tambien vale pero con otro indice detras.
 */
@Import(PostgresDePrueba.class)
@ConPostgresReal
class MensajeriaEnPostgresTest extends MensajeriaIntegracionTest {
}
