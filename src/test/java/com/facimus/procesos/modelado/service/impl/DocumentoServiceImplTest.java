package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.ArchivoDemasiadoGrandeException;
import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.TipoNoPermitidoException;
import com.facimus.procesos.common.almacen.AlmacenEnDisco;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.antivirus.Antivirus;
import com.facimus.procesos.common.antivirus.ResultadoDelAntivirus;
import com.facimus.procesos.common.archivos.Archivos;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.archivos.DetectorDeTipo;
import com.facimus.procesos.common.archivos.InspeccionDeZip;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.DocumentoDetalleResponse;
import com.facimus.procesos.modelado.dto.response.DocumentoResponse;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.model.Documento;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.VersionDocumento;
import com.facimus.procesos.modelado.repository.DocumentoRepository;
import com.facimus.procesos.modelado.repository.VersionDocumentoRepository;
import com.facimus.procesos.modelado.service.Descarga;

/** PR 61: subir, revisar y bajar documentos, con un almacen en disco de verdad y la base de mentira. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentoServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 7L;
    private static final Long PROCESO = 3L;
    private static final Instant AHORA = Instant.parse("2026-11-12T09:30:00Z");
    private static final long MEGA = 1024 * 1024;

    @Mock
    private DocumentoRepository documentoRepository;
    @Mock
    private VersionDocumentoRepository versionRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private ElementosDelProceso elementos;
    @Mock
    private HistorialCambioService historial;
    @Mock
    private Auditoria auditoria;
    @Mock
    private ColaDeTrabajos cola;
    @Mock
    private Antivirus antivirus;

    @TempDir
    Path raiz;
    @TempDir
    Path paso;

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final EnlacesFirmados enlaces = new EnlacesFirmados("secreto-de-las-pruebas-de-documentos", reloj);
    private AlmacenEnDisco almacen;
    private Proceso proceso;

    @BeforeEach
    void preparar() {
        almacen = new AlmacenEnDisco(raiz);
        proceso = Proceso.builder().id(PROCESO).empresa(Empresa.builder().id(EMPRESA).build()).build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(llamada -> {
            Documento documento = llamada.getArgument(0);
            documento.setId(12L);
            return documento;
        });
        when(documentoRepository.saveAndFlush(any(Documento.class))).thenAnswer(llamada -> llamada.getArgument(0));
        when(versionRepository.save(any(VersionDocumento.class))).thenAnswer(llamada -> {
            VersionDocumento version = llamada.getArgument(0);
            version.setId(40L + version.getNumero());
            return version;
        });
    }

    private DocumentoServiceImpl servicio(long tamanoMaximo, long cuota, boolean exigirAntivirus) {
        return new DocumentoServiceImpl(documentoRepository, versionRepository, procesoRepository, elementos,
                historial, auditoria, cola, almacen, antivirus, new DetectorDeTipo(),
                new InspeccionDeZip(10 * MEGA, 100, 1000), enlaces,
                new ArchivosTemporales(paso), reloj,
                new DocumentoServiceImpl.Limites(tamanoMaximo, cuota, exigirAntivirus, Duration.ofMinutes(5)));
    }

    private DocumentoServiceImpl servicio() {
        return servicio(25 * MEGA, 100 * MEGA, false);
    }

    private static InputStream cuerpo(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private Documento documento(int versionActual) {
        return Documento.builder().id(12L).empresa(proceso.getEmpresa()).proceso(proceso).nombre("empaque.pdf")
                .versionActual(versionActual).build();
    }

    private VersionDocumento version(Documento documento, int numero, String tipo, EstadoDocumento estado,
            String clave) {
        return VersionDocumento.builder().id(40L + numero).empresa(proceso.getEmpresa()).documento(documento)
                .numero(numero).nombre("empaque.pdf").tipo(tipo).tamano(100).sha256("ab").clave(clave).estado(estado)
                .build();
    }

    private String guardado(String clave, byte[] contenido) throws IOException {
        Path archivo = Files.write(raiz.resolve("origen-" + System.nanoTime()), contenido);
        almacen.guardar(clave, archivo, "application/octet-stream");
        return clave;
    }

    @Test
    @DisplayName("subir guarda el archivo con su huella, deja la version pendiente y encola su revision")
    void subir() throws IOException {
        DocumentoResponse respuesta = servicio().subir(EMPRESA, AUTOR, PROCESO, null, "Empaque final.pdf",
                cuerpo(Archivos.PDF));

        ArgumentCaptor<VersionDocumento> version = ArgumentCaptor.forClass(VersionDocumento.class);
        verify(versionRepository).save(version.capture());
        VersionDocumento guardada = version.getValue();
        assertThat(guardada.getEstado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(guardada.getNumero()).isEqualTo(1);
        assertThat(guardada.getTipo()).isEqualTo("application/pdf");
        assertThat(guardada.getTamano()).isEqualTo(Archivos.PDF.length);
        assertThat(guardada.getSha256()).isEqualTo(Huella.de(Archivos.PDF));
        assertThat(guardada.getSubidoPor()).isEqualTo(AUTOR);
        assertThat(guardada.getClave()).startsWith("empresas/1/documentos/12/1-");
        try (InputStream guardadoEnElAlmacen = almacen.abrir(guardada.getClave())) {
            assertThat(guardadoEnElAlmacen.readAllBytes()).isEqualTo(Archivos.PDF);
        }
        verify(cola).encolar(EMPRESA, DocumentoServiceImpl.TRABAJO_DE_REVISION, "41");
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Documento \"Empaque final.pdf\" agregado.");
        verify(auditoria).registrar(EMPRESA, AUTOR, "DOCUMENTO_SUBIDO", "DOCUMENTO", 12L,
                Map.of("version", 1, "tamano", (long) Archivos.PDF.length, "tipo", "application/pdf"));
        assertThat(respuesta.id()).isEqualTo(12L);
        assertThat(respuesta.nombre()).isEqualTo("Empaque final.pdf");
        assertThat(respuesta.estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(respuesta.versionActual()).isEqualTo(1);
        assertThat(respuesta.tamano()).isEqualTo(Archivos.PDF.length);
        assertThat(respuesta.revisado()).isFalse();
        assertThat(respuesta.procesoId()).isEqualTo(PROCESO);
    }

    @Test
    @DisplayName("la huella es el SHA-256 del archivo entero, en hexadecimal")
    void huella() {
        servicio().subir(EMPRESA, AUTOR, PROCESO, null, "a.txt", cuerpo(Archivos.TEXTO));

        ArgumentCaptor<VersionDocumento> version = ArgumentCaptor.forClass(VersionDocumento.class);
        verify(versionRepository).save(version.capture());
        assertThat(version.getValue().getSha256()).hasSize(64).matches("[0-9a-f]+")
                .isEqualTo(HexFormat.of().formatHex(sha256(Archivos.TEXTO)));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("un documento de un elemento necesita que el elemento exista en ese proceso")
    void elemento() {
        when(elementos.existe(EMPRESA, PROCESO, "Activity_1")).thenReturn(true);
        DocumentoServiceImpl servicio = servicio();
        InputStream pdf = cuerpo(Archivos.PDF);

        DocumentoResponse respuesta = servicio.subir(EMPRESA, AUTOR, PROCESO, "  Activity_1 ", "a.pdf", pdf);

        assertThat(respuesta.bpmnId()).isEqualTo("Activity_1");
        InputStream otro = cuerpo(Archivos.PDF);
        assertThatThrownBy(() -> servicio.subir(EMPRESA, AUTOR, PROCESO, "Activity_9", "a.pdf", otro))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("Activity_9");
    }

    @Test
    @DisplayName("sin elemento, un bpmnId en blanco es del proceso entero y no se busca")
    void enBlanco() {
        DocumentoResponse respuesta = servicio().subir(EMPRESA, AUTOR, PROCESO, " ", "a.pdf", cuerpo(Archivos.PDF));

        assertThat(respuesta.bpmnId()).isNull();
        verifyNoInteractions(elementos);
    }

    @Test
    @DisplayName("un proceso de otra tienda o borrado no existe")
    void procesoAjeno() {
        DocumentoServiceImpl servicio = servicio();
        InputStream pdf = cuerpo(Archivos.PDF);

        assertThatThrownBy(() -> servicio.subir(EMPRESA, AUTOR, 99L, null, "a.pdf", pdf))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.listar(EMPRESA, 99L, null)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("vacio, de un tipo que no se acepta, mas grande que el tope o sin cuota: no se guarda nada")
    void rechazos() {
        DocumentoServiceImpl chico = servicio(1024, 100 * MEGA, false);
        InputStream vacio = cuerpo(new byte[0]);
        InputStream exe = cuerpo(Archivos.EXE);
        InputStream grande = cuerpo(new byte[2048]);

        assertThatThrownBy(() -> chico.subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", vacio))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessage("El archivo está vacío.");
        assertThatThrownBy(() -> chico.subir(EMPRESA, AUTOR, PROCESO, null, "informe.pdf", exe))
                .isInstanceOf(TipoNoPermitidoException.class);
        assertThatThrownBy(() -> chico.subir(EMPRESA, AUTOR, PROCESO, null, "a.txt", grande))
                .isInstanceOf(ArchivoDemasiadoGrandeException.class)
                .hasMessage("Un documento no puede pasar de 0 MB.");

        verify(documentoRepository, never()).save(any());
        verify(versionRepository, never()).save(any());
        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("el tope de un archivo se cuenta en bytes: el limite justo entra, uno mas no")
    void topeJusto() {
        DocumentoServiceImpl justo = servicio(Archivos.PDF.length, 100 * MEGA, false);
        DocumentoServiceImpl menos = servicio(Archivos.PDF.length - 1L, 100 * MEGA, false);
        InputStream pdf = cuerpo(Archivos.PDF);

        assertThat(justo.subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", cuerpo(Archivos.PDF)).tamano())
                .isEqualTo(Archivos.PDF.length);
        assertThatThrownBy(() -> menos.subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", pdf))
                .isInstanceOf(ArchivoDemasiadoGrandeException.class);
    }

    @Test
    @DisplayName("la cuota es de la tienda: lo que ya ocupa mas el archivo no puede pasarla; justo en ella si entra")
    void cuota() {
        long tamano = Archivos.PDF.length;
        when(versionRepository.ocupado(EMPRESA)).thenReturn(1000L);
        DocumentoServiceImpl justo = servicio(MEGA, 1000L + tamano, false);
        DocumentoServiceImpl lleno = servicio(MEGA, 1000L + tamano - 1, false);
        InputStream pdf = cuerpo(Archivos.PDF);

        assertThat(justo.subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", cuerpo(Archivos.PDF))).isNotNull();
        assertThatThrownBy(() -> lleno.subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", pdf))
                .isInstanceOf(ArchivoDemasiadoGrandeException.class)
                .hasMessage("La organización llegó a su espacio para documentos.");
    }

    @Test
    @DisplayName("otra version sube el numero, cambia el nombre del documento y lo dice en el historial")
    void otraVersion() {
        Documento documento = documento(1);
        when(documentoRepository.findByIdAndEmpresaId(12L, EMPRESA)).thenReturn(Optional.of(documento));

        DocumentoResponse respuesta = servicio().subirVersion(EMPRESA, AUTOR, 12L, "empaque v2.pdf",
                cuerpo(Archivos.PDF));

        assertThat(respuesta.versionActual()).isEqualTo(2);
        assertThat(respuesta.nombre()).isEqualTo("empaque v2.pdf");
        assertThat(documento.getVersionActual()).isEqualTo(2);
        verify(cola).encolar(EMPRESA, DocumentoServiceImpl.TRABAJO_DE_REVISION, "42");
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Documento \"empaque v2.pdf\": versión 2.");
        verify(auditoria).registrar(EMPRESA, AUTOR, "DOCUMENTO_SUBIDO", "DOCUMENTO", 12L,
                Map.of("version", 2, "tamano", (long) Archivos.PDF.length, "tipo", "application/pdf"));
    }

    @Test
    @DisplayName("la lista trae cada documento con su ultima version, en una consulta; sin documentos no pregunta")
    void listar() {
        Documento documento = documento(2);
        when(documentoRepository.findAllByProcesoIdAndEmpresaIdOrderByNombreAscIdAsc(PROCESO, EMPRESA))
                .thenReturn(List.of(documento));
        VersionDocumento ultima = version(documento, 2, "application/pdf", EstadoDocumento.BLOQUEADO, "c");
        ultima.setMotivo("El antivirus encontró X.");
        when(versionRepository.ultimas(EMPRESA, List.of(12L))).thenReturn(List.of(ultima));
        when(documentoRepository.findAllByProcesoIdAndBpmnIdAndEmpresaIdOrderByNombreAscIdAsc(PROCESO, "Lane_1",
                EMPRESA)).thenReturn(List.of());

        List<DocumentoResponse> todos = servicio().listar(EMPRESA, PROCESO, null);
        List<DocumentoResponse> deLaLane = servicio().listar(EMPRESA, PROCESO, " Lane_1 ");

        assertThat(todos).singleElement().satisfies(uno -> {
            assertThat(uno.estado()).isEqualTo(EstadoDocumento.BLOQUEADO);
            assertThat(uno.motivo()).isEqualTo("El antivirus encontró X.");
            assertThat(uno.versionActual()).isEqualTo(2);
        });
        assertThat(deLaLane).isEmpty();
        verify(versionRepository).ultimas(EMPRESA, List.of(12L));
    }

    @Test
    @DisplayName("el detalle trae todas las versiones, la mas nueva primero, y el documento con la mas nueva")
    void obtener() {
        Documento documento = documento(2);
        when(documentoRepository.findByIdAndEmpresaId(12L, EMPRESA)).thenReturn(Optional.of(documento));
        when(versionRepository.findAllByDocumento_IdAndEmpresaIdOrderByNumeroDesc(12L, EMPRESA)).thenReturn(List.of(
                version(documento, 2, "application/pdf", EstadoDocumento.PENDIENTE, "b"),
                version(documento, 1, "application/pdf", EstadoDocumento.DISPONIBLE, "a")));

        DocumentoServiceImpl servicio = servicio();

        DocumentoDetalleResponse detalle = servicio.obtener(EMPRESA, 12L);

        assertThat(detalle.documento().estado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(detalle.versiones()).extracting(v -> v.numero()).containsExactly(2, 1);
        assertThatThrownBy(() -> servicio.obtener(EMPRESA, 13L)).isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Documento no encontrado.");
    }

    @Test
    @DisplayName("el enlace es de una version disponible, firmado por cinco minutos; pendiente o retenida no tiene")
    void enlace() {
        Documento documento = documento(2);
        when(documentoRepository.findByIdAndEmpresaId(12L, EMPRESA)).thenReturn(Optional.of(documento));
        VersionDocumento retenida = version(documento, 2, "application/pdf", EstadoDocumento.BLOQUEADO, "b");
        retenida.setMotivo("El archivo crece demasiado al abrirlo.");
        when(versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(12L, 2, EMPRESA))
                .thenReturn(Optional.of(retenida));
        when(versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(12L, 1, EMPRESA))
                .thenReturn(Optional.of(version(documento, 1, "application/pdf", EstadoDocumento.DISPONIBLE, "a")));
        DocumentoServiceImpl servicio = servicio();

        EnlaceDeDescargaResponse enlace = servicio.enlace(EMPRESA, 12L, 1);

        assertThat(enlace.venceEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(enlace.url()).startsWith("/api/v1/descargas/documentos/12/1?empresa=1&vence="
                + enlace.venceEn().getEpochSecond() + "&firma=");
        String firma = enlace.url().substring(enlace.url().indexOf("&firma=") + "&firma=".length());
        assertThat(enlaces.vale("documento:1:12:1", enlace.venceEn(), firma)).isTrue();
        assertThatThrownBy(() -> servicio.enlace(EMPRESA, 12L, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esta versión está retenida: El archivo crece demasiado al abrirlo.");
        retenida.setEstado(EstadoDocumento.PENDIENTE);
        assertThatThrownBy(() -> servicio.enlace(EMPRESA, 12L, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("todavía se está revisando");
        assertThatThrownBy(() -> servicio.enlace(EMPRESA, 12L, 5)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("bajar con una firma buena entrega el archivo; un SVG va como binario, nunca como imagen")
    void descargar() throws IOException {
        Documento documento = documento(2);
        when(documentoRepository.findByIdAndEmpresaId(12L, EMPRESA)).thenReturn(Optional.of(documento));
        when(versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(12L, 1, EMPRESA)).thenReturn(Optional.of(
                version(documento, 1, "application/pdf", EstadoDocumento.DISPONIBLE, guardado("empresas/1/x/a",
                        Archivos.PDF))));
        when(versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(12L, 2, EMPRESA)).thenReturn(Optional.of(
                version(documento, 2, "image/svg+xml", EstadoDocumento.DISPONIBLE, guardado("empresas/1/x/b",
                        Archivos.SVG_CON_SCRIPT))));
        DocumentoServiceImpl servicio = servicio();
        long vence = AHORA.plusSeconds(60).getEpochSecond();

        Descarga pdf = servicio.descargar(EMPRESA, 12L, 1, vence,
                enlaces.firmar("documento:1:12:1", Instant.ofEpochSecond(vence)));
        Descarga svg = servicio.descargar(EMPRESA, 12L, 2, vence,
                enlaces.firmar("documento:1:12:2", Instant.ofEpochSecond(vence)));

        assertThat(pdf.tipo()).isEqualTo("application/pdf");
        assertThat(pdf.nombre()).isEqualTo("empaque.pdf");
        assertThat(pdf.tamano()).isEqualTo(100);
        try (InputStream contenido = pdf.contenido()) {
            assertThat(contenido.readAllBytes()).isEqualTo(Archivos.PDF);
        }
        assertThat(svg.tipo()).isEqualTo("application/octet-stream");
        svg.contenido().close();
        assertThat(ContextoDeTienda.actual()).as("bajar no deja la tienda puesta en el hilo").isEmpty();
    }

    @Test
    @DisplayName("una firma de otra version, de otra tienda o vencida no lleva a nada: ni se mira la base")
    void firmasMalas() {
        DocumentoServiceImpl servicio = servicio();
        long vence = AHORA.plusSeconds(60).getEpochSecond();
        String deLaUno = enlaces.firmar("documento:1:12:1", Instant.ofEpochSecond(vence));
        long vencido = AHORA.minusSeconds(1).getEpochSecond();
        String vieja = enlaces.firmar("documento:1:12:1", Instant.ofEpochSecond(vencido));

        assertThatThrownBy(() -> servicio.descargar(EMPRESA, 12L, 2, vence, deLaUno))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("venció");
        assertThatThrownBy(() -> servicio.descargar(2L, 12L, 1, vence, deLaUno))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.descargar(EMPRESA, 12L, 1, vencido, vieja))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(documentoRepository, versionRepository);
    }

    @Test
    @DisplayName("una version que no esta disponible no se baja aunque el enlace sea bueno")
    void noDisponible() {
        Documento documento = documento(1);
        when(documentoRepository.findByIdAndEmpresaId(12L, EMPRESA)).thenReturn(Optional.of(documento));
        when(versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(12L, 1, EMPRESA))
                .thenReturn(Optional.of(version(documento, 1, "application/pdf", EstadoDocumento.BLOQUEADO, "a")));
        long vence = AHORA.plusSeconds(60).getEpochSecond();
        String firma = enlaces.firmar("documento:1:12:1", Instant.ofEpochSecond(vence));
        DocumentoServiceImpl servicio = servicio();

        Alcance antes = ContextoDeTienda.fijar(Alcance.SISTEMA);
        try {
            assertThatThrownBy(() -> servicio.descargar(EMPRESA, 12L, 1, vence, firma))
                    .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Versión no encontrada.");
            assertThat(ContextoDeTienda.actual()).as("aun fallando, vuelve el alcance de antes")
                    .contains(Alcance.SISTEMA);
        } finally {
            ContextoDeTienda.restaurar(antes);
        }
    }

    @Test
    @DisplayName("revisar: limpio queda disponible y revisado; con virus, retenido con la firma y en la bitacora")
    void revisarConAntivirus() throws IOException {
        Documento documento = documento(1);
        VersionDocumento limpia = version(documento, 1, "application/pdf", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/limpia", Archivos.PDF));
        VersionDocumento infectada = version(documento, 2, "text/plain", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/infectada", Archivos.TEXTO));
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(limpia));
        when(versionRepository.findByIdAndEmpresaId(42L, EMPRESA)).thenReturn(Optional.of(infectada));
        when(antivirus.disponible()).thenReturn(true);
        when(antivirus.revisar(any())).thenReturn(ResultadoDelAntivirus.sinHallazgos(),
                ResultadoDelAntivirus.infectado("Eicar-Test-Signature"));

        servicio().revisar(EMPRESA, 41L);
        servicio().revisar(EMPRESA, 42L);

        assertThat(limpia.getEstado()).isEqualTo(EstadoDocumento.DISPONIBLE);
        assertThat(limpia.isRevisado()).isTrue();
        assertThat(limpia.getMotivo()).isNull();
        assertThat(infectada.getEstado()).isEqualTo(EstadoDocumento.BLOQUEADO);
        assertThat(infectada.isRevisado()).isTrue();
        assertThat(infectada.getMotivo()).isEqualTo("El antivirus encontró Eicar-Test-Signature.");
        verify(auditoria).registrar(EMPRESA, null, "DOCUMENTO_BLOQUEADO", "DOCUMENTO", 12L, Map.of("version", 2));
        // PR 65: solo lo que el antivirus da por bueno pasa a leerse para la busqueda
        verify(cola).encolar(EMPRESA, TextoDeDocumentosServiceImpl.TRABAJO_DE_LECTURA, "41");
        verify(cola, never()).encolar(EMPRESA, TextoDeDocumentosServiceImpl.TRABAJO_DE_LECTURA, "42");
    }

    @Test
    @DisplayName("revisar: un documento de Office que es una bomba se retiene sin llegar al antivirus")
    void revisarBomba() throws IOException {
        Documento documento = documento(1);
        VersionDocumento bomba = version(documento, 1,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/bomba", Archivos.bomba(20)));
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(bomba));

        servicio().revisar(EMPRESA, 41L);

        assertThat(bomba.getEstado()).isEqualTo(EstadoDocumento.BLOQUEADO);
        assertThat(bomba.getMotivo()).isEqualTo("El archivo crece demasiado al abrirlo.");
        assertThat(bomba.isRevisado()).isFalse();
        verifyNoInteractions(antivirus, cola);
        assertThat(archivosDePaso()).as("la copia del ZIP se borra").isZero();
    }

    @Test
    @DisplayName("revisar: un documento de Office sano pasa al antivirus")
    void revisarOfficeSano() throws IOException {
        Documento documento = documento(1);
        VersionDocumento docx = version(documento, 1,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/docx", Archivos.docx()));
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(docx));
        when(antivirus.disponible()).thenReturn(true);
        when(antivirus.revisar(any())).thenReturn(ResultadoDelAntivirus.sinHallazgos());

        servicio().revisar(EMPRESA, 41L);

        assertThat(docx.getEstado()).isEqualTo(EstadoDocumento.DISPONIBLE);
    }

    @Test
    @DisplayName("revisar sin antivirus: disponible sin revisar, salvo que se exija, que espera con el motivo")
    void revisarSinAntivirus() throws IOException {
        Documento documento = documento(1);
        VersionDocumento libre = version(documento, 1, "application/pdf", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/libre", Archivos.PDF));
        VersionDocumento esperando = version(documento, 2, "application/pdf", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/esperando", Archivos.PDF));
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(libre));
        when(versionRepository.findByIdAndEmpresaId(42L, EMPRESA)).thenReturn(Optional.of(esperando));
        when(antivirus.disponible()).thenReturn(false);

        servicio().revisar(EMPRESA, 41L);
        servicio(MEGA, MEGA, true).revisar(EMPRESA, 42L);

        assertThat(libre.getEstado()).isEqualTo(EstadoDocumento.DISPONIBLE);
        assertThat(libre.isRevisado()).isFalse();
        assertThat(esperando.getEstado()).isEqualTo(EstadoDocumento.PENDIENTE);
        assertThat(esperando.getMotivo()).isEqualTo("Sin antivirus configurado: el documento espera su revisión.");
        verify(antivirus, never()).revisar(any());
        verify(cola).encolar(EMPRESA, TextoDeDocumentosServiceImpl.TRABAJO_DE_LECTURA, "41");
        verify(cola, never()).encolar(EMPRESA, TextoDeDocumentosServiceImpl.TRABAJO_DE_LECTURA, "42");
    }

    @Test
    @DisplayName("revisar: una imagen queda disponible sin pasar a leerse, que no tiene texto que buscar")
    void revisarImagen() throws IOException {
        Documento documento = documento(1);
        VersionDocumento imagen = version(documento, 1, "image/png", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/imagen", Archivos.PNG));
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(imagen));
        when(antivirus.disponible()).thenReturn(false);

        servicio().revisar(EMPRESA, 41L);

        assertThat(imagen.getEstado()).isEqualTo(EstadoDocumento.DISPONIBLE);
        verifyNoInteractions(cola);
    }

    @Test
    @DisplayName("revisar es idempotente: lo que ya no esta pendiente, o no existe, no se toca")
    void revisarIdempotente() {
        Documento documento = documento(1);
        VersionDocumento lista = version(documento, 1, "application/pdf", EstadoDocumento.DISPONIBLE, "x");
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(lista));

        servicio().revisar(EMPRESA, 41L);
        servicio().revisar(EMPRESA, 99L);

        assertThat(lista.getEstado()).isEqualTo(EstadoDocumento.DISPONIBLE);
        verifyNoInteractions(antivirus, auditoria, cola);
    }

    @Test
    @DisplayName("un motivo larguisimo se corta a lo que cabe en la columna")
    void motivoLargo() throws IOException {
        Documento documento = documento(1);
        VersionDocumento version = version(documento, 1, "application/pdf", EstadoDocumento.PENDIENTE,
                guardado("empresas/1/x/largo", Archivos.PDF));
        when(versionRepository.findByIdAndEmpresaId(41L, EMPRESA)).thenReturn(Optional.of(version));
        when(antivirus.disponible()).thenReturn(true);
        when(antivirus.revisar(any())).thenReturn(ResultadoDelAntivirus.infectado("x".repeat(400)));

        servicio().revisar(EMPRESA, 41L);

        assertThat(version.getMotivo()).hasSize(300).startsWith("El antivirus encontró xxx");
    }

    @Test
    @DisplayName("borrar lo saca del proceso, lo dice en el historial y en la bitacora")
    void eliminar() {
        Documento documento = documento(1);
        when(documentoRepository.findByIdAndEmpresaId(12L, EMPRESA)).thenReturn(Optional.of(documento));

        servicio().eliminar(EMPRESA, AUTOR, 12L);

        verify(documentoRepository).delete(documento);
        verify(historial).registrar(EMPRESA, AUTOR, proceso, "Documento \"empaque.pdf\" eliminado.");
        verify(auditoria).registrar(EMPRESA, AUTOR, "DOCUMENTO_ELIMINADO", "DOCUMENTO", 12L, Map.of());
    }

    @Test
    @DisplayName("ningun archivo temporal queda atras, salga bien o mal la subida")
    void sinTemporales() throws IOException {
        DocumentoServiceImpl chico = servicio(16, MEGA, false);
        InputStream grande = cuerpo(Archivos.PDF);

        servicio().subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", cuerpo(Archivos.PDF));
        assertThat(archivosDePaso()).isZero();
        assertThatThrownBy(() -> chico.subir(EMPRESA, AUTOR, PROCESO, null, "a.pdf", grande))
                .isInstanceOf(ArchivoDemasiadoGrandeException.class);

        assertThat(archivosDePaso()).isZero();
    }

    private long archivosDePaso() throws IOException {
        try (Stream<Path> archivos = Files.list(paso)) {
            return archivos.count();
        }
    }
}
