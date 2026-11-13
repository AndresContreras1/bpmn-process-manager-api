package com.facimus.procesos.ejecucion.service.impl;

import static com.facimus.procesos.modelado.service.DiagramaArmado.TIENDA;
import static com.facimus.procesos.modelado.service.DiagramaArmado.VENTAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.metricas.MetricasDeNegocio;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.config.AuditoriaConfig;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoDeCasosResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorActividadResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorPersonaResponse;
import com.facimus.procesos.ejecucion.dto.response.CumplimientoPorProcesoResponse;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoPlazo;
import com.facimus.procesos.ejecucion.model.EventoCaso;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.ejecucion.model.Temporizador;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.model.TipoNodoCaso;
import com.facimus.procesos.ejecucion.model.Vencimiento;
import com.facimus.procesos.ejecucion.puerto.ParametrosDeSimulacion;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.CasoRepository;
import com.facimus.procesos.ejecucion.repository.DelegacionRepository;
import com.facimus.procesos.ejecucion.repository.EventoCasoRepository;
import com.facimus.procesos.ejecucion.repository.MensajeSalienteRepository;
import com.facimus.procesos.ejecucion.repository.TemporizadorRepository;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.Pais;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.TipoVersion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.VersionProceso;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.gestion.service.CalendarioService;
import com.facimus.procesos.gestion.service.MembresiaRolService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.EscalamientoResponse;
import com.facimus.procesos.modelado.dto.response.PlazoResponse;
import com.facimus.procesos.modelado.model.AccionDeEscalamiento;
import com.facimus.procesos.modelado.model.TipoActividad;
import com.facimus.procesos.modelado.model.TipoEvento;
import com.facimus.procesos.modelado.model.TipoTemporizador;
import com.facimus.procesos.modelado.model.UnidadDePlazo;
import com.facimus.procesos.modelado.service.DiagramaArmado;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 71: los plazos en el motor, sobre la base de verdad y con un reloj que la prueba mueve a mano: la tarea y el caso
 * arman su plazo, el repaso los mueve con las consultas y la escritura condicionada de verdad y avisa una sola vez, el
 * borde que no interrumpe escala, y las metricas de cumplimiento se cuentan a mano.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuditoriaConfig.class)
class MotorConPlazosTest {

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
    private final GrafosDeVersion grafos = mock(GrafosDeVersion.class);
    private final MembresiaRolService membresiaRolService = mock(MembresiaRolService.class);
    private final PersonasDeLaTarea personas = mock(PersonasDeLaTarea.class);
    private final Auditoria auditoria = mock(Auditoria.class);
    private final List<NotificacionPedida> avisados = new ArrayList<>();

    private MotorDeProcesos motor;
    private Plazos plazos;
    private DisparoDeTemporizadores disparo;
    private PlazoServiceImpl repaso;
    private Empresa tienda;
    private Proceso proceso;
    private VersionProceso version;
    private Usuario ana;
    private Usuario luis;
    private Usuario dueno;

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
        CalendarioService calendarioService = mock(CalendarioService.class);
        given(calendarioService.laboral(any())).willReturn(COLOMBIA);
        RelojDeLaTienda relojDeLaTienda = mock(RelojDeLaTienda.class);
        given(relojDeLaTienda.ahora(any())).willReturn(TICK);
        ApplicationEventPublisher eventos = evento -> avisados.add((NotificacionPedida) evento);
        AvisosDePlazo avisos = new AvisosDePlazo(eventos, membresiaRolService);
        Tareas tareas = new Tareas(actividadCasoRepository, casoRepository, delegacionRepository);
        Temporizadores temporizadores = new Temporizadores(temporizadorRepository, actividadCasoRepository,
                calendarioService, bitacora, reloj);
        plazos = new Plazos(calendarioService, actividadCasoRepository, bitacora, reloj);
        motor = new MotorDeProcesos(actividadCasoRepository, new BandejaDeSalida(mensajeSalienteRepository,
                new SociosSimulados(List.of()), parametros, bitacora, json), bitacora, json,
                TestObservationRegistry.create(), temporizadores, new AsignacionAlCrear(personas), tareas, plazos,
                new Escalamientos(actividadCasoRepository, personas, tareas, bitacora, auditoria, avisos));
        disparo = new DisparoDeTemporizadores(temporizadorRepository, casoRepository, actividadCasoRepository,
                mock(VersionService.class), grafos, motor, temporizadores, relojDeLaTienda, reloj);
        repaso = new PlazoServiceImpl(actividadCasoRepository, casoRepository, new RevisionDePlazos(
                actividadCasoRepository, casoRepository, bitacora, avisos, relojDeLaTienda, reloj), reloj);
        tienda = em.persistFlushFind(Empresa.builder().nombre("Tienda de los plazos").nit("900765433-1")
                .correoContacto("plazos@demo.com").fechaRegistro(LocalDate.now()).build());
        ana = persona("Ana");
        luis = persona("Luis");
        dueno = persona("Dueno");
        proceso = em.persistFlushFind(Proceso.builder().empresa(tienda).nombre("Refunds")
                .descripcion("Refund to customer").categoria("Support").estado(EstadoProceso.PUBLICADO).activo(true)
                .duenoId(dueno.getId()).build());
        version = em.persistFlushFind(VersionProceso.builder().empresa(tienda).proceso(proceso).numero(1)
                .estado(EstadoVersion.VIGENTE).fechaPublicacion(LocalDateTime.now())
                .huella("1f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0124").definicion("{}")
                .modeloBpmn("<bpmn:definitions/>").build());
        given(membresiaRolService.idsDeLosMiembrosDe(tienda.getId(), VENDEDORES))
                .willReturn(List.of(ana.getId(), luis.getId()));
        given(personas.siPuedeTenerla(eq(tienda.getId()), anyLong()))
                .willAnswer(llamada -> Optional.of(llamada.getArgument(1)));
    }

    @Nested
    @DisplayName("El plazo de una tarea")
    class DeUnaTarea {

        @Test
        @DisplayName("Dos dias habiles desde el viernes: por vencer el miercoles a las diez y media, vencida a las tres, "
                + "con un aviso cada vez y una sola vez")
        void produccion_porVencerYVencida() {
            Caso caso = arrancar(conPlazo(2, UnidadDePlazo.DIAS_HABILES), ModoCaso.PRODUCCION);
            ActividadCaso tarea = pasoPor(caso, "Approve");

            assertThat(tarea.getVencimiento().getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-14T20:00:00"));
            assertThat(tarea.getVencimiento().getAvisoEn()).isEqualTo(LocalDateTime.parse("2026-01-14T15:30:00"));
            assertThat(tarea.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
            assertThat(detalles(caso)).contains("\"Approve\" vence el 2026-01-14 15:00 (America/Bogota).");

            // El martes a ultima hora sigue a tiempo: el lunes festivo no conto
            reloj.irA(Instant.parse("2026-01-13T22:00:00Z"));
            assertThat(repaso.revisarVencidos()).isZero();
            reloj.irA(Instant.parse("2026-01-14T15:30:00Z"));
            assertThat(repaso.revisarVencidos()).isOne();
            assertThat(repaso.revisarVencidos()).isZero();
            assertThat(estadoDe(tarea)).isEqualTo(EstadoPlazo.POR_VENCER);
            reloj.irA(Instant.parse("2026-01-14T19:59:59Z"));
            assertThat(repaso.revisarVencidos()).isZero();
            reloj.irA(Instant.parse("2026-01-14T20:00:00Z"));
            assertThat(repaso.revisarVencidos()).isOne();
            assertThat(repaso.revisarVencidos()).isZero();

            assertThat(estadoDe(tarea)).isEqualTo(EstadoPlazo.VENCIDA);
            assertThat(tiposDeLaBitacora(caso)).containsSubsequence(TipoEventoCaso.PLAZO_POR_VENCER,
                    TipoEventoCaso.PLAZO_VENCIDO);
            // Nadie la tiene: a quienes pertenecen a su rol y al dueno, una vez por cambio
            assertThat(avisados).extracting(NotificacionPedida::tipo).containsExactly(
                    TipoDeNotificacion.PLAZO_POR_VENCER, TipoDeNotificacion.PLAZO_VENCIDO);
            assertThat(avisados).allSatisfy(aviso -> {
                assertThat(aviso.destinatarios()).containsExactly(ana.getId(), luis.getId(), dueno.getId());
                assertThat(aviso.referenciaId()).isEqualTo(caso.getId());
                assertThat(aviso.procesoId()).isEqualTo(proceso.getId());
                assertThat(aviso.autorId()).isNull();
            });
        }

        @Test
        @DisplayName("Con la API caida una semana salta de a tiempo a vencida y solo avisa que vencio")
        void produccion_apiCaida() {
            Caso caso = arrancar(conPlazo(8, UnidadDePlazo.HORAS), ModoCaso.PRODUCCION);
            ActividadCaso tarea = pasoPor(caso, "Approve");
            tarea.setAsignadoA(ana.getId());
            actividadCasoRepository.saveAndFlush(tarea);

            reloj.avanzar(Duration.ofDays(7));
            assertThat(repaso.revisarVencidos()).isOne();
            assertThat(repaso.revisarVencidos()).isZero();

            assertThat(estadoDe(tarea)).isEqualTo(EstadoPlazo.VENCIDA);
            assertThat(avisados).singleElement().satisfies(aviso -> {
                assertThat(aviso.tipo()).isEqualTo(TipoDeNotificacion.PLAZO_VENCIDO);
                assertThat(aviso.destinatarios()).containsExactly(ana.getId(), dueno.getId());
            });
        }

        @Test
        @DisplayName("Una tarea que se completo no se mueve ni avisa; una sin plazo no se arma")
        void completada_oSinPlazo() {
            Caso caso = arrancar(conPlazo(8, UnidadDePlazo.HORAS), ModoCaso.PRODUCCION);
            ActividadCaso tarea = pasoPor(caso, "Approve");
            tarea.setEstado(EstadoActividadCaso.COMPLETADA);
            actividadCasoRepository.saveAndFlush(tarea);
            reloj.avanzar(Duration.ofDays(1));

            assertThat(repaso.revisarVencidos()).isZero();
            assertThat(avisados).isEmpty();

            Caso otro = arrancar(tareaConBorde(), ModoCaso.PRODUCCION);
            assertThat(pasoPor(otro, "Approve").getVencimiento()).isNull();
        }

        @Test
        @DisplayName("En prueba se mueve con el tick de la tienda: ocho horas son ocho ticks, el aviso en el sexto")
        void prueba_conTicks() {
            Caso caso = arrancar(conPlazo(8, UnidadDePlazo.HORAS), ModoCaso.PRUEBA);
            ActividadCaso tarea = pasoPor(caso, "Approve");

            assertThat(tarea.getVencimiento().getVenceTick()).isEqualTo(TICK + 8);
            assertThat(tarea.getVencimiento().getAvisoTick()).isEqualTo(TICK + 6);
            assertThat(tarea.getVencimiento().getVenceEn()).isNull();
            reloj.avanzar(Duration.ofDays(30));
            assertThat(repaso.revisarVencidos()).isZero();
            assertThat(repaso.revisarEnTick(tienda.getId(), TICK + 5)).isZero();
            assertThat(repaso.revisarEnTick(tienda.getId(), TICK + 6)).isOne();
            assertThat(repaso.revisarEnTick(tienda.getId(), TICK + 7)).isZero();
            assertThat(repaso.revisarEnTick(tienda.getId(), TICK + 8)).isOne();
            assertThat(repaso.revisarEnTick(tienda.getId(), TICK + 40)).isZero();
            assertThat(estadoDe(tarea)).isEqualTo(EstadoPlazo.VENCIDA);
            assertThat(avisados).hasSize(2);
            // Otra tienda no mueve lo de esta
            assertThat(repaso.revisarEnTick(tienda.getId() + 1000, TICK + 40)).isZero();
        }

        @Test
        @DisplayName("La bandeja va de la que vence antes a la que vence despues, y las sin plazo al final")
        void bandejaPorVencimiento() {
            Caso tarde = arrancar(conPlazo(3, UnidadDePlazo.DIAS_HABILES), ModoCaso.PRODUCCION);
            Caso sinPlazo = arrancar(tareaConBorde(), ModoCaso.PRODUCCION);
            Caso pronto = arrancar(conPlazo(2, UnidadDePlazo.HORAS), ModoCaso.PRODUCCION);
            Caso enTicks = arrancar(conPlazo(2, UnidadDePlazo.HORAS), ModoCaso.PRUEBA);
            em.clear();

            List<Long> bandeja = actividadCasoRepository.bandejaPorRol(tienda.getId(), null, null,
                    EstadoActividadCaso.EN_ESPERA, null, false, PageRequest.of(0, 10)).map(paso -> paso.getCaso()
                            .getId()).getContent();

            assertThat(bandeja).containsExactly(pronto.getId(), tarde.getId(), enTicks.getId(), sinPlazo.getId());
            assertThat(actividadCasoRepository.bandejaDeMisRoles(tienda.getId(), List.of(VENDEDORES), null,
                    EstadoActividadCaso.EN_ESPERA, PageRequest.of(0, 10)).map(paso -> paso.getCaso().getId())
                    .getContent()).containsExactly(pronto.getId(), tarde.getId(), enTicks.getId(), sinPlazo.getId());
        }

        @Test
        @DisplayName("Quien guarda la tarea que leyo antes de que el repaso la moviera no le pisa el estado: no se "
                + "avisa otra vez")
        void guardarDespuesNoPisa() {
            Caso caso = arrancar(conPlazo(8, UnidadDePlazo.HORAS), ModoCaso.PRODUCCION);
            ActividadCaso leida = pasoPor(caso, "Approve");

            // El repaso, en otra transaccion, la mueve mientras quien la leyo la reasigna
            em.getEntityManager().createNativeQuery("update actividades_caso set estado_plazo = 'POR_VENCER' "
                    + "where id = ?").setParameter(1, leida.getId()).executeUpdate();
            leida.setAsignadoA(luis.getId());
            actividadCasoRepository.saveAndFlush(leida);
            em.clear();

            ActividadCaso guardada = actividadCasoRepository.findById(leida.getId()).orElseThrow();
            assertThat(guardada.getAsignadoA()).isEqualTo(luis.getId());
            assertThat(guardada.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.POR_VENCER);
            reloj.avanzar(Duration.ofHours(7));
            assertThat(repaso.revisarVencidos()).isZero();
            assertThat(avisados).isEmpty();
        }
    }

    @Nested
    @DisplayName("El plazo del caso")
    class DelCaso {

        @Test
        @DisplayName("Un dia desde que se abre: por vencer y vencido, avisando a quien lo abrio y al dueno")
        void porVencerYVencido() {
            DiagramaArmado armado = tareaConBorde();
            armado.plazo("Start", new PlazoResponse(24, UnidadDePlazo.HORAS));
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION, ana.getId());

            assertThat(caso.getVencimiento().getVenceEn()).isEqualTo(LocalDateTime.parse("2026-01-10T20:00:00"));
            reloj.avanzar(Duration.ofHours(18));
            assertThat(repaso.revisarVencidos()).isOne();
            reloj.avanzar(Duration.ofHours(6));
            assertThat(repaso.revisarVencidos()).isOne();
            assertThat(repaso.revisarVencidos()).isZero();

            assertThat(casoRepository.findById(caso.getId()).orElseThrow().getVencimiento().getEstado())
                    .isEqualTo(EstadoPlazo.VENCIDA);
            assertThat(detalles(caso)).contains("El caso vence el 2026-01-10 15:00 (America/Bogota).",
                    "El caso esta por vencer: entro en el ultimo cuarto de su plazo.", "El caso paso su plazo.");
            assertThat(avisados).extracting(NotificacionPedida::destinatarios).containsOnly(
                    List.of(ana.getId(), dueno.getId()));
        }

        @Test
        @DisplayName("Un caso que termina antes de vencer queda a tiempo; despues, vencido")
        void alTerminar() {
            DiagramaArmado armado = tareaConBorde();
            armado.plazo("Start", new PlazoResponse(24, UnidadDePlazo.HORAS));
            GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
            Caso aTiempo = arrancar(armado, ModoCaso.PRODUCCION);
            Caso tarde = arrancar(armado, ModoCaso.PRODUCCION);

            completarTarea(aTiempo, grafo, "Approve");
            reloj.avanzar(Duration.ofDays(2));
            completarTarea(tarde, grafo, "Approve");

            assertThat(aTiempo.getEstado()).isEqualTo(EstadoCaso.TERMINADO);
            assertThat(aTiempo.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.A_TIEMPO);
            assertThat(tarde.getVencimiento().getEstado()).isEqualTo(EstadoPlazo.VENCIDA);
            em.flush();
            assertThat(repaso.revisarVencidos()).isZero();
        }
    }

    @Nested
    @DisplayName("El escalamiento")
    class Escalamiento {

        @Test
        @DisplayName("Un borde que no interrumpe y REASIGNAR le pasa la tarea al supervisor, y la tarea sigue esperando")
        void reasignar() {
            DiagramaArmado armado = tareaConBorde();
            armado.bordeQueNoInterrumpe(VENTAS, "Six hours", TipoTemporizador.DURACION, "PT6H", "Approve");
            armado.evento(VENTAS, "Escalated", TipoEvento.FIN);
            armado.arco("Six hours", "Escalated");
            armado.escalamiento("Six hours", new EscalamientoResponse(AccionDeEscalamiento.REASIGNAR, luis.getId()));
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);
            ActividadCaso tarea = pasoPor(caso, "Approve");
            tarea.setAsignadoA(ana.getId());
            actividadCasoRepository.saveAndFlush(tarea);

            reloj.avanzar(Duration.ofHours(6));
            assertThat(disparo.enProduccion(tienda.getId(), unico(caso).getId())).isTrue();
            em.flush();

            ActividadCaso escalada = pasoPor(caso, "Approve");
            assertThat(escalada.getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            assertThat(escalada.getAsignadoA()).isEqualTo(luis.getId());
            assertThat(pasoPor(caso, "Escalated").getEstado()).isEqualTo(EstadoActividadCaso.COMPLETADA);
            assertThat(caso.getEstado()).isEqualTo(EstadoCaso.ABIERTO);
            assertThat(detalles(caso)).contains("\"Six hours\" escala \"Approve\": pasa del usuario " + ana.getId()
                    + " al usuario " + luis.getId() + ", que la supervisa.");
            verify(auditoria).registrar(eq(tienda.getId()), eq(null), eq(Escalamientos.ESCALADA), eq("TAREA"),
                    eq(escalada.getId()), any());
            assertThat(avisados).singleElement().satisfies(aviso -> {
                assertThat(aviso.tipo()).isEqualTo(TipoDeNotificacion.TAREA_ESCALADA);
                assertThat(aviso.destinatarios()).containsExactly(ana.getId(), luis.getId());
            });
        }

        @Test
        @DisplayName("AVISAR, sin supervisor dicho, le avisa al dueno del proceso y deja la tarea donde estaba")
        void avisar() {
            DiagramaArmado armado = tareaConBorde();
            armado.bordeQueNoInterrumpe(VENTAS, "Six hours", TipoTemporizador.DURACION, "PT6H", "Approve");
            armado.escalamiento("Six hours", new EscalamientoResponse(AccionDeEscalamiento.AVISAR, null));
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);

            reloj.avanzar(Duration.ofHours(6));
            assertThat(disparo.enProduccion(tienda.getId(), unico(caso).getId())).isTrue();

            assertThat(pasoPor(caso, "Approve").getAsignadoA()).isNull();
            assertThat(avisados).singleElement().satisfies(aviso -> assertThat(aviso.destinatarios())
                    .containsExactly(dueno.getId()));
            assertThat(detalles(caso)).contains("\"Six hours\" escala \"Approve\": se le avisa al usuario "
                    + dueno.getId() + ", que la supervisa.");
        }

        @Test
        @DisplayName("Uno que interrumpe toma la otra salida: la tarea sale de la bandeja y no se escala")
        void interrumpe_tomaLaOtraSalida() {
            DiagramaArmado armado = tareaConBorde();
            armado.temporizador(VENTAS, "Too late", TipoEvento.TEMPORIZADOR_BORDE, TipoTemporizador.DURACION, "PT6H",
                    "Approve");
            armado.actividad(VENTAS, "Refund anyway", TipoActividad.USUARIO);
            armado.arco("Too late", "Refund anyway");
            armado.arco("Refund anyway", "Done");
            Caso caso = arrancar(armado, ModoCaso.PRODUCCION);

            reloj.avanzar(Duration.ofHours(6));
            assertThat(disparo.enProduccion(tienda.getId(), unico(caso).getId())).isTrue();

            assertThat(pasoPor(caso, "Approve").getEstado()).isEqualTo(EstadoActividadCaso.OMITIDA);
            assertThat(pasoPor(caso, "Refund anyway").getEstado()).isEqualTo(EstadoActividadCaso.EN_ESPERA);
            assertThat(tiposDeLaBitacora(caso)).doesNotContain(TipoEventoCaso.TAREA_ESCALADA);
            assertThat(avisados).isEmpty();
        }
    }

    @Nested
    @DisplayName("Las metricas de cumplimiento, contadas a mano")
    class Cumplimiento {

        @Test
        @DisplayName("A tiempo lo completado antes de vencer; tarde lo completado despues o lo que espera vencido")
        void porActividadProcesoYPersona() {
            // Approve: Ana completa una a tiempo y una tarde; Luis completa una a tiempo; una espera vencida con Luis
            tarea("Approve", EstadoActividadCaso.COMPLETADA, EstadoPlazo.A_TIEMPO, ana);
            tarea("Approve", EstadoActividadCaso.COMPLETADA, EstadoPlazo.POR_VENCER, luis);
            tarea("Approve", EstadoActividadCaso.COMPLETADA, EstadoPlazo.VENCIDA, ana);
            tarea("Approve", EstadoActividadCaso.EN_ESPERA, EstadoPlazo.VENCIDA, luis);
            // Pay: una vencida que espera sin nadie, y una completada tarde por Ana
            tarea("Pay", EstadoActividadCaso.EN_ESPERA, EstadoPlazo.VENCIDA, null);
            tarea("Pay", EstadoActividadCaso.COMPLETADA, EstadoPlazo.VENCIDA, ana);
            // No cuentan: lo que espera sin vencer, lo omitido, lo que no tiene plazo y las pruebas de un borrador
            tarea("Pay", EstadoActividadCaso.EN_ESPERA, EstadoPlazo.POR_VENCER, ana);
            tarea("Pay", EstadoActividadCaso.OMITIDA, EstadoPlazo.VENCIDA, ana);
            tarea("Pay", EstadoActividadCaso.COMPLETADA, null, ana);
            ActividadCaso deUnaPrueba = tarea("Pay", EstadoActividadCaso.COMPLETADA, EstadoPlazo.VENCIDA, ana);
            VersionProceso prueba = em.persistFlushFind(VersionProceso.builder().empresa(tienda).proceso(proceso)
                    .numero(0).tipo(TipoVersion.PRUEBA).estado(EstadoVersion.RETIRADA).fechaPublicacion(LocalDateTime.now())
                    .huella("1f2c4a6b8d0e1f23456789abcdef0123456789abcdef0123456789abcdef0125").definicion("{}")
                    .modeloBpmn("<bpmn:definitions/>").build());
            em.getEntityManager().createNativeQuery("update casos set version_proceso_id = ? where id = ?")
                    .setParameter(1, prueba.getId()).setParameter(2, deUnaPrueba.getCaso().getId()).executeUpdate();
            em.clear();

            Long tiendaId = tienda.getId();
            assertThat(actividadCasoRepository.cumplimientoPorActividad(tiendaId, null)).containsExactly(
                    new CumplimientoPorActividadResponse(proceso.getId(), "Refunds", 12L, "Approve", 2, 2),
                    new CumplimientoPorActividadResponse(proceso.getId(), "Refunds", 13L, "Pay", 0, 2));
            assertThat(actividadCasoRepository.cumplimientoPorProceso(tiendaId, null)).containsExactly(
                    new CumplimientoPorProcesoResponse(proceso.getId(), "Refunds", 2, 4));
            assertThat(actividadCasoRepository.cumplimientoPorPersona(tiendaId, null)).containsExactly(
                    new CumplimientoPorPersonaResponse(ana.getId(), "Ana", 1, 2),
                    new CumplimientoPorPersonaResponse(luis.getId(), "Luis", 1, 1));
            assertThat(actividadCasoRepository.cumplimientoPorProceso(tiendaId, proceso.getId() + 1000)).isEmpty();
            assertThat(actividadCasoRepository.cumplimientoPorProceso(tiendaId + 1000, null)).isEmpty();
        }

        @Test
        @DisplayName("Los casos: a tiempo los terminados antes de vencer; tarde los terminados despues o abiertos "
                + "vencidos")
        void casos() {
            caso(EstadoCaso.TERMINADO, EstadoPlazo.A_TIEMPO);
            caso(EstadoCaso.TERMINADO, EstadoPlazo.POR_VENCER);
            caso(EstadoCaso.TERMINADO, EstadoPlazo.VENCIDA);
            caso(EstadoCaso.ABIERTO, EstadoPlazo.VENCIDA);
            caso(EstadoCaso.ERROR, EstadoPlazo.VENCIDA);
            // No cuentan: abierto sin vencer, cancelado, sin plazo
            caso(EstadoCaso.ABIERTO, EstadoPlazo.POR_VENCER);
            caso(EstadoCaso.CANCELADO, EstadoPlazo.VENCIDA);
            caso(EstadoCaso.TERMINADO, null);

            assertThat(casoRepository.cumplimientoDeCasos(tienda.getId(), null))
                    .isEqualTo(new CumplimientoDeCasosResponse(2, 3));
            assertThat(casoRepository.cumplimientoDeCasos(tienda.getId(), proceso.getId()))
                    .isEqualTo(new CumplimientoDeCasosResponse(2, 3));
            assertThat(casoRepository.cumplimientoDeCasos(tienda.getId() + 1000, null))
                    .isEqualTo(new CumplimientoDeCasosResponse(0, 0));
        }

        private ActividadCaso tarea(String nombre, EstadoActividadCaso estado, EstadoPlazo plazo, Usuario quien) {
            Caso caso = em.persistFlushFind(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version)
                    .estado(EstadoCaso.ABIERTO).modo(ModoCaso.PRODUCCION).variables("{}").tickInicio(TICK).build());
            Vencimiento vencimiento = null;
            if (plazo != null) {
                vencimiento = Vencimiento.enProduccion(LocalDateTime.parse("2026-01-10T10:00:00"),
                        LocalDateTime.parse("2026-01-10T12:00:00"));
                vencimiento.setEstado(plazo);
            }
            return em.persistFlushFind(ActividadCaso.builder().empresa(tienda).caso(caso)
                    .nodoId("Approve".equals(nombre) ? 12L : 13L).nodoNombre(nombre).tipoNodo(TipoNodoCaso.ACTIVIDAD)
                    .subtipo("USUARIO").rolProcesoId(VENDEDORES).estado(estado).llegadas(1).tickInicio(TICK)
                    .asignadoA(quien == null ? null : quien.getId()).vencimiento(vencimiento).build());
        }

        private void caso(EstadoCaso estado, EstadoPlazo plazo) {
            Vencimiento vencimiento = null;
            if (plazo != null) {
                vencimiento = Vencimiento.enProduccion(LocalDateTime.parse("2026-01-10T10:00:00"),
                        LocalDateTime.parse("2026-01-10T12:00:00"));
                vencimiento.setEstado(plazo);
            }
            em.persistAndFlush(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version).estado(estado)
                    .modo(ModoCaso.PRODUCCION).variables("{}").tickInicio(TICK).vencimiento(vencimiento).build());
        }
    }

    // ---------- Lo que se arma para cada prueba ----------

    /** Inicio, una tarea de usuario y un fin. */
    private DiagramaArmado tareaConBorde() {
        DiagramaArmado armado = DiagramaArmado.reciennacido();
        armado.lane(TIENDA, VENTAS, VENDEDORES);
        armado.evento(VENTAS, "Start", TipoEvento.INICIO);
        armado.actividad(VENTAS, "Approve", TipoActividad.USUARIO);
        armado.evento(VENTAS, "Done", TipoEvento.FIN);
        armado.arco("Start", "Approve");
        armado.arco("Approve", "Done");
        return armado;
    }

    /** Lo mismo, con un plazo en la tarea. */
    private DiagramaArmado conPlazo(int cantidad, UnidadDePlazo unidad) {
        DiagramaArmado armado = tareaConBorde();
        armado.plazo("Approve", new PlazoResponse(cantidad, unidad));
        return armado;
    }

    private Usuario persona(String nombre) {
        return em.persistFlushFind(Usuario.builder().empresa(tienda).nombre(nombre)
                .email(nombre.toLowerCase() + "-plazos@demo.com").passwordHash("hash").rolAcceso(RolAcceso.EDITOR)
                .build());
    }

    private Caso arrancar(DiagramaArmado armado, ModoCaso modo) {
        return arrancar(armado, modo, null);
    }

    private Caso arrancar(DiagramaArmado armado, ModoCaso modo, Long iniciadoPor) {
        GrafoDeVersion grafo = GrafoDeVersion.de(armado.diagrama());
        given(grafos.del(any(), any())).willReturn(grafo);
        Caso caso = em.persistFlushFind(Caso.builder().empresa(tienda).proceso(proceso).versionProceso(version)
                .referencia("ORD-1").estado(EstadoCaso.ABIERTO).modo(modo).variables("{}").tickInicio(TICK)
                .iniciadoPor(iniciadoPor).build());
        motor.arrancar(caso, grafo, grafo.inicioAMano().orElseThrow(), Momento.en(TICK));
        em.flush();
        return caso;
    }

    /** Como la completa el service: cerrado su plazo, marcada, y el motor sigue por sus salidas. */
    private void completarTarea(Caso caso, GrafoDeVersion grafo, String nombre) {
        ActividadCaso tarea = pasoPor(caso, nombre);
        tarea.setEstado(EstadoActividadCaso.COMPLETADA);
        plazos.alCompletar(tarea, Momento.en(TICK));
        actividadCasoRepository.save(tarea);
        motor.seguirDesde(caso, grafo, tarea.getNodoId(), Momento.en(TICK));
        em.flush();
    }

    private EstadoPlazo estadoDe(ActividadCaso tarea) {
        return actividadCasoRepository.findById(tarea.getId()).orElseThrow().getVencimiento().getEstado();
    }

    private Temporizador unico(Caso caso) {
        List<Temporizador> armados = temporizadorRepository.findAllByCasoIdAndEmpresaIdOrderByIdAsc(caso.getId(),
                tienda.getId());
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
