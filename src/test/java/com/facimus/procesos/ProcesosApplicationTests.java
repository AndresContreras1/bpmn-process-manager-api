package com.facimus.procesos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.config.DatosDemoInitializer;
import com.facimus.procesos.gestion.repository.EmpresaRepository;

@SpringBootTest
@ActiveProfiles("test")
class ProcesosApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Test
    void contextLoads() {
    }

    // Otros tests con esta misma configuracion comparten el contexto y su base: se busca la tienda demo, no una base vacia.
    @Test
    @DisplayName("El perfil test arranca sin la tienda demo: cada test crea sus datos")
    void perfilTest_arrancaSinDatosDemo() {
        assertThat(context.getBeanNamesForType(DatosDemoInitializer.class)).isEmpty();
        assertThat(empresaRepository.existsByNit("900123456-1")).isFalse();
    }
}
