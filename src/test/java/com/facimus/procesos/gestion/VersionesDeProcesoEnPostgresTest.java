package com.facimus.procesos.gestion;

import org.springframework.context.annotation.Import;

import com.facimus.procesos.postgres.ConPostgresReal;
import com.facimus.procesos.postgres.PostgresDePrueba;

/**
 * Publicar, listar y retirar versiones de punta a punta contra PostgreSQL: el diagrama que se guarda es el que
 * escribe el servicio, no uno de prueba, y vuelve entero desde la columna {@code text} de la V13.
 */
@Import(PostgresDePrueba.class)
@ConPostgresReal
class VersionesDeProcesoEnPostgresTest extends VersionesDeProcesoIntegracionTest {
}
