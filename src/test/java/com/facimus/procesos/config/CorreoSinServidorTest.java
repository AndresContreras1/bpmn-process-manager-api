package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.service.EmpresaService;

/**
 * Sin SMTP_HOST la aplicacion arranca igual, y un correo no se pierde ni se finge enviado: su trabajo falla al salir,
 * queda en la cola con el motivo y se vuelve a intentar mas tarde.
 */
@SpringBootTest
@ActiveProfiles("test")
class CorreoSinServidorTest {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private ColaDeTrabajos cola;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Sin servidor de correo, el correo de verificacion queda en la cola, pendiente y con el motivo")
    void sinServidor_elCorreoQuedaEnLaColaConElMotivo() {
        jdbc.update("delete from trabajos");
        Long empresaId = empresaService.registrar("Tienda sin correo", "900888777-1", "contacto@sin-correo.test",
                "Administradora", "admin@sin-correo.test", "clave-sin-correo").id();

        assertThat(cola.procesarUno()).isTrue();

        Map<String, Object> trabajo = jdbc.queryForMap("select * from trabajos where empresa_id = ?", empresaId);
        assertThat(trabajo).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
        assertThat((String) trabajo.get("ultimo_error")).contains("No hay servidor de correo configurado");
        assertThat(jdbc.queryForObject("select count(*) from enlaces_de_un_uso where empresa_id = ?", Integer.class,
                empresaId)).as("el enlace se deshace con el envio que fallo").isZero();
    }
}
