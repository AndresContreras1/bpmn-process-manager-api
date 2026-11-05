package com.facimus.procesos.modelado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.EmpresaService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.dto.response.HallazgoResponse;
import com.facimus.procesos.modelado.dto.response.RevisionResponse;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.Severidad;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.RevisionIaRepository;

/**
 * Las revisiones con IA guardadas en la base (D34): cuando una se devuelve otra vez, cuando se vuelve a preguntar y
 * como cuentan para el limite de la tienda. El modelo es un revisor de prueba que cuenta sus llamadas, y el reloj, uno
 * que la prueba mueve.
 */
@SpringBootTest(properties = {"revision.max-reviews=2", "revision.window=1h"})
@ActiveProfiles("test")
class RevisionesIaIntegracionTest {

    private static final AtomicInteger TIENDAS = new AtomicInteger();

    @TestConfiguration
    static class RevisorYRelojDePrueba {

        @Bean
        @Primary
        RevisorContado revisorContado() {
            return new RevisorContado();
        }

        @Bean
        @Primary
        RelojMovil relojMovil() {
            return new RelojMovil();
        }
    }

    @Autowired
    private RevisionService revisionService;

    @Autowired
    private RevisionIaRepository revisiones;

    @Autowired
    private RevisorContado revisor;

    @Autowired
    private RelojMovil reloj;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private PoolService poolService;

    @Test
    @DisplayName("Pedir dos veces la revision de un diagrama que no cambio devuelve la misma, sin llamar al modelo")
    void mismoDiagrama_reutilizaLaRevision() {
        Tienda tienda = nuevaTienda();
        Long procesoId = tienda.proceso("Order fulfillment");
        int antes = revisor.llamadas();

        RevisionResponse primera = revisionService.revisar(tienda.id(), procesoId);
        RevisionResponse segunda = revisionService.revisar(tienda.id(), procesoId);

        assertThat(primera.reutilizada()).isFalse();
        assertThat(segunda.reutilizada()).isTrue();
        assertThat(segunda.resumen()).isEqualTo(primera.resumen());
        assertThat(segunda.hallazgos()).isEqualTo(primera.hallazgos());
        assertThat(segunda.fecha()).isEqualTo(primera.fecha());
        assertThat(revisor.llamadas() - antes).isEqualTo(1);
    }

    @Test
    @DisplayName("Si el diagrama cambio, la revision guardada ya no sirve y se vuelve a preguntar")
    void diagramaCambiado_vuelveAPreguntar() {
        Tienda tienda = nuevaTienda();
        Long procesoId = tienda.proceso("Order fulfillment");
        int antes = revisor.llamadas();

        revisionService.revisar(tienda.id(), procesoId);
        tienda.cambiarDiagrama(procesoId, "Carrier");
        RevisionResponse segunda = revisionService.revisar(tienda.id(), procesoId);

        assertThat(segunda.reutilizada()).isFalse();
        assertThat(revisor.llamadas() - antes).isEqualTo(2);
    }

    @Test
    @DisplayName("Pasado el limite de la tienda, la siguiente revision responde 429 con cuanto hay que esperar")
    void pasadoElLimite_esperaHastaQueSalgaLaMasVieja() {
        Tienda tienda = nuevaTienda();
        Long procesoId = tienda.proceso("Order fulfillment");
        revisionService.revisar(tienda.id(), procesoId);
        tienda.cambiarDiagrama(procesoId, "Carrier");
        revisionService.revisar(tienda.id(), procesoId);
        tienda.cambiarDiagrama(procesoId, "Payment gateway");

        assertThatThrownBy(() -> revisionService.revisar(tienda.id(), procesoId))
                .isInstanceOf(DemasiadosIntentosException.class)
                .hasMessageContaining("revisiones con IA")
                .extracting(fallo -> ((DemasiadosIntentosException) fallo).getSegundosDeEspera())
                .isEqualTo(3600L);

        reloj.avanzar(Duration.ofHours(1));
        assertThat(revisionService.revisar(tienda.id(), procesoId).reutilizada()).isFalse();
    }

    @Test
    @DisplayName("Reutilizar una revision no gasta del limite: el diagrama no cambio y no hubo llamada")
    void reutilizada_noGastaDelLimite() {
        Tienda tienda = nuevaTienda();
        Long procesoId = tienda.proceso("Order fulfillment");
        revisionService.revisar(tienda.id(), procesoId);

        assertThat(revisionService.revisar(tienda.id(), procesoId).reutilizada()).isTrue();
        assertThat(revisionService.revisar(tienda.id(), procesoId).reutilizada()).isTrue();
        tienda.cambiarDiagrama(procesoId, "Carrier");
        assertThat(revisionService.revisar(tienda.id(), procesoId).reutilizada()).isFalse();
    }

    @Test
    @DisplayName("La purga olvida las revisiones superadas que salieron de la ventana, y la ultima sigue sirviendo")
    void purga_olvidaLasSuperadasYLaUltimaSigueSirviendo() {
        Tienda tienda = nuevaTienda();
        Long procesoId = tienda.proceso("Order fulfillment");
        revisionService.revisar(tienda.id(), procesoId);
        tienda.cambiarDiagrama(procesoId, "Carrier");
        reloj.avanzar(Duration.ofMinutes(1));
        revisionService.revisar(tienda.id(), procesoId);

        reloj.avanzar(Duration.ofHours(2));
        revisionService.olvidarSuperadas();

        assertThat(revisiones.findAllByEmpresaId(tienda.id())).hasSize(1);
        assertThat(revisionService.revisar(tienda.id(), procesoId).reutilizada()).isTrue();
    }

    private Tienda nuevaTienda() {
        int numero = TIENDAS.incrementAndGet();
        String admin = "admin" + numero + "@revisiones-ia.com";
        Long empresaId = empresaService.registrar("Tienda revisada " + numero, "9006" + numero + "000-1",
                "contacto" + numero + "@revisiones-ia.com", "Administradora", admin, "clave-de-la-revision").id();
        return new Tienda(empresaId, usuarioRepository.findByEmail(admin).orElseThrow().getId());
    }

    private final class Tienda {

        private final Long id;
        private final Long adminId;

        private Tienda(Long id, Long adminId) {
            this.id = id;
            this.adminId = adminId;
        }

        Long id() {
            return id;
        }

        Long proceso(String nombre) {
            return procesoService.crear(id, adminId, nombre, "Lo que el modelo revisa", "Ventas").id();
        }

        /** Un participante mas cambia el diagrama contado en texto, y con el su huella. */
        void cambiarDiagrama(Long procesoId, String participante) {
            poolService.crear(id, adminId, procesoId, participante, TipoParticipante.PROVEEDOR, true,
                    Integracion.NINGUNA);
        }
    }

    /** El modelo de prueba: siempre contesta lo mismo y cuenta cuantas veces lo llamaron. */
    static final class RevisorContado implements RevisorDeDiagramas {

        private final AtomicInteger llamadas = new AtomicInteger();

        @Override
        public boolean estaConfigurado() {
            return true;
        }

        @Override
        public Dictamen revisar(String diagrama) {
            llamadas.incrementAndGet();
            return new Dictamen("Falta el caso de error.", List.of(new HallazgoResponse(Severidad.MEDIA,
                    "Gateway: Stock available?", "Una salida sin condicion", "Darle una condicion")));
        }

        int llamadas() {
            return llamadas.get();
        }
    }

    /** Un reloj que solo avanza cuando la prueba lo dice. */
    static final class RelojMovil extends Clock {

        /** Con nanosegundos, como el reloj del sistema: PostgreSQL solo guarda microsegundos. */
        private Instant ahora = Instant.parse("2026-10-01T12:00:00.123456789Z");

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
