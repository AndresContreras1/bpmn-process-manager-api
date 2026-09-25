package com.facimus.procesos.ejecucion;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * El pedido de la demo entero contra PostgreSQL. Lo que solo el motor de verdad puede responder aqui: las columnas
 * {@code text} de los cuerpos de los mensajes, y las consultas con nombre de las bandejas, que preguntan
 * {@code :estado is null} con parametros que en PostgreSQL hay que tipar.
 */
@Import(PostgresDePrueba.class)
@ConPostgresReal
class DemoEnPostgresTest extends DemoDePuntaAPuntaTest {
}
