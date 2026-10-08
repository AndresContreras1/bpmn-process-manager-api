package com.facimus.procesos.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

/**
 * H2 salio del proyecto: el desarrollo y las pruebas corren contra PostgreSQL, como produccion. Si una dependencia
 * lo volviera a traer, esto lo dice antes de que alguna configuracion vuelva a usarlo sin que nadie lo note.
 */
class SinH2Test {

    @Test
    @DisplayName("H2 no esta en el classpath")
    void h2NoEstaEnElClasspath() {
        assertThat(ClassUtils.isPresent("org.h2.Driver", null)).isFalse();
    }
}
