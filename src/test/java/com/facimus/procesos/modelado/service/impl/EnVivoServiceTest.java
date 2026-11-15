package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.facimus.procesos.common.DemasiadosIntentosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.gestion.event.ProcesoModificado;
import com.facimus.procesos.gestion.event.ProcesoPublicado;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.modelado.dto.response.BloqueoResponse;
import com.facimus.procesos.modelado.model.BloqueoEdicion;
import com.facimus.procesos.modelado.model.Presencia;
import com.facimus.procesos.modelado.repository.BloqueoEdicionRepository;
import com.facimus.procesos.modelado.repository.PresenciaRepository;

/**
 * PR 58: la edicion en vivo. Una persona abre pocas conexiones y cada una deja su presencia hasta que se cierra; el
 * bloqueo es del primero que edita mientras siga latiendo, se suelta solo por su dueno, y tomar el control queda en la
 * bitacora y se avisa con el nombre de quien lo perdio. Cada cambio sale por el canal a quien este mirando.
 */
@ExtendWith(MockitoExtension.class)
class EnVivoServiceTest {

    private static final Long EMPRESA = 1L;
    private static final Long PROCESO = 100L;
    private static final Long ANA = 2L;
    private static final Long BETO = 3L;
    private static final Duration VENCIMIENTO = Duration.ofSeconds(60);
    private static final BloqueoResponse LIBRE = new BloqueoResponse(true, false, null, null, null, null);

    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PresenciaRepository presenciaRepository;
    @Mock
    private BloqueoEdicionRepository bloqueoRepository;
    @Mock
    private CanalEnVivo canal;
    @Mock
    private Auditoria auditoria;

    private EnVivoServiceImpl enVivo;
    private Empresa empresa;
    private Proceso proceso;
    private Usuario ana;
    private Usuario beto;

    @BeforeEach
    void setUp() {
        enVivo = new EnVivoServiceImpl(procesoRepository, usuarioRepository, presenciaRepository, bloqueoRepository,
                canal, auditoria, VENCIMIENTO, 5);
        empresa = Empresa.builder().id(EMPRESA).nombre("Demo Store").build();
        proceso = Proceso.builder().id(PROCESO).empresa(empresa).nombre("Order fulfillment").build();
        ana = Usuario.builder().id(ANA).empresa(empresa).nombre("Ana").build();
        beto = Usuario.builder().id(BETO).empresa(empresa).nombre("Beto").build();
    }

    private BloqueoEdicion bloqueoDe(Long usuarioId, String nombre, LocalDateTime desde, LocalDateTime latido) {
        return BloqueoEdicion.builder().id(50L).empresa(empresa).procesoId(PROCESO).usuarioId(usuarioId)
                .nombre(nombre).desde(desde).ultimoLatido(latido).build();
    }

    private void procesoBloqueable() {
        when(procesoRepository.bloquear(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
    }

    private void bloqueoGuardado(BloqueoEdicion bloqueo) {
        when(bloqueoRepository.findByProcesoIdAndEmpresaId(PROCESO, EMPRESA)).thenReturn(Optional.ofNullable(bloqueo));
    }

    private void seGuarda() {
        when(bloqueoRepository.save(any(BloqueoEdicion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void usuario(Usuario usuario) {
        when(usuarioRepository.findByIdAndEmpresaId(usuario.getId(), EMPRESA)).thenReturn(Optional.of(usuario));
    }

    private static void entre(LocalDateTime valor, LocalDateTime antes, LocalDateTime despues) {
        assertThat(valor).isAfterOrEqualTo(antes).isBeforeOrEqualTo(despues);
    }

    // ---- Conexiones y presencia

    @Test
    @DisplayName("Conectarse a un proceso de otra tienda responde no encontrado y no deja presencia")
    void conectar_procesoAjeno_lanzaNoEncontrado() {
        SseEmitter emisor = mock(SseEmitter.class);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.conectar(EMPRESA, ANA, PROCESO, emisor))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(presenciaRepository, never()).save(any());
        verify(canal, never()).registrar(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("Un usuario que no es de la tienda no se conecta")
    void conectar_usuarioAjeno_lanzaNoEncontrado() {
        SseEmitter emisor = mock(SseEmitter.class);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(usuarioRepository.findByIdAndEmpresaId(ANA, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.conectar(EMPRESA, ANA, PROCESO, emisor))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Usuario no encontrado.");
        verify(presenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con el tope de conexiones vivas abiertas, la siguiente responde 429 y no deja presencia")
    void conectar_enElTope_lanzaDemasiadosIntentos() {
        SseEmitter emisor = mock(SseEmitter.class);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        usuario(ana);
        when(presenciaRepository.countByUsuarioIdAndEmpresaIdAndUltimoLatidoAfter(eq(ANA), eq(EMPRESA),
                any(LocalDateTime.class))).thenReturn(5L);

        assertThatThrownBy(() -> enVivo.conectar(EMPRESA, ANA, PROCESO, emisor))
                .isInstanceOfSatisfying(DemasiadosIntentosException.class, error -> {
                    assertThat(error).hasMessage("Tienes demasiadas pestañas abiertas en vivo: cierra alguna.");
                    assertThat(error.getSegundosDeEspera()).isEqualTo(30);
                });
        verify(presenciaRepository, never()).save(any());
        verify(canal, never()).registrar(anyString(), anyLong(), anyLong(), any());
        verify(canal, never()).publicar(anyLong(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Una conexion por debajo del tope deja su presencia, se registra en el canal y avisa a quien mira")
    void conectar_bajoElTope_dejaPresenciaYAvisa() {
        SseEmitter emisor = mock(SseEmitter.class);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        usuario(ana);
        when(presenciaRepository.countByUsuarioIdAndEmpresaIdAndUltimoLatidoAfter(eq(ANA), eq(EMPRESA),
                any(LocalDateTime.class))).thenReturn(4L);
        LocalDateTime antes = LocalDateTime.now();

        enVivo.conectar(EMPRESA, ANA, PROCESO, emisor);

        LocalDateTime despues = LocalDateTime.now();
        // Solo cuentan las conexiones que dieron un latido en el ultimo minuto
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(presenciaRepository).countByUsuarioIdAndEmpresaIdAndUltimoLatidoAfter(eq(ANA), eq(EMPRESA),
                desde.capture());
        entre(desde.getValue(), antes.minusSeconds(60), despues.minusSeconds(60));
        ArgumentCaptor<Presencia> guardada = ArgumentCaptor.forClass(Presencia.class);
        verify(presenciaRepository).save(guardada.capture());
        Presencia presencia = guardada.getValue();
        assertThat(presencia.getEmpresa()).isSameAs(empresa);
        assertThat(presencia.getProcesoId()).isEqualTo(PROCESO);
        assertThat(presencia.getUsuarioId()).isEqualTo(ANA);
        assertThat(presencia.getNombre()).isEqualTo("Ana");
        assertThat(presencia.getConexion()).hasSize(36);
        entre(presencia.getDesde(), antes, despues);
        assertThat(presencia.getUltimoLatido()).isEqualTo(presencia.getDesde());
        verify(canal).registrar(presencia.getConexion(), EMPRESA, PROCESO, emisor);
        verify(canal).publicar(EMPRESA, PROCESO, CanalEnVivo.PRESENCIA, Map.of());
    }

    @Test
    @DisplayName("Cada conexion tiene su propio identificador")
    void conectar_dosVeces_usaConexionesDistintas() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        usuario(ana);

        enVivo.conectar(EMPRESA, ANA, PROCESO, mock(SseEmitter.class));
        enVivo.conectar(EMPRESA, ANA, PROCESO, mock(SseEmitter.class));

        ArgumentCaptor<Presencia> guardadas = ArgumentCaptor.forClass(Presencia.class);
        verify(presenciaRepository, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues().get(0).getConexion())
                .isNotEqualTo(guardadas.getAllValues().get(1).getConexion());
    }

    @Test
    @DisplayName("Sin emisor propio se abre uno sin caducidad, y es el que queda registrado en el canal")
    void conectar_sinEmisor_abreUnoSinCaducidad() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        usuario(ana);

        SseEmitter emisor = enVivo.conectar(EMPRESA, ANA, PROCESO);

        assertThat(emisor).isNotNull();
        assertThat(emisor.getTimeout()).isZero();
        verify(canal).registrar(anyString(), eq(EMPRESA), eq(PROCESO), eq(emisor));
    }

    /** Conecta con un emisor simulado y devuelve el identificador de la conexion. */
    private String conectarCon(SseEmitter emisor) {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        usuario(ana);
        enVivo.conectar(EMPRESA, ANA, PROCESO, emisor);
        ArgumentCaptor<String> conexion = ArgumentCaptor.forClass(String.class);
        verify(canal).registrar(conexion.capture(), eq(EMPRESA), eq(PROCESO), eq(emisor));
        return conexion.getValue();
    }

    @Test
    @DisplayName("Al completarse la conexion se borra su presencia, con la tienda puesta, y se avisa a quien mira")
    void alCompletarse_borraLaPresenciaComoLaTienda() {
        SseEmitter emisor = mock(SseEmitter.class);
        String conexion = conectarCon(emisor);
        ArgumentCaptor<Runnable> alCompletarse = ArgumentCaptor.forClass(Runnable.class);
        verify(emisor).onCompletion(alCompletarse.capture());
        when(canal.quitar(conexion)).thenReturn(true);
        AtomicReference<Optional<ContextoDeTienda.Alcance>> alcance = new AtomicReference<>();
        when(presenciaRepository.borrarConexion(conexion)).thenAnswer(inv -> {
            alcance.set(ContextoDeTienda.actual());
            return 1;
        });

        alCompletarse.getValue().run();

        verify(presenciaRepository).borrarConexion(conexion);
        assertThat(alcance.get()).contains(ContextoDeTienda.Alcance.deLaTienda(EMPRESA));
        verify(canal, times(2)).publicar(EMPRESA, PROCESO, CanalEnVivo.PRESENCIA, Map.of());
    }

    @Test
    @DisplayName("Al caducar la conexion tambien se va su presencia")
    void alCaducar_borraLaPresencia() {
        SseEmitter emisor = mock(SseEmitter.class);
        String conexion = conectarCon(emisor);
        ArgumentCaptor<Runnable> alCaducar = ArgumentCaptor.forClass(Runnable.class);
        verify(emisor).onTimeout(alCaducar.capture());
        when(canal.quitar(conexion)).thenReturn(true);

        alCaducar.getValue().run();

        verify(presenciaRepository).borrarConexion(conexion);
    }

    @Test
    @DisplayName("Una conexion que falla tambien se va")
    @SuppressWarnings("unchecked")
    void alFallar_borraLaPresencia() {
        SseEmitter emisor = mock(SseEmitter.class);
        String conexion = conectarCon(emisor);
        ArgumentCaptor<Consumer<Throwable>> alFallar = ArgumentCaptor.forClass(Consumer.class);
        verify(emisor).onError(alFallar.capture());
        when(canal.quitar(conexion)).thenReturn(true);

        alFallar.getValue().accept(new IllegalStateException("cortada"));

        verify(presenciaRepository).borrarConexion(conexion);
    }

    @Test
    @DisplayName("Una conexion que ya no estaba registrada no se borra dos veces ni se avisa de nuevo")
    void alCerrarseDosVeces_noBorraNiAvisaOtraVez() {
        SseEmitter emisor = mock(SseEmitter.class);
        String conexion = conectarCon(emisor);
        ArgumentCaptor<Runnable> alCompletarse = ArgumentCaptor.forClass(Runnable.class);
        verify(emisor).onCompletion(alCompletarse.capture());
        when(canal.quitar(conexion)).thenReturn(false);

        alCompletarse.getValue().run();

        verify(presenciaRepository, never()).borrarConexion(anyString());
        verify(canal, times(1)).publicar(EMPRESA, PROCESO, CanalEnVivo.PRESENCIA, Map.of());
    }

    // ---- Estado del bloqueo

    @Test
    @DisplayName("El estado de un proceso de otra tienda responde no encontrado")
    void estado_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.estado(EMPRESA, ANA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
    }

    @Test
    @DisplayName("Sin bloqueo el proceso esta libre")
    void estado_sinBloqueo_estaLibre() {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        bloqueoGuardado(null);

        assertThat(enVivo.estado(EMPRESA, ANA, PROCESO)).isEqualTo(LIBRE);
    }

    @Test
    @DisplayName("Un bloqueo con latido dice de quien es, desde cuando y cuando vence, y que no es de quien pregunta")
    void estado_bloqueoVigenteDeOtro_diceDeQuienEs() {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(5);
        LocalDateTime latido = LocalDateTime.now().minusSeconds(10);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        bloqueoGuardado(bloqueoDe(BETO, "Beto", desde, latido));

        assertThat(enVivo.estado(EMPRESA, ANA, PROCESO))
                .isEqualTo(new BloqueoResponse(false, false, BETO, "Beto", desde, latido.plus(VENCIMIENTO)));
    }

    @Test
    @DisplayName("Un bloqueo propio con latido es mio")
    void estado_bloqueoPropio_esMio() {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(5);
        LocalDateTime latido = LocalDateTime.now().minusSeconds(10);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        bloqueoGuardado(bloqueoDe(ANA, "Ana", desde, latido));

        assertThat(enVivo.estado(EMPRESA, ANA, PROCESO))
                .isEqualTo(new BloqueoResponse(false, true, ANA, "Ana", desde, latido.plus(VENCIMIENTO)));
    }

    @Test
    @DisplayName("Un bloqueo sin latido durante el vencimiento ya no cuenta: el proceso esta libre")
    void estado_bloqueoVencido_estaLibre() {
        LocalDateTime latido = LocalDateTime.now().minus(VENCIMIENTO).minusSeconds(1);
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        bloqueoGuardado(bloqueoDe(BETO, "Beto", latido.minusMinutes(5), latido));

        assertThat(enVivo.estado(EMPRESA, ANA, PROCESO)).isEqualTo(LIBRE);
    }

    // ---- Adquirir

    @Test
    @DisplayName("Adquirir el bloqueo de un proceso de otra tienda responde no encontrado")
    void adquirir_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.bloquear(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.adquirir(EMPRESA, ANA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Proceso no encontrado.");
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    @DisplayName("El primero que edita se queda el bloqueo, desde ahora, y se avisa a quien mira")
    void adquirir_libre_loTomaYAvisa() {
        procesoBloqueable();
        bloqueoGuardado(null);
        seGuarda();
        usuario(ana);
        LocalDateTime antes = LocalDateTime.now();

        BloqueoResponse respuesta = enVivo.adquirir(EMPRESA, ANA, PROCESO);

        LocalDateTime despues = LocalDateTime.now();
        ArgumentCaptor<BloqueoEdicion> guardado = ArgumentCaptor.forClass(BloqueoEdicion.class);
        verify(bloqueoRepository).save(guardado.capture());
        BloqueoEdicion bloqueo = guardado.getValue();
        assertThat(bloqueo.getEmpresa()).isSameAs(empresa);
        assertThat(bloqueo.getProcesoId()).isEqualTo(PROCESO);
        assertThat(bloqueo.getUsuarioId()).isEqualTo(ANA);
        assertThat(bloqueo.getNombre()).isEqualTo("Ana");
        entre(bloqueo.getDesde(), antes, despues);
        assertThat(bloqueo.getUltimoLatido()).isEqualTo(bloqueo.getDesde());
        assertThat(respuesta).isEqualTo(new BloqueoResponse(false, true, ANA, "Ana", bloqueo.getDesde(),
                bloqueo.getUltimoLatido().plus(VENCIMIENTO)));
        verify(canal).publicar(EMPRESA, PROCESO, "bloqueo", Map.of("usuarioId", ANA, "nombre", "Ana"));
    }

    @Test
    @DisplayName("Si otro lo tiene con latido, adquirir no lo pisa: dice de quien es y no avisa nada")
    void adquirir_deOtroVigente_noLoPisa() {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(5);
        LocalDateTime latido = LocalDateTime.now().minusSeconds(10);
        procesoBloqueable();
        bloqueoGuardado(bloqueoDe(BETO, "Beto", desde, latido));

        BloqueoResponse respuesta = enVivo.adquirir(EMPRESA, ANA, PROCESO);

        assertThat(respuesta).isEqualTo(new BloqueoResponse(false, false, BETO, "Beto", desde,
                latido.plus(VENCIMIENTO)));
        verify(bloqueoRepository, never()).save(any());
        verify(canal, never()).publicar(anyLong(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Adquirir lo que ya es mio renueva el latido sin cambiar desde cuando ni avisar")
    void adquirir_propio_renuevaSinAvisar() {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(5);
        LocalDateTime latido = LocalDateTime.now().minusSeconds(10);
        BloqueoEdicion bloqueo = bloqueoDe(ANA, "Ana", desde, latido);
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(ana);
        LocalDateTime antes = LocalDateTime.now();

        BloqueoResponse respuesta = enVivo.adquirir(EMPRESA, ANA, PROCESO);

        verify(bloqueoRepository).save(bloqueo);
        assertThat(bloqueo.getDesde()).isEqualTo(desde);
        assertThat(bloqueo.getUltimoLatido()).isAfterOrEqualTo(antes);
        assertThat(respuesta.mio()).isTrue();
        assertThat(respuesta.desde()).isEqualTo(desde);
        verify(canal, never()).publicar(anyLong(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("El bloqueo vencido de otro se reemplaza: cambia de dueno, empieza de nuevo y se avisa")
    void adquirir_deOtroVencido_loTomaYAvisa() {
        LocalDateTime latido = LocalDateTime.now().minus(VENCIMIENTO).minusSeconds(1);
        BloqueoEdicion bloqueo = bloqueoDe(BETO, "Beto", latido.minusMinutes(5), latido);
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(ana);
        LocalDateTime antes = LocalDateTime.now();

        BloqueoResponse respuesta = enVivo.adquirir(EMPRESA, ANA, PROCESO);

        verify(bloqueoRepository).save(bloqueo);
        assertThat(bloqueo.getUsuarioId()).isEqualTo(ANA);
        assertThat(bloqueo.getNombre()).isEqualTo("Ana");
        assertThat(bloqueo.getDesde()).isAfterOrEqualTo(antes);
        assertThat(respuesta.mio()).isTrue();
        verify(canal).publicar(EMPRESA, PROCESO, "bloqueo", Map.of("usuarioId", ANA, "nombre", "Ana"));
    }

    @Test
    @DisplayName("Un bloqueo guardado sin fecha de inicio la toma al renovarse")
    void adquirir_propioSinDesde_poneDesde() {
        BloqueoEdicion bloqueo = bloqueoDe(ANA, "Ana", null, LocalDateTime.now().minusSeconds(10));
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(ana);
        LocalDateTime antes = LocalDateTime.now();

        enVivo.adquirir(EMPRESA, ANA, PROCESO);

        assertThat(bloqueo.getDesde()).isNotNull().isAfterOrEqualTo(antes);
    }

    @Test
    @DisplayName("Quien no es usuario de la tienda no se queda el bloqueo")
    void adquirir_usuarioAjeno_lanzaNoEncontrado() {
        procesoBloqueable();
        bloqueoGuardado(null);
        when(usuarioRepository.findByIdAndEmpresaId(ANA, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.adquirir(EMPRESA, ANA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Usuario no encontrado.");
        verify(bloqueoRepository, never()).save(any());
    }

    // ---- Latir

    @Test
    @DisplayName("Si otro tomo el control, el latido de quien lo perdio falla con su nombre")
    void latir_otroTomoElControl_lanzaReglaNegocio() {
        procesoBloqueable();
        bloqueoGuardado(bloqueoDe(BETO, "Beto", LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusSeconds(5)));

        assertThatThrownBy(() -> enVivo.latir(EMPRESA, ANA, PROCESO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Beto tomó el control de este proceso.");
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    @DisplayName("El latido de quien tiene el bloqueo lo renueva sin cambiar desde cuando y sin avisar")
    void latir_propio_renueva() {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(5);
        BloqueoEdicion bloqueo = bloqueoDe(ANA, "Ana", desde, LocalDateTime.now().minusSeconds(20));
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(ana);
        LocalDateTime antes = LocalDateTime.now();

        BloqueoResponse respuesta = enVivo.latir(EMPRESA, ANA, PROCESO);

        verify(bloqueoRepository).save(bloqueo);
        assertThat(bloqueo.getDesde()).isEqualTo(desde);
        assertThat(bloqueo.getUltimoLatido()).isAfterOrEqualTo(antes);
        assertThat(respuesta).isEqualTo(new BloqueoResponse(false, true, ANA, "Ana", desde,
                bloqueo.getUltimoLatido().plus(VENCIMIENTO)));
        verify(canal, never()).publicar(anyLong(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Si el bloqueo se vencio, el latido lo recupera desde ahora")
    void latir_vencido_loRecuperaDesdeAhora() {
        LocalDateTime latido = LocalDateTime.now().minus(VENCIMIENTO).minusSeconds(1);
        BloqueoEdicion bloqueo = bloqueoDe(BETO, "Beto", latido.minusMinutes(5), latido);
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(ana);
        LocalDateTime antes = LocalDateTime.now();

        BloqueoResponse respuesta = enVivo.latir(EMPRESA, ANA, PROCESO);

        assertThat(bloqueo.getUsuarioId()).isEqualTo(ANA);
        assertThat(bloqueo.getDesde()).isAfterOrEqualTo(antes);
        assertThat(respuesta.mio()).isTrue();
    }

    @Test
    @DisplayName("Latir en un proceso de otra tienda responde no encontrado")
    void latir_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.bloquear(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.latir(EMPRESA, ANA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- Soltar

    @Test
    @DisplayName("Quien tiene el bloqueo lo suelta: se borra, queda libre y se avisa con datos vacios")
    void soltar_propio_loBorraYAvisa() {
        BloqueoEdicion bloqueo = bloqueoDe(ANA, "Ana", LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusSeconds(5));
        procesoBloqueable();
        bloqueoGuardado(bloqueo);

        BloqueoResponse respuesta = enVivo.soltar(EMPRESA, ANA, PROCESO);

        assertThat(respuesta).isEqualTo(LIBRE);
        verify(bloqueoRepository).delete(bloqueo);
        verify(canal).publicar(EMPRESA, PROCESO, "bloqueo", Map.of());
    }

    @Test
    @DisplayName("Quien no tiene el bloqueo no lo suelta: sigue siendo del otro")
    void soltar_deOtro_noLoBorra() {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(1);
        LocalDateTime latido = LocalDateTime.now().minusSeconds(5);
        procesoBloqueable();
        bloqueoGuardado(bloqueoDe(BETO, "Beto", desde, latido));

        BloqueoResponse respuesta = enVivo.soltar(EMPRESA, ANA, PROCESO);

        assertThat(respuesta).isEqualTo(new BloqueoResponse(false, false, BETO, "Beto", desde,
                latido.plus(VENCIMIENTO)));
        verify(bloqueoRepository, never()).delete(any());
        verify(canal, never()).publicar(anyLong(), anyLong(), anyString(), anyMap());
    }

    @Test
    @DisplayName("Soltar sin bloqueo no borra nada y el proceso sigue libre")
    void soltar_sinBloqueo_sigueLibre() {
        procesoBloqueable();
        bloqueoGuardado(null);

        assertThat(enVivo.soltar(EMPRESA, ANA, PROCESO)).isEqualTo(LIBRE);
        verify(bloqueoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Soltar en un proceso de otra tienda responde no encontrado")
    void soltar_procesoAjeno_lanzaNoEncontrado() {
        when(procesoRepository.bloquear(PROCESO, EMPRESA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enVivo.soltar(EMPRESA, ANA, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(bloqueoRepository, never()).delete(any());
    }

    // ---- Tomar el control

    @Test
    @DisplayName("Tomar el control de otro queda en la bitacora y se avisa con quien lo tenia")
    void tomarControl_deOtro_quedaEnLaBitacoraYAvisa() {
        BloqueoEdicion bloqueo = bloqueoDe(ANA, "Ana", LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusSeconds(5));
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(beto);
        LocalDateTime antes = LocalDateTime.now();

        BloqueoResponse respuesta = enVivo.tomarControl(EMPRESA, BETO, PROCESO);

        assertThat(bloqueo.getUsuarioId()).isEqualTo(BETO);
        assertThat(bloqueo.getNombre()).isEqualTo("Beto");
        assertThat(bloqueo.getDesde()).isAfterOrEqualTo(antes);
        assertThat(respuesta.mio()).isTrue();
        assertThat(respuesta.nombre()).isEqualTo("Beto");
        verify(auditoria).registrar(EMPRESA, BETO, "CONTROL_TOMADO", "PROCESO", PROCESO, Map.of("anterior", ANA));
        verify(canal).publicar(EMPRESA, PROCESO, "control-tomado",
                Map.of("usuarioId", BETO, "nombre", "Beto", "anteriorId", ANA, "anterior", "Ana"));
        verify(canal, never()).publicar(anyLong(), anyLong(), eq("bloqueo"), anyMap());
    }

    @Test
    @DisplayName("Tomar el control de un proceso libre es adquirirlo: no va a la bitacora")
    void tomarControl_libre_soloAvisaElBloqueo() {
        procesoBloqueable();
        bloqueoGuardado(null);
        seGuarda();
        usuario(beto);

        BloqueoResponse respuesta = enVivo.tomarControl(EMPRESA, BETO, PROCESO);

        assertThat(respuesta.mio()).isTrue();
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any());
        verify(canal).publicar(EMPRESA, PROCESO, "bloqueo", Map.of("usuarioId", BETO, "nombre", "Beto"));
    }

    @Test
    @DisplayName("Tomar el control de lo que ya es mio no va a la bitacora y empieza de nuevo")
    void tomarControl_propio_noVaALaBitacora() {
        BloqueoEdicion bloqueo = bloqueoDe(BETO, "Beto", LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().minusSeconds(5));
        procesoBloqueable();
        bloqueoGuardado(bloqueo);
        seGuarda();
        usuario(beto);
        LocalDateTime antes = LocalDateTime.now();

        enVivo.tomarControl(EMPRESA, BETO, PROCESO);

        assertThat(bloqueo.getDesde()).isAfterOrEqualTo(antes);
        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any());
        verify(canal).publicar(EMPRESA, PROCESO, "bloqueo", Map.of("usuarioId", BETO, "nombre", "Beto"));
    }

    @Test
    @DisplayName("Un bloqueo vencido de otro no cuenta como control tomado")
    void tomarControl_deOtroVencido_noVaALaBitacora() {
        LocalDateTime latido = LocalDateTime.now().minus(VENCIMIENTO).minusSeconds(1);
        procesoBloqueable();
        bloqueoGuardado(bloqueoDe(ANA, "Ana", latido.minusMinutes(5), latido));
        seGuarda();
        usuario(beto);

        enVivo.tomarControl(EMPRESA, BETO, PROCESO);

        verify(auditoria, never()).registrar(any(), any(), any(), any(), any(), any());
        verify(canal).publicar(EMPRESA, PROCESO, "bloqueo", Map.of("usuarioId", BETO, "nombre", "Beto"));
    }

    // ---- Avisos de lo guardado y lo publicado

    @Test
    @DisplayName("Cada cambio guardado se avisa a quien mira el proceso, con su autor")
    void alModificarse_avisaElGuardado() {
        enVivo.alModificarse(new ProcesoModificado(EMPRESA, PROCESO, "Ana"));

        verify(canal).publicar(EMPRESA, PROCESO, "guardado", Map.of("autor", "Ana"));
    }

    @Test
    @DisplayName("Una version publicada se avisa con su numero y su autor")
    void alPublicarse_avisaElNumero() {
        enVivo.alPublicarse(new ProcesoPublicado(EMPRESA, PROCESO, 3, "Beto"));

        verify(canal).publicar(EMPRESA, PROCESO, "publicado", Map.of("numero", 3, "autor", "Beto"));
    }
}
