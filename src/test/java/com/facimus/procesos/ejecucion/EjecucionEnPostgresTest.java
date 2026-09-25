package com.facimus.procesos.ejecucion;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * Un pedido entero contra PostgreSQL. Lo que solo el motor de verdad puede responder: la columna {@code text} donde
 * viven las variables del caso, y la consulta con nombre de la bandeja, que pregunta {@code :rolProcesoId is null}
 * con parametros que en PostgreSQL hay que tipar.
 */
@Import(PostgresDePrueba.class)
@ConPostgresReal
class EjecucionEnPostgresTest extends EjecucionIntegracionTest {
}
