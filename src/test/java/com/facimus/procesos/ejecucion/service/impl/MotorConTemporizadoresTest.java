package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.metricas.MetricasDeNegocio;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.ClaseTemporizador;
import com.facimus.procesos.ejecucion.model.Delegacion;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.model.EstadoTemporizador;
import com.facimus.procesos.ejecucion.model.EventoCaso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.Temporizador;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.DelegacionRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorVencido;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.Pais;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.gestion.service.CalendarioService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 68: los temporizadores en el motor, sobre la base de verdad y con un reloj que la prueba mueve a mano: una espera,
 * un borde que interrumpe y otro que avisa, un inicio temporizado, y lo que pasa cuando el caso o el paso ya no estan.
 * Quien dispara es el mismo componente que usan el planificador y el tick; aqui se le llama directamente.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class MotorConTemporizadoresTest {

    private static final Long VENDEDORES = 3L;
    private static final int TICK = 3;
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** Viernes 9 de enero de 2026 a las tres de la tarde en Bogota; el lunes 12 es festivo (Reyes Magos). */
    private static final Instant VIERNES = LocalDateTime.parse("2026-01-09T15:00:00").atZone(BOGOTA).toInstant();
    private static final CalendarioLaboral COLOMBIA = new CalendarioLaboral(Pais.CO, BOGOTA, LocalTime.of(8, 0),
            LocalTime.of(17, 0), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Map.of(), Set.of());

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ActividadCasoRepository actividadCasoRepository;
    @Autowired
    private EventoCasoRepository eventoCasoRepository;
    @Autowired
    private MensajeSalienteRepository mensajeSalienteRepository;
    @Autowired
    private TemporizadorRepository temporizadorRepository;
    @Autowired
    private CasoRepository casoRepository;
    @Autowired
    private DelegacionRepository delegacionRepository;

    private final RelojMovil reloj = new RelojMovil();
    private final VersionService versionService = mock(VersionService.class);
    private final GrafosDeVersion grafos = mock(GrafosDeVersion.class);

    private CalendarioService calendarioService;
    private MotorDeProcesos motor;
    private Temporizadores temporizadores;
    private DisparoDeTemporizadores disparo;
    private Empresa tienda;
    private Proceso proceso;
    private VersionProceso version;

    /** Un reloj que solo se mueve cuando la prueba lo pide. */
    static final class RelojMovil extends Clock {

        private Instant ahora = VIERNES;

        void irA(Instant momento) {
            ahora = momento;
        }

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
            throw new UnsupportedOperationException("El reloj de la prueba va en UTC.");
        }
    }

    @BeforeEach
    void armarElMotor() {
        JsonMapper json = JsonMapper.builder().build();
        Bitacora bitacora = new Bitacora(eventoCasoRepository, new MetricasDeNegocio(new SimpleMeterRegistry()));
        ParametrosDeLaTienda parametros = mock(ParametrosDeLaTienda.class);
        given(parametros.de(any())).willReturn(ParametrosDeSimulacion.deFabrica());
        calendarioService = mock(CalendarioService.class);
        given(calendarioService.laboral(any())).willReturn(COLOMBIA);
        RelojDeLaTienda relojDeLaTienda = mock(RelojDeLaTienda.class);
        given(relojDeLaTienda.ahora(any())).willReturn(TICK);
        temporizadores = new Temporizadores(temporizadorRepository, actividadCasoRepository, calendarioService,
                bitacora, reloj);
        motor = new MotorDeProcesos(actividadCasoRepository, new BandejaDeSalida(mensajeSalienteRepository,
                new SociosSimulados(List.of()), parametros, bitacora, json), bitacora, json,
                TestObservationRegistry.create(), temporizadores, new AsignacionAlCrear(mock(PersonasDeLaTarea.class)),
                new Tareas(actividadCasoRepository, casoRepository, delegacionRepository));
        disparo = new DisparoDeTemporizadores(temporizadorRepository, casoRepository, actividadCasoRepository,
                versionService, grafos, motor, temporizadores, relojDeLaTienda, reloj);
        tienda = em.persistFlushFind(Empresa.builder().nombre("Tienda de los relojes").nit("900765432-1")
                .correoContacto("relojes@demo.com").fechaRegistro(LocalDate.now()).build());
        proceso = em.persistFlushFind(Proceso.builder().empresa(tienda).nombre("Order fulfillment")
                .descripcion("Checkout to delivery").categoria("Fulfillment").estado(EstadoProceso.PUBLICADO)
                .activo(true).build());
        version = em.persistFlushFind(VersionProceso.builder().empresa(tienda).proceso(proceso).numero(1)
                .estado(EstadoVersion.VIGENTE).fechaPublicacion(LocalDateTime.now())
                .huella("1f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123").definicion("{}")
                .modeloBpmn("<bpmn:definitions/>").build());
    }

    @Nested
    @DisplayName("Una espera")
    class Espera {

        @Test
        @DisplayName("En produccion espera dos dias habiles con el reloj de pared, y al vencer el caso sigue")
        void produccion_esperaYSigue() {
            DiagramaArmado armado = esperaDe(TipoTemporizador.DURACION_HABIL, "P2D");
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);

            assertThat(pasoPor(caso, "Wait").getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            assertThat(espera.getClase()).isEqualTo(ClaseTemporizador.INTERMEDIO);
            assertThat(espera.getModo()).isEqualTo(ModoCaso.PRODUCCION);
            assertThat(espera.getActividadCasoId()).isEqualTo(pasoPor(caso, "Wait").getId());
            // Viernes a las tres + 18 horas habiles, sin el lunes festivo: el miercoles 14 a las tres
            assertThat(espera.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-14T20:00:00"));
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);

            reloj.irA(Instant.parse("2026-01-14T19:59:59Z"));
            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();
            reloj.irA(Instant.parse("2026-01-14T20:00:00Z"));
            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isTrue();

            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.DISPARADO);
            assertThat(espera.getDisparos()).isOne();
            assertThat(espera.getDisparadoEn()).isEqualTo(LocalDateTime.parse("2026-01-14T20:00:00"));
            assertThat(pasoPor(caso, "Wait").getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(tiposDeLaBitacora(caso)).containsSubsequence(TipoEventoCaso.TEMPORIZADOR_PROGRAMADO,
                    TipoEventoCaso.TEMPORIZADOR_DISPARADO, TipoEventoCaso.CASO_TERMINADO);
            assertThat(detalles(caso)).anyMatch(detalle -> detalle.equals(
                    "\"Wait\" vence el 2026-01-14 15:00 (America/Bogota)."));
        }

        @Test
        @DisplayName("Un temporizador ya disparado no salta otra vez, aunque se lo vuelva a pedir")
        void produccion_unaSolaVez() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            reloj.avanzar(Duration.ofDays(30));

            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isTrue();
            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();
            assertThat(eventoCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId())
                    .stream().filter(evento -> evento.getTipo() == TipoEventoCaso.TEMPORIZADOR_DISPARADO)).hasSize(1);
        }

        @Test
        @DisplayName("En prueba espera ticks: PT2H son dos, y el que se pide en otro modo no lo toma")
        void prueba_enTicks() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT2H"), ModoCaso.PRUEBA);
            Temporizador espera = unico(caso);

            assertThat(espera.getVenceTick()).isEqualTo(TICK + 2);
            assertThat(espera.getVenceEn()).isNull();
            assertThat(detalles(caso)).contains("\"Wait\" vence en el tick 5.");
            reloj.avanzar(Duration.ofDays(30));
            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();
            assertThat(disparo.enPrueba(tienda.getId(), espera.getId(), TICK + 1)).isFalse();
            assertThat(disparo.enPrueba(tienda.getId(), espera.getId(), TICK + 2)).isTrue();

            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("Una espera que no se entiende no se arma: queda anotado y el caso sigue de largo")
        void sinExpresionLegible_sigueDeLargo() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "dos horas"), ModoCaso.PRODUCCION);

            assertThat(temporizadoresDe(caso)).isEmpty();
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(detalles(caso)).anyMatch(detalle -> detalle.startsWith("\"Wait\" no se arma:"));
        }

        @Test
        @DisplayName("Una espera que no vence en diez anos con el calendario de la tienda no se arma ni tumba el caso")
        void cuentaQueSeSale_noSeArma() {
            given(calendarioService.laboral(any())).willReturn(new CalendarioLaboral(Pais.CO, BOGOTA,
                    LocalTime.of(8, 0), LocalTime.of(8, 1), EnumSet.of(DayOfWeek.MONDAY), Map.of(), Set.of()));
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION_HABIL, "PT8000H"), ModoCaso.PRODUCCION);

            assertThat(temporizadoresDe(caso)).isEmpty();
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(detalles(caso)).anyMatch(detalle -> detalle.startsWith("\"Wait\" no se arma:")
                    && detalle.contains("no vence dentro de lo que se puede contar"));
        }

        @Test
        @DisplayName("Un salto despues de un fallo deja los intentos en cero y borra el error")
        void saltoDespuesDeUnFallo_empiezaDeCero() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            disparo.anotarFallo(tienda.getId(), espera.getId(), "fallo");
            assertThat(espera.getIntentos()).isOne();
            reloj.avanzar(Duration.ofHours(2));

            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isTrue();

            assertThat(espera.getIntentos()).isZero();
            assertThat(espera.getUltimoError()).isNull();
        }

        @Test
        @DisplayName("Una espera sin expresion no espera nada")
        void sinExpresion_sigueDeLargo() {
            Caso caso = arrancar(esperaDe(null, null), ModoCaso.PRODUCCION);

            assertThat(temporizadoresDe(caso)).isEmpty();
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }
    }

    @Nested
    @DisplayName("Un borde")
    class Borde {

        @Test
        @DisplayName("El que interrumpe saca a la tarea de la bandeja y el caso sigue por el borde")
        void interrumpe() {
            DiagramaArmado armado = tareaConBorde();
            armado.temporizador(VENTAS, "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, "PT2H",
                    "Pick items");
            armado.evento(VENTAS, "Escalated", TipoEvento.FIN);
            armado.arco("Late", "Escalated");
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            Temporizador tarde = unico(caso);

            assertThat(tarde.getClase()).isEqualTo(ClaseTemporizador.BORDE);
            assertThat(tarde.getInterrumpe()).isTrue();
            assertThat(tarde.getActividadCasoId()).isEqualTo(pasoPor(caso, "Pick items").getId());
            reloj.avanzar(Duration.ofHours(2));
            assertThat(disparo.enProduccion(tienda.getId(), tarde.getId())).isTrue();

            assertThat(pasoPor(caso, "Pick items").getEstado()).isEqualTo(EstadoActividadCaso.OMITIDA);
            assertThat(pasoPor(caso, "Pick items").getTickFin()).isEqualTo(TICK);
            assertThat(pasoPor(caso, "Escalated").getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(tarde.getEstado()).isEqualTo(EstadoTemporizador.DISPARADO);
            assertThat(detalles(caso)).anyMatch(detalle -> detalle.contains("que se interrumpe"));
        }

        @Test
        @DisplayName("PR 70: el que interrumpe una tarea delegada anula su delegacion: ya no vuelve a nadie")
        void interrumpe_anulaLaDelegacion() {
            DiagramaArmado armado = tareaConBorde();
            armado.temporizador(VENTAS, "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, "PT2H",
                    "Pick items");
            armado.evento(VENTAS, "Escalated", TipoEvento.FIN);
            armado.arco("Late", "Escalated");
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            Usuario ana = persona("Ana");
            Usuario luis = persona("Luis");
            ActividadCaso tarea = pasoPor(caso, "Pick items");
            tarea.setAsignadoA(luis.getId());
            tarea.setDelegadaPor(ana.getId());
            actividadCasoRepository.save(tarea);
            Delegacion abierta = delegacionRepository.saveAndFlush(Delegacion.builder().empresa(tienda).tarea(tarea)
                    .caso(caso).delegadaPor(ana.getId()).delegadaA(luis.getId()).motivo("Vacaciones")
                    .estado(EstadoDelegacion.ACTIVA).build());
            reloj.avanzar(Duration.ofHours(2));

            assertThat(disparo.enProduccion(tienda.getId(), unico(caso).getId())).isTrue();
            em.flush();

            assertThat(pasoPor(caso, "Pick items").getEstado()).isEqualTo(EstadoActividadCaso.OMITIDA);
            Delegacion cerrada = delegacionRepository.findById(abierta.getId()).orElseThrow();
            assertThat(cerrada.getEstado()).isEqualTo(EstadoDelegacion.ANULADA);
            assertThat(cerrada.getCerradaEn()).isNotNull();
        }

        @Test
        @DisplayName("El que no interrumpe avisa cada vez mientras la tarea espera, y deja de avisar al completarla")
        void noInterrumpe_avisaYSeSuelta() {
            DiagramaArmado armado = tareaConBorde();
            armado.bordeQueNoInterrumpe(VENTAS, "Remind", TipoTemporizador.CICLO, "R3/PT1H", "Pick items");
            armado.actividad(VENTAS, "Send reminder", TipoActividad.SERVICIO);
            armado.evento(VENTAS, "Reminded", TipoEvento.FIN);
            armado.arco("Remind", "Send reminder");
            armado.arco("Send reminder", "Reminded");
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            Temporizador aviso = unico(caso);

            assertThat(aviso.getInterrumpe()).isFalse();
            assertThat(aviso.getRepeticiones()).isEqualTo(3);
            reloj.avanzar(Duration.ofHours(1));
            assertThat(disparo.enProduccion(tienda.getId(), aviso.getId())).isTrue();

            assertThat(pasoPor(caso, "Pick items").getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            assertThat(pasoPor(caso, "Reminded").getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
            assertThat(aviso.getEstado()).isEqualTo(EstadoTemporizador.PROGRAMADO);
            assertThat(aviso.getDisparos()).isOne();
            assertThat(aviso.getVenceEn()).isEqualTo(Temporizadores.utc(VIERNES.plus(Duration.ofHours(2))));

            completarTarea(caso, grafo, "Pick items");

            assertThat(aviso.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }

        @Test
        @DisplayName("El ciclo de un borde que no interrumpe salta sus veces y se termina")
        void noInterrumpe_cicloQueSeAcaba() {
            DiagramaArmado armado = tareaConBorde();
            armado.bordeQueNoInterrumpe(VENTAS, "Remind", TipoTemporizador.CICLO, "R2/PT1H", "Pick items");
            armado.evento(VENTAS, "Reminded", TipoEvento.FIN);
            armado.arco("Remind", "Reminded");
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            Temporizador aviso = unico(caso);

            reloj.avanzar(Duration.ofHours(1));
            disparo.enProduccion(tienda.getId(), aviso.getId());
            reloj.avanzar(Duration.ofHours(1));
            disparo.enProduccion(tienda.getId(), aviso.getId());

            assertThat(aviso.getDisparos()).isEqualTo(2);
            assertThat(aviso.getEstado()).isEqualTo(EstadoTemporizador.DISPARADO);
            assertThat(pasoPor(caso, "Pick items").getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
        }

        @Test
        @DisplayName("En prueba el ciclo de un borde vuelve a vencer cada tanto en ticks, sus veces")
        void noInterrumpe_cicloEnPrueba() {
            DiagramaArmado armado = tareaConBorde();
            armado.bordeQueNoInterrumpe(VENTAS, "Remind", TipoTemporizador.CICLO, "R2/PT2H", "Pick items");
            armado.evento(VENTAS, "Reminded", TipoEvento.FIN);
            armado.arco("Remind", "Reminded");
            Caso caso = arrancar(armado, ModoCaso.PRUEBA);
            Temporizador aviso = unico(caso);

            assertThat(aviso.getVenceTick()).isEqualTo(TICK + 2);
            assertThat(disparo.enPrueba(tienda.getId(), aviso.getId(), 10)).isTrue();
            assertThat(aviso.getVenceTick()).isEqualTo(12);
            assertThat(aviso.getEstado()).isEqualTo(EstadoTemporizador.PROGRAMADO);
            assertThat(disparo.enPrueba(tienda.getId(), aviso.getId(), 12)).isTrue();
            assertThat(aviso.getEstado()).isEqualTo(EstadoTemporizador.DISPARADO);
        }

        @Test
        @DisplayName("Un ciclo de borde sin vueltas por delante no se arma")
        void cicloSinVueltas_noSeArma() {
            DiagramaArmado armado = tareaConBorde();
            armado.bordeQueNoInterrumpe(VENTAS, "Remind", TipoTemporizador.CICLO, "R1/2020-01-01T00:00:00Z/P1D",
                    "Pick items");
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);

            assertThat(temporizadoresDe(caso)).isEmpty();
            assertThat(detalles(caso)).contains("\"Remind\" no tiene ninguna vuelta por delante: no se arma.");
        }

        @Test
        @DisplayName("Una tarea completada antes de tiempo suelta su borde: ya no hay que avisar")
        void tareaCompletada_sueltaElBorde() {
            DiagramaArmado armado = tareaConBorde();
            armado.temporizador(VENTAS, "Late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, "PT2H",
                    "Pick items");
            armado.evento(VENTAS, "Escalated", TipoEvento.FIN);
            armado.arco("Late", "Escalated");
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            Temporizador tarde = unico(caso);

            completarTarea(caso, grafo, "Pick items");
            reloj.avanzar(Duration.ofHours(3));

            assertThat(tarde.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
            assertThat(disparo.enProduccion(tienda.getId(), tarde.getId())).isFalse();
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
        }
    }

    @Nested
    @DisplayName("Lo que ya no esta")
    class LoQueYaNoEsta {

        @Test
        @DisplayName("Cancelar el caso cancela sus temporizadores")
        void casoCancelado() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);

            motor.apagarTokens(caso, Momento.en(TICK));

            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
        }

        @Test
        @DisplayName("Uno cuyo caso ya se cerro por otro lado se cancela al tomarlo, sin mover nada")
        void casoCerrado() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            caso.setEstado(EstadoCaso.CANCELADO);
            reloj.avanzar(Duration.ofHours(2));

            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();

            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
            assertThat(pasoPor(caso, "Wait").getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
        }

        @Test
        @DisplayName("Uno de un caso en error no salta ni se cancela: espera a que lo rescaten")
        void casoEnError() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            caso.setEstado(EstadoCaso.ERROR);
            reloj.avanzar(Duration.ofHours(2));

            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();

            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.PROGRAMADO);
        }

        @Test
        @DisplayName("Uno cuyo paso ya no espera sobraba: se cancela")
        void pasoQueYaNoEspera() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            pasoPor(caso, "Wait").setEstado(EstadoActividadCaso.OMITIDA);
            reloj.avanzar(Duration.ofHours(2));

            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();

            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
        }

        @Test
        @DisplayName("Uno cuyo evento ya no esta en la version queda fallido, con el error")
        void eventoQueNoEsta() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            DiagramaArmado otra = DiagramaArmado.reciennacido();
            otra.lane(TIENDA, VENTAS, VENDEDORES);
            otra.evento(VENTAS, "Start", TipoEvento.INICIO);
            given(grafos.del(any(), any())).willReturn(GrafoDeVersion.de(otra.diagrama()));
            reloj.avanzar(Duration.ofHours(2));

            assertThat(disparo.enProduccion(tienda.getId(), espera.getId())).isFalse();

            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.FALLIDO);
            assertThat(espera.getUltimoError()).contains("no esta en la version");
        }

        @Test
        @DisplayName("Un fallo se anota y se aplaza; al tercero queda fallido")
        void fallos() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRODUCCION);
            Temporizador espera = unico(caso);
            reloj.irA(Instant.parse("2026-01-10T00:00:00Z"));

            disparo.anotarFallo(tienda.getId(), espera.getId(), "IllegalStateException: " + "x".repeat(600));

            assertThat(espera.getIntentos()).isOne();
            assertThat(espera.getUltimoError()).hasSize(500).startsWith("IllegalStateException: ");
            assertThat(espera.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-10T00:01:00"));
            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.PROGRAMADO);
            disparo.anotarFallo(tienda.getId(), espera.getId(), "corto");
            assertThat(espera.getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-10T00:02:00"));
            assertThat(espera.getUltimoError()).isEqualTo("corto");
            disparo.anotarFallo(tienda.getId(), espera.getId(), "otra vez");
            assertThat(espera.getIntentos()).isEqualTo(DisparoDeTemporizadores.INTENTOS);
            assertThat(espera.getEstado()).isEqualTo(EstadoTemporizador.FALLIDO);
        }

        @Test
        @DisplayName("Uno de prueba que falla se aplaza un tick")
        void fallos_enPrueba() {
            Caso caso = arrancar(esperaDe(TipoTemporizador.DURACION, "PT1H"), ModoCaso.PRUEBA);
            Temporizador espera = unico(caso);

            disparo.anotarFallo(tienda.getId(), espera.getId(), "fallo");

            assertThat(espera.getVenceTick()).isEqualTo(TICK + 2);
        }
    }

    @Nested
    @DisplayName("Un inicio temporizado")
    class Inicio {

        @Test
        @DisplayName("Abre un caso de produccion sobre su version, empezando por el, y vuelve a armarse")
        void abreUnCaso() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.temporizador(VENTAS, "Every hour", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO,
                    "R2/PT1H", null);
            armado.actividad(VENTAS, "Count stock", TipoActividad.USUARIO);
            armado.arco("Every hour", "Count stock");
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            given(grafos.del(any(), any())).willReturn(grafo);
            given(versionService.vigente(tienda.getId(), proceso.getId())).willReturn(Optional.of(version));

            assertThat(temporizadores.armarInicio(version, grafo.iniciosTemporizados().getFirst())).isTrue();
            Temporizador inicio = temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseOrderByIdDesc(
                    proceso.getId(), tienda.getId(), ClaseTemporizador.INICIO, PageRequest.of(0, 1)).getContent()
                    .getFirst();
            assertThat(inicio.getCasoId()).isNull();
            assertThat(inicio.getModo()).isEqualTo(ModoCaso.PRODUCCION);
            assertThat(inicio.getVenceEn()).isEqualTo(Temporizadores.utc(VIERNES.plus(Duration.ofHours(1))));

            reloj.avanzar(Duration.ofHours(1));
            assertThat(disparo.enProduccion(tienda.getId(), inicio.getId())).isTrue();

            List<Caso> casos = casoRepository.findAll().stream()
                    .filter(caso -> caso.getVersionProceso().getId().equals(version.getId())).toList();
            assertThat(casos).singleElement().satisfies(caso -> {
                assertThat(caso.getModo()).isEqualTo(ModoCaso.PRODUCCION);
                assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
                assertThat(pasoPor(caso, "Every hour").getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
                assertThat(pasoPor(caso, "Count stock").getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            });
            assertThat(inicio.getEstado()).isEqualTo(EstadoTemporizador.PROGRAMADO);
            assertThat(inicio.getVenceEn()).isEqualTo(Temporizadores.utc(VIERNES.plus(Duration.ofHours(2))));
        }

        @Test
        @DisplayName("El de una version que ya no es la vigente se cancela sin abrir nada")
        void versionQueYaNoEsLaVigente() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.temporizador(VENTAS, "Once", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.FECHA,
                    "2026-01-09T21:00:00Z", null);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            given(versionService.vigente(tienda.getId(), proceso.getId())).willReturn(Optional.empty());
            temporizadores.armarInicio(version, grafo.iniciosTemporizados().getFirst());
            Temporizador inicio = temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseOrderByIdDesc(
                    proceso.getId(), tienda.getId(), ClaseTemporizador.INICIO, PageRequest.of(0, 1)).getContent()
                    .getFirst();
            reloj.avanzar(Duration.ofHours(7));

            assertThat(disparo.enProduccion(tienda.getId(), inicio.getId())).isFalse();

            assertThat(inicio.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
        }

        @Test
        @DisplayName("El de una version que otra reemplazo se cancela sin abrir un caso en la nueva")
        void versionReemplazada() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.temporizador(VENTAS, "Once", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.FECHA,
                    "2026-01-09T21:00:00Z", null);
            armado.actividad(VENTAS, "Count stock", TipoActividad.USUARIO);
            armado.arco("Once", "Count stock");
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            given(grafos.del(any(), any())).willReturn(grafo);
            VersionProceso nueva = em.persistFlushFind(VersionProceso.builder().empresa(tienda).proceso(proceso)
                    .numero(2).estado(EstadoVersion.VIGENTE).fechaPublicacion(LocalDateTime.now())
                    .huella("2f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0123").definicion("{}")
                    .modeloBpmn("<bpmn:definitions/>").build());
            given(versionService.vigente(tienda.getId(), proceso.getId())).willReturn(Optional.of(nueva));
            temporizadores.armarInicio(version, grafo.iniciosTemporizados().getFirst());
            Temporizador inicio = inicioDelProceso();
            reloj.avanzar(Duration.ofHours(7));

            assertThat(disparo.enProduccion(tienda.getId(), inicio.getId())).isFalse();

            assertThat(inicio.getEstado()).isEqualTo(EstadoTemporizador.CANCELADO);
            assertThat(casosDelProceso()).isEmpty();
        }

        @Test
        @DisplayName("Uno cuya version vigente es documental queda fallido sin abrir nada")
        void versionDocumental() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.temporizador(VENTAS, "Once", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.FECHA,
                    "2026-01-09T21:00:00Z", null);
            armado.actividad(VENTAS, "Count stock", TipoActividad.USUARIO);
            armado.arco("Once", "Count stock");
            GrafoDeVersion ejecutable = GrafoDeVersion.de(armado.diagrama());
            armado.modo(ModoProceso.DOCUMENTAL);
            given(grafos.del(any(), any())).willReturn(GrafoDeVersion.de(armado.diagrama()));
            given(versionService.vigente(tienda.getId(), proceso.getId())).willReturn(Optional.of(version));
            temporizadores.armarInicio(version, ejecutable.iniciosTemporizados().getFirst());
            Temporizador inicio = inicioDelProceso();
            reloj.avanzar(Duration.ofHours(7));

            assertThat(disparo.enProduccion(tienda.getId(), inicio.getId())).isFalse();

            assertThat(inicio.getEstado()).isEqualTo(EstadoTemporizador.FALLIDO);
            assertThat(inicio.getUltimoError()).contains("ya no abre casos");
            assertThat(casosDelProceso()).isEmpty();
        }

        @Test
        @DisplayName("Un inicio cuya expresion no se entiende no se arma")
        void sinExpresionLegible() {
            DiagramaArmado armado = DiagramaArmado.reciennacido();
            armado.lane(TIENDA, VENTAS, VENDEDORES);
            armado.temporizador(VENTAS, "Never", TipoEvento.TEMPORIZADOR_INICIO, TipoTemporizador.CICLO, "nunca",
                    null);
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());

            assertThat(temporizadores.armarInicio(version, grafo.iniciosTemporizados().getFirst())).isFalse();
        }
    }

    @Nested
    @DisplayName("La busqueda de vencidos")
    class Vencidos {

        @Test
        @DisplayName("Deja fuera los de un caso en error, aunque llenen el lote, y trae los de un caso abierto")
        void sinLosDeCasosEnError() {
            Caso enError = em.persistFlushFind(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version)
                    .referencia("ORD-ERR").estado(EstadoCaso.ERROR).modo(ModoCaso.PRODUCCION).variables("{}")
                    .build());
            Caso abierto = em.persistFlushFind(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version)
                    .referencia("ORD-OK").estado(EstadoCaso.ABIERTO).modo(ModoCaso.PRODUCCION).variables("{}")
                    .build());
            List<Long> deError = new ArrayList<>();
            for (int i = 0; i <= TemporizadorServiceImpl.LOTE; i++) {
                deError.add(vencido(enError, LocalDateTime.parse("2001-01-01T00:00:00").plusMinutes(i)).getId());
            }
            Long delAbierto = vencido(abierto, LocalDateTime.parse("2001-01-02T00:00:00")).getId();
            em.flush();

            assertThat(temporizadorRepository.vencidosEnProduccion(Temporizadores.utc(VIERNES),
                    PageRequest.of(0, TemporizadorServiceImpl.LOTE)))
                    .extracting(TemporizadorVencido::id)
                    .contains(delAbierto)
                    .doesNotContainAnyElementsOf(deError);
        }

        private Temporizador vencido(Caso caso, LocalDateTime venceEn) {
            ActividadCaso espera = actividadCasoRepository.save(ActividadCaso.builder().empresa(tienda).caso(caso)
                    .nodoId(1L).nodoNombre("Wait").tipoNodo(TipoNodoCaso.EVENTO).subtipo("TEMPORIZADOR_INTERMEDIO")
                    .estado(EstadoActividadCaso.EN_ESPERA).build());
            return em.persist(Temporizador.builder().empresa(tienda).procesoId(proceso.getId())
                    .versionProcesoId(version.getId()).casoId(caso.getId()).actividadCasoId(espera.getId())
                    .nodoId(1L).nodoNombre("Wait")
                    .clase(ClaseTemporizador.INTERMEDIO).modo(ModoCaso.PRODUCCION)
                    .tipoTemporizador(TipoTemporizador.DURACION).expresion("PT1H").venceEn(venceEn)
                    .estado(EstadoTemporizador.PROGRAMADO).creadoEn(venceEn.minusHours(1)).build());
        }
    }

    private Temporizador inicioDelProceso() {
        return temporizadorRepository.findAllByProcesoIdAndEmpresaIdAndClaseOrderByIdDesc(proceso.getId(),
                tienda.getId(), ClaseTemporizador.INICIO, PageRequest.of(0, 1)).getContent().getFirst();
    }

    private List<Caso> casosDelProceso() {
        return casoRepository.findAll().stream()
                .filter(caso -> caso.getProceso().getId().equals(proceso.getId())).toList();
    }

    private DiagramaArmado esperaDe(TipoTemporizador tipo, String expresion) {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, VENDEDORES);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.temporizador(VENTAS, "Wait", TipoEvento.TEMPORIZADOR_INTERMEDIO, tipo, expresion, null);
        armado.evento(VENTAS, "Done", TipoEvento.FIN);
        armado.arco("Start", "Wait");
        armado.arco("Wait", "Done");
        return armado;
    }

    private DiagramaArmado tareaConBorde() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, VENDEDORES);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Pick items", TipoActividad.USUARIO);
        armado.evento(VENTAS, "Picked", TipoEvento.FIN);
        armado.arco("Start", "Pick items");
        armado.arco("Pick items", "Picked");
        return armado;
    }

    private Usuario persona(String nombre) {
        return em.persistFlushFind(Usuario.builder().empresa(tienda).nombre(nombre)
                .email(nombre.toLowerCase() + "-reloj@demo.com").passwordHash("hash").rolAcceso(RolAcceso.EDITOR)
                .build());
    }

    private Caso arrancar(DiagramaArmado armado, ModoCaso modo) {
        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
        given(grafos.del(any(), any())).willReturn(grafo);
        Caso caso = em.persistFlushFind(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version)
                .referencia("ORD-1").estado(EstadoCaso.ABIERTO).modo(modo).variables("{}").tickInicio(TICK).build());
        motor.arrancar(caso, grafo, grafo.inicioAMano().orElseThrow(), Momento.en(TICK));
        em.flush();
        return caso;
    }

    /** Como la completa el service: marcada, y el motor sigue por sus salidas. */
    private void completarTarea(Caso caso, GrafoDeVersion grafo, String nombre) {
        ActividadCaso tarea = pasoPor(caso, nombre);
        tarea.setEstado(EstadoActividadCaso.COMPLETADA);
        actividadCasoRepository.save(tarea);
        motor.seguirDesde(caso, grafo, tarea.getNodoId(), Momento.en(TICK));
        em.flush();
    }

    private List<Temporizador> temporizadoresDe(Caso caso) {
        return temporizadorRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId());
    }

    private Temporizador unico(Caso caso) {
        List<Temporizador> armados = temporizadoresDe(caso);
        assertThat(armados).hasSize(1);
        return armados.getFirst();
    }

    private ActividadCaso pasoPor(Caso caso, String nombre) {
        return actividadCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId()).stream()
                .filter(paso -> paso.getNodoNombre().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El caso no paso por " + nombre));
    }

    private List<TipoEventoCaso> tiposDeLaBitacora(Caso caso) {
        return eventoCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId()).stream()
                .map(EventoCaso::getTipo).toList();
    }

    private List<String> detalles(Caso caso) {
        return eventoCasoRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(), tienda.getId()).stream()
                .map(EventoCaso::getDetalle).toList();
    }
}
