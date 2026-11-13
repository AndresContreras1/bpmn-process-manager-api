package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.CorreoSaliente;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.EstadoExportacion;
import com.facimus.procesos.gestion.model.Exportacion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.ExportacionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

/**
 * PR 66: el correo con el enlace de una exportacion lista, con las plantillas y los enlaces de verdad: en el idioma de
 * quien la pidio, con el enlace firmado hasta el vencimiento, su tamano, sus archivos y su huella. Si ya vencio, o
 * quien la pidio ya no esta, no sale.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorreoDeExportacionTest {

    private static final Long EMPRESA = 1L;
    private static final Long ANA = 7L;
    private static final Instant AHORA = Instant.parse("2026-11-12T10:00:30Z");
    private static final LocalDateTime VENCE = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Mock
    private ExportacionRepository exportacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private Correo correo;

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final EnlacesFirmados enlaces = new EnlacesFirmados("secreto-de-las-pruebas-de-exportaciones", reloj);
    private final Empresa acme = Empresa.builder().id(EMPRESA).nombre("Acme <Store>").build();
    private CorreoDeExportacion correos;
    private Exportacion exportacion;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        correos = new CorreoDeExportacion(exportacionRepository, usuarioRepository, configuracionRepository, enlaces,
                new Redactor(), correo, reloj, "https://tienda.example/");
        exportacion = Exportacion.builder().id(4L).empresa(acme).estado(EstadoExportacion.LISTA).pedidaPor(ANA)
                .tamano(2049L).archivos(42).sha256("ab".repeat(32)).venceEn(VENCE).build();
        ana = Usuario.builder().id(ANA).nombre("Ana").email("ana@acme.test").activo(true).build();
        when(exportacionRepository.findByIdAndEmpresaId(4L, EMPRESA)).thenReturn(Optional.of(exportacion));
        when(usuarioRepository.findByIdAndEmpresaId(ANA, EMPRESA)).thenReturn(Optional.of(ana));
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(ConfiguracionTienda.builder().idioma("fr-FR").build()));
    }

    private void ejecutar() {
        correos.ejecutar(new Trabajo(9, EMPRESA, CorreoDeExportacion.TRABAJO_DE_CORREO, "4", 1, 5));
    }

    private CorreoSaliente enviado() {
        ArgumentCaptor<CorreoSaliente> enviado = ArgumentCaptor.forClass(CorreoSaliente.class);
        verify(correo).enviar(enviado.capture());
        return enviado.getValue();
    }

    @Test
    @DisplayName("sale a quien la pidio, en su idioma, con el enlace firmado hasta el vencimiento, el tamano, los "
            + "archivos, las horas que le quedan y la huella; el nombre de la tienda sale escapado en el HTML")
    void enSuIdioma() {
        ana.setIdioma("en-US");

        ejecutar();

        CorreoSaliente enviado = enviado();
        long vence = VENCE.toInstant(ZoneOffset.UTC).getEpochSecond();
        String enlace = "https://tienda.example/api/v1/descargas/exportaciones/4?empresa=1&vence=" + vence
                + "&firma=" + enlaces.firmar("exportacion:1:4", Instant.ofEpochSecond(vence));
        assertThat(correos.tipo()).isEqualTo("exportaciones.correo");
        assertThat(enviado.para()).isEqualTo("ana@acme.test");
        assertThat(enviado.asunto()).isEqualTo("The export of Acme <Store> is ready");
        assertThat(enviado.texto()).contains("Hello, Ana:", enlace, "42 files in a ZIP of 3 KB", "expires in 23 hours",
                "ab".repeat(32));
        assertThat(enviado.html()).contains("href=\"" + enlace.replace("&", "&amp;") + "\"", "Acme &lt;Store&gt;")
                .doesNotContain("Acme <Store>");
    }

    @Test
    @DisplayName("sin idioma propio, el de su organizacion; sin el de la organizacion, el espanol")
    void idiomaDeLaOrganizacion() {
        ejecutar();
        assertThat(enviado().asunto()).isEqualTo("L’export de Acme <Store> est prêt");
    }

    @Test
    @DisplayName("sin configuracion de la tienda, en espanol")
    void enEspanol() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.empty());

        ejecutar();

        assertThat(enviado().asunto()).isEqualTo("La exportación de Acme <Store> está lista");
    }

    @Test
    @DisplayName("una que ya no esta LISTA, vencida, sin quien la pidio, o de alguien inactivo, no manda nada")
    void noSale() {
        exportacion.setEstado(EstadoExportacion.VENCIDA);
        ejecutar();
        exportacion.setEstado(EstadoExportacion.LISTA);
        exportacion.setVenceEn(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC));
        ejecutar();
        exportacion.setVenceEn(VENCE);
        exportacion.setPedidaPor(null);
        ejecutar();
        exportacion.setPedidaPor(ANA);
        ana.setActivo(false);
        ejecutar();
        when(exportacionRepository.findByIdAndEmpresaId(4L, EMPRESA)).thenReturn(Optional.empty());
        ejecutar();

        verify(correo, never()).enviar(any());
    }

    @Test
    @DisplayName("una hora antes del vencimiento dice una hora, nunca cero")
    void unaHora() {
        exportacion.setVenceEn(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusMinutes(30));

        ejecutar();

        assertThat(enviado().texto()).contains("dans 1 heures");
    }

    @Test
    @DisplayName("el tamano va en KB, redondeado hacia arriba y nunca cero")
    void kilobytes() {
        assertThat(CorreoDeExportacion.kilobytes(0)).isEqualTo("1 KB");
        assertThat(CorreoDeExportacion.kilobytes(1)).isEqualTo("1 KB");
        assertThat(CorreoDeExportacion.kilobytes(1024)).isEqualTo("1 KB");
        assertThat(CorreoDeExportacion.kilobytes(1025)).isEqualTo("2 KB");
        assertThat(CorreoDeExportacion.kilobytes(10 * 1024 * 1024)).isEqualTo("10240 KB");
    }
}
