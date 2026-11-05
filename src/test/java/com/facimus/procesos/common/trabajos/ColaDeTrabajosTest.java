package com.facimus.procesos.common.trabajos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.gestion.service.EmpresaService;

/**
 * D34: la cola de trabajos contra PostgreSQL. Los trabajos de prueba anotan lo que reciben o fallan siempre; el reloj
 * lo mueve la prueba. Nada corre por detras: cada prueba toma y corre los trabajos llamando a la cola.
 */
@SpringBootTest(properties = {"trabajos.espera-base=1m", "trabajos.espera-maxima=10m",
        "trabajos.maximo-en-curso-por-tienda=1", "trabajos.atascado-tras=15m", "trabajos.retencion=7d"})
@ActiveProfiles("test")
class ColaDeTrabajosTest {

    private static final AtomicInteger TIENDAS = new AtomicInteger();

    @TestConfiguration
    static class TrabajosYRelojDePrueba {

        @Bean
        Anotador anotador() {
            return new Anotador();
        }

        @Bean
        ManejadorDeTrabajo siempreFalla() {
            return new ManejadorDeTrabajo() {
                @Override
                public String tipo() {
                    return "siempre-falla";
                }

                @Override
                public void ejecutar(Trabajo trabajo) {
                    throw new IllegalStateException("el socio no contesta");
                }
            };
        }

        @Bean
        @Primary
        RelojMovil relojMovil() {
            return new RelojMovil();
        }
    }

    /** Anota los datos de cada trabajo que corre, para ver cuales corrieron y cuantas veces. */
    static final class Anotador implements ManejadorDeTrabajo {

        private final List<String> corridos = new CopyOnWriteArrayList<>();

        @Override
        public String tipo() {
            return "anotar";
        }

        @Override
        public void ejecutar(Trabajo trabajo) {
            corridos.add(trabajo.datos());
        }
    }

    @Autowired
    private ColaDeTrabajos cola;

    @Autowired
    private Anotador anotador;

    @Autowired
    private RelojMovil reloj;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private PlatformTransactionManager transacciones;

    @BeforeEach
    void vaciarLaCola() {
        jdbc.update("delete from trabajos");
        anotador.corridos.clear();
    }

    @Test
    @DisplayName("Un trabajo encolado se toma, corre con sus datos y queda hecho")
    void encolado_correYQuedaHecho() {
        long id = cola.encolar(null, "anotar", "{\"pedido\": 1001}");

        assertThat(cola.procesarUno()).isTrue();

        assertThat(anotador.corridos).containsExactly("{\"pedido\": 1001}");
        assertThat(fila(id)).containsEntry("estado", "HECHO").containsEntry("intentos", 1);
        assertThat(cola.procesarUno()).isFalse();
    }

    @Test
    @DisplayName("Un trabajo que falla dos veces reintenta despues de esperar y termina en los fallidos")
    void fallaDosVeces_reintentaYTerminaFallido() {
        long id = cola.encolar(null, "siempre-falla", "{}", 2);

        assertThat(cola.procesarUno()).isTrue();
        assertThat(fila(id)).containsEntry("estado", "PENDIENTE").containsEntry("intentos", 1);
        assertThat((String) fila(id).get("ultimo_error")).contains("el socio no contesta");
        assertThat(cola.procesarUno()).as("todavia espera").isFalse();

        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(cola.procesarUno()).isTrue();

        assertThat(fila(id)).containsEntry("estado", "FALLIDO").containsEntry("intentos", 2);
        assertThat(fila(id).get("terminado_en")).isNotNull();
    }

    @Test
    @DisplayName("La espera entre intentos se duplica en cada fallo, hasta el techo")
    void espera_seDuplicaHastaElTecho() {
        assertThat(cola.espera(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(cola.espera(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(cola.espera(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(cola.espera(5)).isEqualTo(Duration.ofMinutes(10));
        assertThat(cola.espera(80)).isEqualTo(Duration.ofMinutes(10));

        long id = cola.encolar(null, "siempre-falla", "{}", 3);
        cola.procesarUno();
        reloj.avanzar(Duration.ofMinutes(1));
        cola.procesarUno();

        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(cola.procesarUno()).as("tras el segundo fallo espera dos minutos").isFalse();
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(cola.procesarUno()).isTrue();
        assertThat(fila(id)).containsEntry("estado", "FALLIDO").containsEntry("intentos", 3);
    }

    @Test
    @DisplayName("Una tienda con un trabajo corriendo no toma otro, y la siguiente tienda no la espera")
    void topePorTienda_noFrenaALasDemas() {
        Long grande = nuevaTienda();
        Long chica = nuevaTienda();
        long primero = cola.encolar(grande, "anotar", "exportacion 1");
        long segundo = cola.encolar(grande, "anotar", "exportacion 2");
        long otro = cola.encolar(chica, "anotar", "factura");

        assertThat(cola.tomar()).map(Trabajo::id).contains(primero);
        assertThat(cola.tomar()).map(Trabajo::id).as("la grande ya tiene uno en curso").contains(otro);
        assertThat(cola.tomar()).isEmpty();

        jdbc.update("update trabajos set estado = 'HECHO' where id = ?", primero);
        assertThat(cola.tomar()).map(Trabajo::id).contains(segundo);
    }

    @Test
    @DisplayName("Varios trabajadores a la vez nunca toman el mismo trabajo, y ninguno queda sin correr")
    void variosTrabajadores_cadaTrabajoCorreUnaVez() throws Exception {
        List<String> encolados = new ArrayList<>();
        for (int numero = 0; numero < 40; numero++) {
            encolados.add("trabajo " + numero);
            cola.encolar(null, "anotar", "trabajo " + numero);
        }

        try (ExecutorService trabajadores = Executors.newFixedThreadPool(4)) {
            List<Future<?>> enCurso = new ArrayList<>();
            for (int trabajador = 0; trabajador < 4; trabajador++) {
                enCurso.add(trabajadores.submit(() -> {
                    while (cola.procesarUno()) {
                        Thread.onSpinWait();
                    }
                }));
            }
            for (Future<?> trabajador : enCurso) {
                trabajador.get();
            }
        }

        assertThat(anotador.corridos).hasSize(40).containsExactlyInAnyOrderElementsOf(encolados);
        assertThat(jdbc.queryForObject("select count(*) from trabajos where estado = 'HECHO'", Integer.class))
                .isEqualTo(40);
    }

    @Test
    @DisplayName("Un trabajo encolado en una transaccion que se deshace no existe")
    void transaccionDeshecha_noDejaTrabajo() {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            cola.encolar(null, "anotar", "nunca");
            estado.setRollbackOnly();
        });

        assertThat(cola.procesarUno()).isFalse();
        assertThat(anotador.corridos).isEmpty();
    }

    @Test
    @DisplayName("Un tipo sin manejador queda fallido al primer intento, con el motivo")
    void tipoSinManejador_quedaFallido() {
        long id = cola.encolar(null, "nadie-lo-sabe-hacer", "{}");

        cola.procesarUno();

        assertThat(fila(id)).containsEntry("estado", "FALLIDO").containsEntry("intentos", 1);
        assertThat((String) fila(id).get("ultimo_error")).contains("nadie-lo-sabe-hacer");
    }

    @Test
    @DisplayName("Lo que una instancia dejo a medias vuelve a la cola, o queda fallido si no le quedan intentos")
    void atascados_vuelvenALaColaOFallan() {
        long conIntentos = cola.encolar(null, "anotar", "a medias", 3);
        long sinIntentos = cola.encolar(null, "anotar", "a medias y sin intentos", 1);
        cola.tomar();
        cola.tomar();

        reloj.avanzar(Duration.ofMinutes(10));
        assertThat(cola.reabrirAtascados()).as("todavia no pasaron 15 minutos").isZero();
        reloj.avanzar(Duration.ofMinutes(6));
        assertThat(cola.reabrirAtascados()).isEqualTo(2);

        assertThat(fila(conIntentos)).containsEntry("estado", "PENDIENTE");
        assertThat(fila(sinIntentos)).containsEntry("estado", "FALLIDO");
        assertThat(cola.procesarUno()).isTrue();
        assertThat(anotador.corridos).containsExactly("a medias");
    }

    @Test
    @DisplayName("La purga se lleva los trabajos terminados hace mas de una semana y deja los demas")
    void purga_seLlevaLosTerminadosViejos() {
        long viejo = cola.encolar(null, "anotar", "viejo");
        cola.procesarUno();
        reloj.avanzar(Duration.ofDays(8));
        long reciente = cola.encolar(null, "anotar", "reciente");
        cola.procesarUno();
        long pendiente = cola.encolar(null, "anotar", "pendiente");
        reloj.avanzar(Duration.ofHours(1));

        assertThat(cola.olvidarTerminados()).isEqualTo(1);

        assertThat(jdbc.queryForList("select id from trabajos order by id", Long.class))
                .containsExactly(reciente, pendiente)
                .doesNotContain(viejo);
    }

    private Map<String, Object> fila(long id) {
        return jdbc.queryForMap("select * from trabajos where id = ?", id);
    }

    private Long nuevaTienda() {
        int numero = TIENDAS.incrementAndGet();
        Long id = empresaService.registrar("Tienda de la cola " + numero, "9007" + numero + "000-1",
                "contacto" + numero + "@cola.com", "Administradora", "admin" + numero + "@cola.com",
                "clave-de-la-cola").id();
        // El registro deja en la cola el correo de verificacion: esta prueba mira solo los trabajos que encola.
        jdbc.update("delete from trabajos where empresa_id = ?", id);
        return id;
    }

    /** Un reloj que solo avanza cuando la prueba lo dice. */
    static final class RelojMovil extends Clock {

        private Instant ahora = Instant.parse("2026-10-01T12:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }
}
