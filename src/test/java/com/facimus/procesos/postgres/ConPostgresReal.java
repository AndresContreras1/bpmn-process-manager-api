package com.facimus.procesos.postgres;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * La clase corre contra PostgreSQL de verdad, no contra H2.
 * <p>
 * La etiqueta {@code postgres} la deja fuera del {@code verify} de todos los dias, que no puede depender de que
 * haya un Docker escuchando; el trabajo <em>PostgreSQL Integration</em> del CI corre justo esa etiqueta. La
 * condicion es el segundo cinturon: en una maquina sin Docker estas clases se saltan en vez de fallar.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("postgres")
@EnabledIf("com.facimus.procesos.postgres.PostgresDePrueba#hayDocker")
public @interface ConPostgresReal {
}
