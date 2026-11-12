package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.archivos.TipoDeArchivo;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.VersionDocumento;
import com.facimus.procesos.modelado.repository.VersionDocumentoRepository;
import com.facimus.procesos.modelado.service.TextoDeDocumentosService;
import com.facimus.procesos.modelado.service.impl.ExtractorDeTexto.Resultado;

/** PR 65: leer el texto de un archivo solo cuando ya esta disponible, fuera de toda transaccion, y anotarlo una vez. */
@ExtendWith(MockitoExtension.class)
class TextoDeDocumentosServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long VERSION = 41L;
    private static final String CLAVE = "empresas/1/documentos/12/1/x";
    private static final byte[] CONTENIDO = {'%', 'P', 'D', 'F'};
    private static final Instant AHORA = Instant.parse("2026-11-12T10:15:30.123456789Z");

    @Mock
    private VersionDocumentoRepository versionRepository;
    @Mock
    private Almacen almacen;
    @Mock
    private ExtractorDeTexto extractor;

    @TempDir
    private Path paso;

    private final Transacciones transacciones = new Transacciones();
    private final List<Integer> abiertasAlLeer = new ArrayList<>();
    private final List<byte[]> leidos = new ArrayList<>();
    private TextoDeDocumentosServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new TextoDeDocumentosServiceImpl(versionRepository, almacen, new ArchivosTemporales(paso),
                extractor, Clock.fixed(AHORA, ZoneOffset.UTC), transacciones);
    }

    private VersionDocumento version(String tipo, EstadoDocumento estado, LocalDateTime textoExtraidoEn) {
        return VersionDocumento.builder().id(VERSION).empresa(Empresa.builder().id(EMPRESA).build()).numero(1)
                .nombre("archivo").tipo(tipo).tamano(4).sha256("ab").clave(CLAVE).estado(estado)
                .textoExtraidoEn(textoExtraidoEn).build();
    }

    private void existe(VersionDocumento version) {
        when(versionRepository.findByIdAndEmpresaId(VERSION, EMPRESA)).thenReturn(Optional.of(version));
    }

    /** El extractor de mentira anota cuantas transacciones habia abiertas y que archivo le dieron. */
    private void extraera(TipoDeArchivo tipo, Resultado resultado) {
        when(extractor.extraer(any(Path.class), eq(tipo))).thenAnswer(llamada -> {
            abiertasAlLeer.add(transacciones.abiertas);
            leidos.add(Files.readAllBytes(llamada.getArgument(0, Path.class)));
            return resultado;
        });
    }

    private long archivosDePaso() throws IOException {
        try (Stream<Path> archivos = Files.list(paso)) {
            return archivos.count();
        }
    }

    @Test
    @DisplayName("una version disponible se lee de una copia, fuera de toda transaccion, y se guarda con la hora")
    void leer() throws IOException {
        existe(version("application/pdf", EstadoDocumento.DISPONIBLE, null));
        when(almacen.abrir(CLAVE)).thenReturn(new ByteArrayInputStream(CONTENIDO));
        extraera(TipoDeArchivo.PDF, new Resultado("Politica de credito", null));

        servicio.leer(EMPRESA, VERSION);

        verify(versionRepository).guardarTexto(EMPRESA, VERSION, "Politica de credito",
                LocalDateTime.parse("2026-11-12T10:15:30.123456"));
        assertThat(leidos).singleElement().isEqualTo(CONTENIDO);
        assertThat(abiertasAlLeer).containsExactly(0);
        assertThat(transacciones.soloLectura).as("mirar si hace falta, y anotar").containsExactly(true, false);
        assertThat(transacciones.abiertas).isZero();
        assertThat(archivosDePaso()).as("la copia se borra").isZero();
    }

    @Test
    @DisplayName("lo que no se deja leer se anota sin texto, con la hora: la cola no lo intenta otra vez")
    void sinTexto() {
        existe(version("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                EstadoDocumento.DISPONIBLE, null));
        when(almacen.abrir(CLAVE)).thenReturn(new ByteArrayInputStream(CONTENIDO));
        extraera(TipoDeArchivo.DOCX, new Resultado(null, "TikaException: roto"));

        servicio.leer(EMPRESA, VERSION);

        verify(versionRepository).guardarTexto(EMPRESA, VERSION, null,
                LocalDateTime.parse("2026-11-12T10:15:30.123456"));
    }

    @Test
    @DisplayName("lo leido hasta el tope se guarda como cualquier texto")
    void recortado() {
        existe(version("text/plain", EstadoDocumento.DISPONIBLE, null));
        when(almacen.abrir(CLAVE)).thenReturn(new ByteArrayInputStream(CONTENIDO));
        extraera(TipoDeArchivo.TEXTO, new Resultado("los primeros", "El texto pasa de 10 caracteres."));

        servicio.leer(EMPRESA, VERSION);

        verify(versionRepository).guardarTexto(EMPRESA, VERSION, "los primeros",
                LocalDateTime.parse("2026-11-12T10:15:30.123456"));
    }

    @Test
    @DisplayName("una imagen se anota sin pasar por ningun parser: no tiene texto")
    void imagen() {
        existe(version("image/png", EstadoDocumento.DISPONIBLE, null));

        servicio.leer(EMPRESA, VERSION);

        verify(versionRepository).guardarTexto(EMPRESA, VERSION, null,
                LocalDateTime.parse("2026-11-12T10:15:30.123456"));
        verifyNoInteractions(extractor, almacen);
    }

    @Test
    @DisplayName("nada se lee antes de que el antivirus lo de por bueno, ni lo retenido, ni lo ya leido, ni lo que no hay")
    void noSeLee() {
        when(versionRepository.findByIdAndEmpresaId(VERSION, EMPRESA)).thenReturn(
                Optional.of(version("application/pdf", EstadoDocumento.PENDIENTE, null)),
                Optional.of(version("application/pdf", EstadoDocumento.BLOQUEADO, null)),
                Optional.of(version("application/pdf", EstadoDocumento.DISPONIBLE, LocalDateTime.now())),
                Optional.empty());

        for (int vez = 0; vez < 4; vez++) {
            servicio.leer(EMPRESA, VERSION);
        }

        verifyNoInteractions(extractor, almacen);
        verify(versionRepository, never()).guardarTexto(any(), any(), any(), any());
        assertThat(transacciones.soloLectura).containsOnly(true);
    }

    @Test
    @DisplayName("si el almacen no responde, el trabajo falla para que la cola lo intente otra vez, sin anotar nada")
    void almacenCaido() throws IOException {
        existe(version("application/pdf", EstadoDocumento.DISPONIBLE, null));
        when(almacen.abrir(CLAVE)).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("se corto");
            }
        });

        assertThatThrownBy(() -> servicio.leer(EMPRESA, VERSION)).isInstanceOf(UncheckedIOException.class)
                .hasMessage("No se pudo bajar " + CLAVE + " del almacen");
        verify(versionRepository, never()).guardarTexto(any(), any(), any(), any());
        verifyNoInteractions(extractor);
        assertThat(archivosDePaso()).isZero();
    }

    @Test
    @DisplayName("el trabajo de la cola lee la version de sus datos, en la tienda del trabajo")
    void trabajo() {
        TextoDeDocumentosService textos = mock(TextoDeDocumentosService.class);
        LecturaDeDocumentos lectura = new LecturaDeDocumentos(textos);

        lectura.ejecutar(new Trabajo(1, 7L, TextoDeDocumentosServiceImpl.TRABAJO_DE_LECTURA, "42", 1, 5));

        assertThat(lectura.tipo()).isEqualTo("documentos.leer-texto");
        verify(textos).leer(7L, 42L);
    }

    /** Transacciones de mentira que llevan la cuenta: cuantas hay abiertas y si cada una era de solo lectura. */
    private static final class Transacciones implements PlatformTransactionManager {

        private final List<Boolean> soloLectura = new ArrayList<>();
        private int abiertas;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definicion) {
            abiertas++;
            soloLectura.add(definicion != null && definicion.isReadOnly());
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus estado) {
            abiertas--;
        }

        @Override
        public void rollback(TransactionStatus estado) {
            abiertas--;
        }
    }
}
