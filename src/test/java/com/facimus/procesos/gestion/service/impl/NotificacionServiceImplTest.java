package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.NotificacionPedida;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.ConteoDeNotificacionesResponse;
import com.facimus.procesos.gestion.dto.response.NotificacionResponse;
import com.facimus.procesos.gestion.dto.response.PreferenciaDeNotificacionResponse;
import com.facimus.procesos.gestion.model.Notificacion;
import com.facimus.procesos.gestion.model.PreferenciaNotificacion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.NotificacionRepository;
import com.facimus.procesos.gestion.repository.PreferenciaNotificacionRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import tools.jackson.databind.json.JsonMapper;

/** PR 63: el centro de notificaciones, con la base y el canal en vivo de mentira. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacionServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 3L;
    private static final Long ANA = 2L;
    private static final Long BETO = 5L;
    private static final Long CARLA = 6L;
    private static final Long ELIO = 8L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private PreferenciaNotificacionRepository preferenciaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ColaDeTrabajos cola;

    @Mock
    private JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private CanalDeNotificaciones canal;
    private NotificacionServiceImpl servicio;
    private Empresa empresa;
    private long siguienteId = 40;

    @BeforeEach
    void preparar() {
        canal = spy(new CanalDeNotificaciones(jdbc, json));
        Clock reloj = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        servicio = new NotificacionServiceImpl(notificacionRepository, preferenciaRepository, usuarioRepository,
                procesoRepository, canal, cola, json, reloj, Duration.ofDays(90), 2);
        empresa = Empresa.builder().id(EMPRESA).build();
        List<Usuario> personas = List.of(persona(ANA, "Ana", true), persona(BETO, "Beto", true),
                persona(CARLA, "Carla", true), persona(ELIO, "Elio", false));
        when(usuarioRepository.findAllByEmpresaIdAndIdIn(eq(EMPRESA), anyCollection())).thenAnswer(llamada -> {
            Collection<Long> ids = llamada.getArgument(1);
            // Al reves, para ver que el orden lo pone el servicio
            return personas.reversed().stream().filter(una -> ids.contains(una.getId())).toList();
        });
        personas.forEach(una -> when(usuarioRepository.findByIdAndEmpresaId(una.getId(), EMPRESA))
                .thenReturn(Optional.of(una)));
        when(procesoRepository.findByIdAndEmpresaId(PROCESO, EMPRESA))
                .thenReturn(Optional.of(Proceso.builder().id(PROCESO).nombre("Despacho").empresa(empresa).build()));
        when(procesoRepository.findAllByIdInAndEmpresaId(anyCollection(), eq(EMPRESA)))
                .thenReturn(List.of(Proceso.builder().id(PROCESO).nombre("Despacho").empresa(empresa).build()));
        when(notificacionRepository.save(any(Notificacion.class))).thenAnswer(llamada -> {
            Notificacion notificacion = llamada.getArgument(0);
            if (notificacion.getId() == null) {
                notificacion.setId(siguienteId++);
            }
            return notificacion;
        });
    }

    private Usuario persona(Long id, String nombre, boolean activo) {
        return Usuario.builder().id(id).nombre(nombre).rolAcceso(RolAcceso.EDITOR).activo(activo).empresa(empresa)
                .build();
    }

    private Notificacion notificacion(Long id, Long usuario) {
        return Notificacion.builder().id(id).empresa(empresa).usuarioId(usuario).tipo(TipoDeNotificacion.MENCION)
                .autorId(ANA).procesoId(PROCESO).referenciaId(12L).envio(EnvioPorCorreo.INMEDIATO).creadaEn(AHORA)
                .build();
    }

    @Test
    @DisplayName("notificar crea una por persona activa que no sea quien lo hizo, con su preferencia o la del tipo")
    void notificar() {
        when(preferenciaRepository.findAllByEmpresaIdAndTipoAndUsuarioIdIn(eq(EMPRESA),
                eq(TipoDeNotificacion.RESPUESTA), anyCollection())).thenReturn(List.of(PreferenciaNotificacion
                        .builder().usuarioId(CARLA).tipo(TipoDeNotificacion.RESPUESTA)
                        .envio(EnvioPorCorreo.RESUMEN_DIARIO).build()));
        when(notificacionRepository.countByEmpresaIdAndUsuarioIdAndLeidaEnIsNull(EMPRESA, BETO)).thenReturn(1L);
        List<Alcance> alcances = new ArrayList<>();
        when(usuarioRepository.findByIdAndEmpresaId(ANA, EMPRESA)).thenAnswer(llamada -> {
            alcances.add(ContextoDeTienda.actual().orElse(null));
            return Optional.of(persona(ANA, "Ana", true));
        });

        servicio.notificar(new NotificacionPedida(EMPRESA, TipoDeNotificacion.RESPUESTA, ANA, PROCESO, 12L,
                List.of(CARLA, ANA, BETO, ELIO, BETO)));

        ArgumentCaptor<Notificacion> guardadas = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(Notificacion::getUsuarioId).containsExactly(BETO, CARLA);
        assertThat(guardadas.getAllValues()).extracting(Notificacion::getEnvio)
                .containsExactly(EnvioPorCorreo.INMEDIATO, EnvioPorCorreo.RESUMEN_DIARIO);
        Notificacion deBeto = guardadas.getAllValues().getFirst();
        assertThat(deBeto.getTipo()).isEqualTo(TipoDeNotificacion.RESPUESTA);
        assertThat(deBeto.getAutorId()).isEqualTo(ANA);
        assertThat(deBeto.getProcesoId()).isEqualTo(PROCESO);
        assertThat(deBeto.getReferenciaId()).isEqualTo(12L);
        assertThat(deBeto.getCreadaEn()).isEqualTo(AHORA);
        assertThat(deBeto.getEmpresa()).isSameAs(empresa);
        // Solo la inmediata va a la cola, con lo que necesita el correo y nada del comentario
        verify(cola).encolar(EMPRESA, "notificaciones.correo",
                "{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}");
        verify(cola, never()).encolar(eq(EMPRESA), anyString(), org.mockito.ArgumentMatchers.contains("41"));
        InOrder orden = inOrder(canal);
        orden.verify(canal).publicar(EMPRESA, BETO, "notificacion", new NotificacionResponse(40L,
                TipoDeNotificacion.RESPUESTA, PROCESO, "Despacho", 12L, ANA, "Ana", AHORA, null));
        orden.verify(canal).publicar(EMPRESA, BETO, "conteo", new ConteoDeNotificacionesResponse(1));
        orden.verify(canal).publicar(EMPRESA, CARLA, "notificacion", new NotificacionResponse(41L,
                TipoDeNotificacion.RESPUESTA, PROCESO, "Despacho", 12L, ANA, "Ana", AHORA, null));
        orden.verify(canal).publicar(EMPRESA, CARLA, "conteo", new ConteoDeNotificacionesResponse(0));
        // Cada aviso sale por pg_notify, en la transaccion de la notificacion
        verify(jdbc, times(4)).query(eq("select pg_notify(?, ?)"), any(ResultSetExtractor.class),
                eq("notificaciones"), anyString());
        // Escribe con el alcance de la tienda del pedido, y deja el de antes al terminar
        assertThat(alcances).containsExactly(Alcance.deLaTienda(EMPRESA));
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("un pedido solo para quien lo hizo no crea nada; sin proceso ni autor, la notificacion no los nombra")
    void notificarANadie() {
        servicio.notificar(new NotificacionPedida(EMPRESA, TipoDeNotificacion.MENCION, ANA, PROCESO, 12L,
                List.of(ANA)));

        verifyNoInteractions(notificacionRepository, preferenciaRepository, cola);
        verify(usuarioRepository, never()).findAllByEmpresaIdAndIdIn(anyLong(), anyCollection());

        servicio.notificar(new NotificacionPedida(EMPRESA, TipoDeNotificacion.HILO_RESUELTO, null, null, 12L,
                List.of(BETO)));

        verify(canal).publicar(EMPRESA, BETO, "notificacion", new NotificacionResponse(40L,
                TipoDeNotificacion.HILO_RESUELTO, null, null, 12L, null, null, AHORA, null));
        verify(procesoRepository, never()).findByIdAndEmpresaId(any(), any());
        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("listar trae la pagina de la persona con los nombres; solo las no leidas si se piden")
    void listar() {
        Notificacion una = notificacion(40L, BETO);
        Pageable pagina = PageRequest.of(0, 20);
        when(notificacionRepository.findAllByEmpresaIdAndUsuarioIdOrderByCreadaEnDescIdDesc(EMPRESA, BETO, pagina))
                .thenReturn(new PageImpl<>(List.of(una), pagina, 1));
        when(notificacionRepository.findAllByEmpresaIdAndUsuarioIdAndLeidaEnIsNullOrderByCreadaEnDescIdDesc(EMPRESA,
                BETO, pagina)).thenReturn(new PageImpl<>(List.of(), pagina, 0));

        assertThat(servicio.listar(EMPRESA, BETO, false, pagina).content()).containsExactly(new NotificacionResponse(
                40L, TipoDeNotificacion.MENCION, PROCESO, "Despacho", 12L, ANA, "Ana", AHORA, null));
        assertThat(servicio.listar(EMPRESA, BETO, true, pagina).content()).isEmpty();
        verify(procesoRepository, times(1)).findAllByIdInAndEmpresaId(anyCollection(),
                anyLong());
    }

    @Test
    @DisplayName("PR 71: un aviso del sistema, sin autor ni proceso conocido, se lista sin nombres en vez de fallar")
    void listar_sinAutor() {
        Notificacion delSistema = notificacion(41L, BETO);
        delSistema.setTipo(TipoDeNotificacion.PLAZO_VENCIDO);
        delSistema.setAutorId(null);
        delSistema.setProcesoId(null);
        Pageable pagina = PageRequest.of(0, 20);
        when(notificacionRepository.findAllByEmpresaIdAndUsuarioIdOrderByCreadaEnDescIdDesc(EMPRESA, BETO, pagina))
                .thenReturn(new PageImpl<>(List.of(delSistema), pagina, 1));

        assertThat(servicio.listar(EMPRESA, BETO, false, pagina).content()).containsExactly(new NotificacionResponse(
                41L, TipoDeNotificacion.PLAZO_VENCIDO, null, null, 12L, null, null, AHORA, null));
    }

    @Test
    @DisplayName("marcar una como leida es solo de su destinatario, y avisa del conteo una vez")
    void marcarLeida() {
        Notificacion una = notificacion(40L, BETO);
        when(notificacionRepository.findByIdAndEmpresaIdAndUsuarioId(40L, EMPRESA, BETO)).thenReturn(Optional.of(una));

        assertThatThrownBy(() -> servicio.marcarLeida(EMPRESA, CARLA, 40L))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Notificación no encontrada.");
        NotificacionResponse leida = servicio.marcarLeida(EMPRESA, BETO, 40L);
        servicio.marcarLeida(EMPRESA, BETO, 40L);

        assertThat(leida.leidaEn()).isEqualTo(AHORA);
        assertThat(leida.proceso()).isEqualTo("Despacho");
        assertThat(leida.autor()).isEqualTo("Ana");
        verify(notificacionRepository, times(1)).save(una);
        verify(canal, times(1)).publicar(anyLong(), anyLong(), anyString(), any());
        verify(canal).publicar(EMPRESA, BETO, "conteo", new ConteoDeNotificacionesResponse(0));
    }

    @Test
    @DisplayName("marcar todas avisa del conteo en cero solo si habia alguna sin leer")
    void marcarTodas() {
        when(notificacionRepository.marcarLeidas(EMPRESA, BETO, AHORA)).thenReturn(3, 0);

        assertThat(servicio.marcarTodasLeidas(EMPRESA, BETO)).isEqualTo(new ConteoDeNotificacionesResponse(0));
        verify(canal).publicar(EMPRESA, BETO, "conteo", new ConteoDeNotificacionesResponse(0));

        servicio.marcarTodasLeidas(EMPRESA, BETO);
        verify(canal, times(1)).publicar(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("un flujo empieza con el conteo, se suelta al cerrarse, y una persona no abre mas de su tope")
    void conectar() {
        when(notificacionRepository.countByEmpresaIdAndUsuarioIdAndLeidaEnIsNull(EMPRESA, BETO)).thenReturn(4L);
        Oyente uno = new Oyente();
        Oyente dos = new Oyente();

        servicio.conectar(EMPRESA, BETO, uno);
        servicio.conectar(EMPRESA, BETO, dos);

        assertThat(uno.recibido).singleElement().asString().contains("event:conteo", "noLeidas=4");
        assertThat(canal.abiertas(EMPRESA, BETO)).isEqualTo(2);
        Oyente tercero = new Oyente();
        assertThatThrownBy(() -> servicio.conectar(EMPRESA, BETO, tercero))
                .isInstanceOf(DemasiadosIntentosException.class);
        // Cada persona tiene su tope
        servicio.conectar(EMPRESA, CARLA, new Oyente());

        uno.cerrar();
        dos.vencer();
        assertThat(canal.abiertas(EMPRESA, BETO)).isZero();
        SseEmitter emisor = servicio.conectar(EMPRESA, BETO);
        assertThat(emisor.getTimeout()).isZero();
        assertThat(canal.abiertas(EMPRESA, BETO)).isEqualTo(1);
    }

    @Test
    @DisplayName("un flujo que falla con su primer evento no queda registrado, y uno que falla despues se suelta")
    void conectarCortado() {
        Oyente cortado = new Oyente();
        cortado.falla = new IOException("la pestana se cerro");

        servicio.conectar(EMPRESA, BETO, cortado);

        assertThat(canal.abiertas(EMPRESA, BETO)).isZero();
        assertThat(cortado.error).isInstanceOf(IOException.class);
        Oyente otro = new Oyente();
        servicio.conectar(EMPRESA, BETO, otro);
        otro.fallarAlCerrar();
        assertThat(canal.abiertas(EMPRESA, BETO)).isZero();
    }

    @Test
    @DisplayName("las preferencias dicen cada tipo, con lo elegido o lo de por defecto")
    void preferencias() {
        when(preferenciaRepository.findAllByEmpresaIdAndUsuarioId(EMPRESA, BETO)).thenReturn(List.of(
                PreferenciaNotificacion.builder().usuarioId(BETO).tipo(TipoDeNotificacion.MENCION)
                        .envio(EnvioPorCorreo.NUNCA).build()));

        assertThat(servicio.preferencias(EMPRESA, BETO)).containsExactly(
                new PreferenciaDeNotificacionResponse(TipoDeNotificacion.MENCION, EnvioPorCorreo.NUNCA, false),
                new PreferenciaDeNotificacionResponse(TipoDeNotificacion.RESPUESTA, EnvioPorCorreo.INMEDIATO, true),
                new PreferenciaDeNotificacionResponse(TipoDeNotificacion.HILO_RESUELTO,
                        EnvioPorCorreo.RESUMEN_DIARIO, true),
                new PreferenciaDeNotificacionResponse(TipoDeNotificacion.PLAZO_POR_VENCER, EnvioPorCorreo.INMEDIATO,
                        true),
                new PreferenciaDeNotificacionResponse(TipoDeNotificacion.PLAZO_VENCIDO, EnvioPorCorreo.INMEDIATO, true),
                new PreferenciaDeNotificacionResponse(TipoDeNotificacion.TAREA_ESCALADA, EnvioPorCorreo.INMEDIATO,
                        true));
    }

    @Test
    @DisplayName("guardar cambia las elegidas, crea las nuevas y deja las demas")
    void guardarPreferencias() {
        PreferenciaNotificacion mencion = PreferenciaNotificacion.builder().id(9L).usuarioId(BETO)
                .tipo(TipoDeNotificacion.MENCION).envio(EnvioPorCorreo.NUNCA).build();
        when(preferenciaRepository.findAllByEmpresaIdAndUsuarioId(EMPRESA, BETO)).thenReturn(List.of(mencion));
        Map<TipoDeNotificacion, EnvioPorCorreo> pedidas = new EnumMap<>(TipoDeNotificacion.class);
        pedidas.put(TipoDeNotificacion.MENCION, EnvioPorCorreo.RESUMEN_DIARIO);
        pedidas.put(TipoDeNotificacion.HILO_RESUELTO, EnvioPorCorreo.NUNCA);

        assertThat(servicio.guardarPreferencias(EMPRESA, BETO, pedidas)).hasSize(TipoDeNotificacion.values().length);

        ArgumentCaptor<PreferenciaNotificacion> guardadas = ArgumentCaptor.forClass(PreferenciaNotificacion.class);
        verify(preferenciaRepository, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues().getFirst()).isSameAs(mencion);
        assertThat(mencion.getEnvio()).isEqualTo(EnvioPorCorreo.RESUMEN_DIARIO);
        assertThat(mencion.getFechaModificacion()).isEqualTo(AHORA);
        PreferenciaNotificacion nueva = guardadas.getAllValues().get(1);
        assertThat(nueva.getTipo()).isEqualTo(TipoDeNotificacion.HILO_RESUELTO);
        assertThat(nueva.getEnvio()).isEqualTo(EnvioPorCorreo.NUNCA);
        assertThat(nueva.getUsuarioId()).isEqualTo(BETO);
        assertThat(nueva.getEmpresa()).isSameAs(empresa);
        assertThat(nueva.getFechaModificacion()).isEqualTo(AHORA);
        assertThatThrownBy(() -> servicio.guardarPreferencias(EMPRESA, 404L, pedidas))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("el resumen deja en la cola un correo por persona de cada tienda con lo que lo espera")
    void encolarResumenes() {
        when(notificacionRepository.pendientesDelResumen()).thenReturn(List.of(new Object[] {EMPRESA, BETO, 40L},
                new Object[] {EMPRESA, BETO, 41L}, new Object[] {EMPRESA, CARLA, 42L}, new Object[] {9L, BETO, 43L}));

        assertThat(servicio.encolarResumenes()).isEqualTo(3);

        verify(cola).encolar(EMPRESA, "notificaciones.correo",
                "{\"usuarioId\":5,\"notificaciones\":[40,41],\"resumen\":true}");
        verify(cola).encolar(EMPRESA, "notificaciones.correo",
                "{\"usuarioId\":6,\"notificaciones\":[42],\"resumen\":true}");
        verify(cola).encolar(9L, "notificaciones.correo",
                "{\"usuarioId\":5,\"notificaciones\":[43],\"resumen\":true}");
    }

    @Test
    @DisplayName("la purga borra lo leido antes de su retencion; anonimizar borra lo de la persona")
    void purgaYAnonimizacion() {
        when(notificacionRepository.borrarLeidasAntesDe(AHORA.minusDays(90))).thenReturn(7);

        assertThat(servicio.olvidarLeidas()).isEqualTo(7);
        servicio.borrarDe(EMPRESA, BETO);

        verify(notificacionRepository).borrarLasDe(EMPRESA, BETO);
        verify(preferenciaRepository).borrarLasDe(EMPRESA, BETO);
    }

    @Test
    @DisplayName("el conteo es el de la base")
    void contar() {
        when(notificacionRepository.countByEmpresaIdAndUsuarioIdAndLeidaEnIsNull(EMPRESA, BETO)).thenReturn(5L);

        assertThat(servicio.contar(EMPRESA, BETO)).isEqualTo(new ConteoDeNotificacionesResponse(5));
    }

    /** Un emisor que guarda lo que le mandan y sus avisos de cierre, en vez de escribir una respuesta. */
    private static final class Oyente extends SseEmitter {

        private final List<String> recibido = new ArrayList<>();
        private final List<Runnable> alCompletar = new ArrayList<>();
        private final List<Runnable> alVencer = new ArrayList<>();
        private final List<Consumer<Throwable>> alFallar = new ArrayList<>();
        private IOException falla;
        private Throwable error;

        @Override
        public void send(SseEventBuilder evento) throws IOException {
            if (falla != null) {
                throw falla;
            }
            StringBuilder texto = new StringBuilder();
            evento.build().forEach(parte -> texto.append(parte.getData()));
            recibido.add(texto.toString());
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            alCompletar.add(callback);
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            alVencer.add(callback);
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            alFallar.add(callback);
        }

        @Override
        public synchronized void completeWithError(Throwable causa) {
            error = causa;
        }

        void cerrar() {
            alCompletar.forEach(Runnable::run);
        }

        void vencer() {
            alVencer.forEach(Runnable::run);
        }

        void fallarAlCerrar() {
            alFallar.forEach(callback -> callback.accept(new IOException("se corto")));
        }
    }
}
