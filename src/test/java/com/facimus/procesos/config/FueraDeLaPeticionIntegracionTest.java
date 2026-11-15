package com.facimus.procesos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.auditoria.SucesoAuditable;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.ejecucion.service.SimulacionService;
import com.facimus.procesos.gestion.service.BitacoraService;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.CuentaService;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.LimpiezaService;
import com.facimus.procesos.gestion.service.NotificacionService;
import com.facimus.procesos.gestion.service.SesionService;
import com.facimus.procesos.modelado.service.RevisionService;
import com.facimus.procesos.security.DesafiosWebAuthn;
import com.facimus.procesos.security.IntentosDeLogin;
import com.facimus.procesos.security.LimitesDePeticiones;
import com.facimus.procesos.security.RevokedSessions;

/**
 * PR 51: lo que corre fuera de una peticion dice de que tienda es, o que es del sistema. Cada prueba lo corre con el
 * alcance de produccion para un hilo que no dice nada, ninguna fila: si alguno se olvidara de decirlo, no veria ni
 * escribiria nada y la prueba lo notaria.
 */
@SpringBootTest
@ActiveProfiles("test")
class FueraDeLaPeticionIntegracionTest {

    private static final String CLAVE = "marea-violeta-del-sur";
    private static int tiendas;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmpresaService empresaService;
    @Autowired private BitacoraService bitacoraService;
    @Autowired private SesionService sesionService;
    @Autowired private LimpiezaService limpiezaService;
    @Autowired private RevisionService revisionService;
    @Autowired private IntentosDeLogin intentosDeLogin;
    @Autowired private ColaDeTrabajos colaDeTrabajos;
    @Autowired private CuentaService cuentaService;
    @Autowired private DesafiosWebAuthn desafiosWebAuthn;
    @Autowired private LimitesDePeticiones limitesDePeticiones;
    @Autowired private ConfiguracionTiendaService configuracionTiendaService;
    @Autowired private NotificacionService notificacionService;
    @Autowired private SimulacionService simulacionService;

    private Long empresaId;
    private Long adminId;

    @BeforeEach
    void tienda() {
        int numero = ++tiendas;
        String correo = "fuera" + numero + "@filas.test";
        empresaId = empresaService.registrar("Tienda de fondo " + numero, "9081" + numero + "000-1", correo, "Admin",
                correo, CLAVE).id();
        adminId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, correo);
    }

    @Test
    @DisplayName("La bitacora anota en la tienda del suceso, aunque su hilo no diga ninguna")
    void bitacora() {
        comoEnProduccion(() -> {
            bitacoraService.anotar(new SucesoAuditable(empresaId, SucesoAuditable.TipoDeActor.SISTEMA, null,
                    "PRUEBA_DE_FONDO", null, null, true, Map.of(), null, null, null, null,
                    LocalDateTime.now(ZoneOffset.UTC).withNano(0)));
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from bitacora where empresa_id = ? and accion = ?",
                Integer.class, empresaId, "PRUEBA_DE_FONDO")).isOne();
    }

    @Test
    @DisplayName("Al arrancar, las sesiones cerradas se recuperan de todas las tiendas")
    void sesionesCerradas() {
        String codigo = sesionService.iniciar(empresaId, adminId).sesion();
        sesionService.cerrarTodas(empresaId, adminId);
        RevokedSessions nuevas = new RevokedSessions(sesionService, 900, Clock.systemUTC());

        comoEnProduccion(() -> {
            nuevas.recuperarCerradas();
            return null;
        });
        assertThat(nuevas.estaRevocada(codigo)).isTrue();
    }

    @Test
    @DisplayName("La purga nocturna se lleva lo vencido de todas las tiendas")
    void purga() {
        jdbc.update("insert into enlaces_de_un_uso (empresa_id, usuario_id, email, proposito, token_hash, creado_en, "
                + "vence_en) values (?, ?, 'x@filas.test', 'RECUPERAR_CLAVE', ?, ?, ?)", empresaId, adminId,
                "vencido-" + empresaId, LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(29));
        // PR 49b: un desafio de passkey que nadie uso.
        jdbc.update("insert into desafios_webauthn (id_hash, proposito, opciones, creado_en) values (?, 'ENTRADA', "
                + "'{}', ?)", "vencido-" + empresaId, LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1));
        LimpiezaConfig limpieza = new LimpiezaConfig(limpiezaService, revisionService, intentosDeLogin,
                colaDeTrabajos, cuentaService, empresaService, limitesDePeticiones, desafiosWebAuthn,
                notificacionService);

        comoEnProduccion(() -> {
            limpieza.limpiar();
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from enlaces_de_un_uso where token_hash = ?", Integer.class,
                "vencido-" + empresaId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from desafios_webauthn where id_hash = ?", Integer.class,
                "vencido-" + empresaId)).isZero();
    }

    @Test
    @DisplayName("PR 63: una notificacion se crea en la tienda del pedido; el resumen del dia y la purga cruzan tiendas")
    void notificaciones() {
        comoEnProduccion(() -> {
            notificacionService.notificar(new NotificacionPedida(empresaId, TipoDeNotificacion.HILO_RESUELTO, null,
                    null, null, List.of(adminId)));
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from notificaciones where empresa_id = ? and usuario_id = ? "
                + "and envio = 'RESUMEN_DIARIO'", Integer.class, empresaId, adminId)).isOne();

        comoEnProduccion(() -> {
            new ResumenDeNotificacionesConfig(notificacionService).resumir();
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from trabajos where empresa_id = ? and tipo = "
                + "'notificaciones.correo' and datos like '%\"resumen\":true%'", Integer.class, empresaId)).isOne();

        jdbc.update("update notificaciones set leida_en = ? where empresa_id = ?", LocalDateTime.now().minusDays(91),
                empresaId);
        comoEnProduccion(() -> {
            new LimpiezaConfig(limpiezaService, revisionService, intentosDeLogin, colaDeTrabajos, cuentaService,
                    empresaService, limitesDePeticiones, desafiosWebAuthn, notificacionService).limpiar();
            return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from notificaciones where empresa_id = ?", Integer.class,
                empresaId)).isZero();
    }

    @Test
    @DisplayName("El reloj de la simulacion encuentra las tiendas en automatico y mueve cada una en su alcance")
    void relojDeLaSimulacion() {
        jdbc.update("update configuracion_tienda set modo_simulacion = 'AUTOMATICO' where empresa_id = ?", empresaId);
        int antes = jdbc.queryForObject("select reloj from configuracion_tienda where empresa_id = ?", Integer.class,
                empresaId);

        comoEnProduccion(() -> {
            new SimulacionConfig(configuracionTiendaService, simulacionService).mover();
            return null;
        });
        assertThat(jdbc.queryForObject("select reloj from configuracion_tienda where empresa_id = ?", Integer.class,
                empresaId)).isEqualTo(antes + 1);
    }

    /** Como corre en produccion un hilo que no dice de que tienda es: sin ninguna fila. */
    private static <T> T comoEnProduccion(Supplier<T> trabajo) {
        Alcance anterior = ContextoDeTienda.fijar(Alcance.NINGUNA);
        try {
            return trabajo.get();
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }
}
