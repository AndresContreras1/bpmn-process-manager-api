package com.facimus.procesos.ejecucion;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * El reloj y los socios contra PostgreSQL. Aqui se ve de verdad la consulta que recoge lo que el tick puede
 * atender: mezcla dos resultados y una comparacion de ticks en el mismo {@code where}, y en un motor tipado los
 * enumerados de esa consulta tienen que cuadrar con los de la columna.
 */
@Import(PostgresDePrueba.class)
@ConPostgresReal
class SimulacionEnPostgresTest extends SimulacionIntegracionTest {
}
