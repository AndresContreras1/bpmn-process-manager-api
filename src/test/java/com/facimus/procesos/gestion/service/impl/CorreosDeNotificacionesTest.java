package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.CorreoSaliente;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.notificaciones.EnvioPorCorreo;
import com.facimus.procesos.common.notificaciones.TipoDeNotificacion;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.Notificacion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.NotificacionRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 63: el correo de las notificaciones, con las plantillas de verdad. Una sola sale con el asunto de su tipo; el
 * resumen las lista; solo cuenta lo no leido, nunca lleva el texto de un comentario, y lo que entra al HTML sale
 * escapado. Lo que ya se resolvio no vuelve a salir, y quien ya no esta no lo recibe.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorreosDeNotificacionesTest {

    private static final Long EMPRESA = 1L;
    private static final Long ANA = 2L;
    private static final Long BETO = 5L;
    private static final Long PROCESO = 3L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private Correo correo;

    private final JsonMapper json = JsonMapper.builder().build();
    private CorreosDeNotificaciones correos;
    private Empresa empresa;
    private Usuario beto;

    @BeforeEach
    void preparar() {
        correos = new CorreosDeNotificaciones(json, notificacionRepository, usuarioRepository, procesoRepository,
                configuracionRepository, new Redactor(), correo,
                Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), "https://bpm.example.com/");
        empresa = Empresa.builder().id(EMPRESA).build();
        beto = Usuario.builder().id(BETO).nombre("Beto").email("beto@acme.com").rolAcceso(RolAcceso.EDITOR)
                .empresa(empresa).build();
        Usuario ana = Usuario.builder().id(ANA).nombre("<b>Ana</b>").rolAcceso(RolAcceso.EDITOR).empresa(empresa)
                .build();
        when(usuarioRepository.findByIdAndEmpresaId(BETO, EMPRESA)).thenReturn(Optional.of(beto));
        when(usuarioRepository.findAllByEmpresaIdAndIdIn(eq(EMPRESA), anyCollection())).thenAnswer(llamada -> {
            Collection<Long> ids = llamada.getArgument(1);
            return ids.contains(ANA) ? List.of(ana) : List.of();
        });
        when(procesoRepository.findAllByIdInAndEmpresaId(anyCollection(), eq(EMPRESA)))
                .thenReturn(List.of(Proceso.builder().id(PROCESO).nombre("Despacho").empresa(empresa).build()));
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(ConfiguracionTienda.builder().idioma("fr-FR").build()));
    }

    private Notificacion notificacion(Long id, TipoDeNotificacion tipo, LocalDateTime leida) {
        return Notificacion.builder().id(id).empresa(empresa).usuarioId(BETO).tipo(tipo).autorId(ANA)
                .procesoId(PROCESO).referenciaId(12L).envio(EnvioPorCorreo.INMEDIATO).creadaEn(AHORA).leidaEn(leida)
                .build();
    }

    private void pendientes(List<Long> ids, Notificacion... notificaciones) {
        when(notificacionRepository.findAllByEmpresaIdAndUsuarioIdAndIdInAndCorreoEnIsNullOrderByCreadaEnAscIdAsc(
                EMPRESA, BETO, ids)).thenReturn(List.of(notificaciones));
    }

    private static Trabajo trabajo(String datos) {
        return new Trabajo(1L, EMPRESA, CorreosDeNotificaciones.TRABAJO_DE_CORREO, datos, 1, 5);
    }

    private CorreoSaliente enviado() {
        ArgumentCaptor<CorreoSaliente> enviado = ArgumentCaptor.forClass(CorreoSaliente.class);
        verify(correo).enviar(enviado.capture());
        return enviado.getValue();
    }

    @Test
    @DisplayName("una mencion sale en seguida con el asunto de su tipo, en el idioma de la persona y con su enlace")
    void una() {
        beto.setIdioma("es-CO");
        pendientes(List.of(40L), notificacion(40L, TipoDeNotificacion.MENCION, null));

        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}"));

        CorreoSaliente salido = enviado();
        assertThat(salido.para()).isEqualTo("beto@acme.com");
        assertThat(salido.asunto()).isEqualTo("<b>Ana</b> te mencionó en Despacho");
        assertThat(salido.texto()).contains("Hola, Beto:", "<b>Ana</b> te mencionó en un comentario del proceso",
                "https://bpm.example.com/es-CO/procesos/3?comentario=12", "https://bpm.example.com/es-CO/cuenta");
        assertThat(salido.html()).contains("&lt;b&gt;Ana&lt;/b&gt;", "href=\"https://bpm.example.com/es-CO/procesos/3"
                + "?comentario=12\"").doesNotContain("<b>Ana");
        verify(notificacionRepository).resolverCorreo(EMPRESA, BETO, List.of(40L), AHORA);
        Assertions.assertThat(correos.tipo()).isEqualTo("notificaciones.correo");
    }

    @Test
    @DisplayName("el resumen lista lo que sigue sin leer, en el idioma de la organizacion si la persona no eligio")
    void resumen() {
        pendientes(List.of(40L, 41L, 42L), notificacion(40L, TipoDeNotificacion.RESPUESTA, null),
                notificacion(41L, TipoDeNotificacion.MENCION, AHORA),
                notificacion(42L, TipoDeNotificacion.HILO_RESUELTO, null));

        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40,41,42],\"resumen\":true}"));

        CorreoSaliente salido = enviado();
        assertThat(salido.asunto()).isEqualTo("Vos notifications du jour : 2 non lues");
        assertThat(salido.texto()).contains("Bonjour Beto,", "<b>Ana</b> a répondu dans Despacho",
                "<b>Ana</b> a résolu un fil dans Despacho", "https://bpm.example.com/fr-FR/procesos/3?comentario=12")
                .doesNotContain("mentionné");
        assertThat(salido.html()).contains("&lt;b&gt;Ana&lt;/b&gt; a répondu dans Despacho", "lang=\"fr\"");
        // Lo leido tambien queda resuelto: no vuelve a esperar al resumen de manana
        verify(notificacionRepository).resolverCorreo(EMPRESA, BETO, List.of(40L, 41L, 42L), AHORA);
    }

    @Test
    @DisplayName("PR 64: un aviso de una solicitud enlaza la solicitud, con su boton, en el idioma de la persona")
    void solicitud() {
        beto.setIdioma("en-US");
        pendientes(List.of(40L), notificacion(40L, TipoDeNotificacion.APROBACION_PEDIDA, null));

        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}"));

        CorreoSaliente salido = enviado();
        assertThat(salido.asunto()).isEqualTo("<b>Ana</b> asks for your approval of Despacho");
        assertThat(salido.texto()).contains("https://bpm.example.com/en-US/procesos/3?solicitud=12")
                .doesNotContain("comentario=");
        assertThat(salido.html()).contains("See the request", "href=\"https://bpm.example.com/en-US/procesos/3"
                + "?solicitud=12\"");
    }

    @Test
    @DisplayName("PR 64: un aviso de recertificacion enlaza el proceso solo, aunque traiga una referencia")
    void recertificacion() {
        beto.setIdioma("es-CO");
        pendientes(List.of(40L), notificacion(40L, TipoDeNotificacion.RECERTIFICACION_VENCIDA, null));

        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}"));

        CorreoSaliente salido = enviado();
        assertThat(salido.asunto()).isEqualTo("La revisión de Despacho está vencida");
        assertThat(salido.texto()).contains("https://bpm.example.com/es-CO/procesos/3").doesNotContain("procesos/3?");
        assertThat(salido.html()).contains("Ver el proceso", "href=\"https://bpm.example.com/es-CO/procesos/3\"");
    }

    @Test
    @DisplayName("PR 64: un aviso de una solicitud sin su referencia enlaza el proceso")
    void solicitudSinReferencia() {
        beto.setIdioma("fr-FR");
        Notificacion sinReferencia = notificacion(40L, TipoDeNotificacion.SOLICITUD_PUBLICADA, null);
        sinReferencia.setReferenciaId(null);
        pendientes(List.of(40L), sinReferencia);

        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}"));

        CorreoSaliente salido = enviado();
        assertThat(salido.asunto()).isEqualTo("Despacho est publié");
        assertThat(salido.html()).contains("Voir la demande", "href=\"https://bpm.example.com/fr-FR/procesos/3\"");
    }

    @Test
    @DisplayName("sin nada sin leer, o si la persona ya no esta activa, no sale nada pero queda resuelto")
    void nadaQueContar() {
        pendientes(List.of(41L), notificacion(41L, TipoDeNotificacion.MENCION, AHORA));
        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[41],\"resumen\":true}"));

        beto.setActivo(false);
        pendientes(List.of(40L), notificacion(40L, TipoDeNotificacion.MENCION, null));
        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}"));

        verify(correo, never()).enviar(any());
        verify(notificacionRepository).resolverCorreo(EMPRESA, BETO, List.of(41L), AHORA);
        verify(notificacionRepository).resolverCorreo(EMPRESA, BETO, List.of(40L), AHORA);
    }

    @Test
    @DisplayName("lo que ya se resolvio no vuelve a salir; si el correo falla no queda resuelto y la cola reintenta")
    void reintento() {
        correos.ejecutar(trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}"));
        verify(correo, never()).enviar(any());
        verify(notificacionRepository, never()).resolverCorreo(any(), any(), anyCollection(), any());

        pendientes(List.of(40L), notificacion(40L, TipoDeNotificacion.MENCION, null));
        doThrow(new IllegalStateException("el servidor no contesta")).when(correo).enviar(any());
        Trabajo otraVez = trabajo("{\"usuarioId\":5,\"notificaciones\":[40],\"resumen\":false}");
        Assertions.assertThatThrownBy(() -> correos.ejecutar(otraVez)).isInstanceOf(IllegalStateException.class);
        verify(notificacionRepository, never()).resolverCorreo(any(), any(), anyCollection(), any());
    }
}
