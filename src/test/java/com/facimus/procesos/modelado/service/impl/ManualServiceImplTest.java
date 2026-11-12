package com.facimus.procesos.modelado.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.almacen.AlmacenEnDisco;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.PreferenciasResponse;
import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.PreferenciasService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.CaracterizacionResponse;
import com.facimus.procesos.modelado.dto.response.DocumentoResponse;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.dto.response.ManualResponse;
import com.facimus.procesos.modelado.model.Documentacion;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.EstadoManual;
import com.facimus.procesos.modelado.model.Manual;
import com.facimus.procesos.modelado.repository.DocumentacionRepository;
import com.facimus.procesos.modelado.repository.ManualRepository;
import com.facimus.procesos.modelado.service.Descarga;
import com.facimus.procesos.modelado.service.DocumentacionService;
import com.facimus.procesos.modelado.service.DocumentoService;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** PR 62: pedir, armar y bajar un manual, con la redaccion y el almacen de verdad y lo demas de mentira. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long AUTOR = 7L;
    private static final Long PROCESO = 3L;
    private static final Instant AHORA = Instant.parse("2026-11-12T10:00:00Z");

    /** Un diagrama publicado, lo justo: una lane con cuatro pasos fuera de orden y otra vacia. */
    private static final String DIAGRAMA = """
            {"proceso":{"id":3,"nombre":"Despacho","descripcion":"Del pago a la entrega","categoria":"Fulfillment"},
             "compartido":false,
             "pools":[{"id":1,"bpmnId":"Participant_1","nombre":"Tienda"}],
             "lanes":[{"id":10,"bpmnId":"Lane_1","nombre":"Bodega","poolId":1,"rolProcesoNombre":"Bodeguero"},
                      {"id":11,"bpmnId":"Lane_2","nombre":"Vacia","poolId":1}],
             "actividades":[{"id":20,"bpmnId":"Activity_2","nombre":"Empacar","descripcion":"Con cinta","laneId":10,
                             "posicionX":300},
                            {"id":21,"bpmnId":"Activity_1","nombre":"Revisar","laneId":10,"posicionX":100}],
             "gateways":[{"id":30,"bpmnId":"Gateway_1","nombre":"","laneId":10,"posicionX":200}],
             "eventos":[{"id":40,"bpmnId":"Event_1","nombre":"Pedido pagado","laneId":10,"posicionX":20}],
             "arcos":[],"mensajes":[],"correlaciones":[]}
            """;

    /** El diagrama de arriba es parcial: lo que no trae se lee vacio, como haria uno real con todos sus campos. */
    private static final JsonMapper PARCIAL = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    @Mock
    private ManualRepository manualRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private DocumentacionRepository documentacionRepository;
    @Mock
    private VersionService versionService;
    @Mock
    private DocumentacionService documentacionService;
    @Mock
    private DocumentoService documentoService;
    @Mock
    private PreferenciasService preferenciasService;
    @Mock
    private ColaDeTrabajos cola;

    @TempDir
    Path raiz;
    @TempDir
    Path paso;

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final EnlacesFirmados enlaces = new EnlacesFirmados("secreto-de-las-pruebas-de-manuales", reloj);
    private final Transacciones transacciones = new Transacciones();
    private final List<Integer> abiertasAlImprimir = new ArrayList<>();
    private AlmacenEnDisco almacen;
    private ManualServiceImpl servicio;
    private Proceso proceso;

    @BeforeEach
    void preparar() {
        almacen = new AlmacenEnDisco(raiz);
        RedaccionDelManual redaccion = new RedaccionDelManual() {
            @Override
            public byte[] pdf(String html) {
                abiertasAlImprimir.add(transacciones.abiertas);
                return super.pdf(html);
            }
        };
        servicio = new ManualServiceImpl(manualRepository, procesoRepository, documentacionRepository, versionService,
                documentacionService, documentoService, preferenciasService, redaccion, cola, almacen, enlaces,
                new ArchivosTemporales(paso), PARCIAL, reloj, transacciones, Duration.ofMinutes(5));
        proceso = Proceso.builder().id(PROCESO).empresa(Empresa.builder().id(EMPRESA).build()).nombre("Despacho")
                .build();
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(PROCESO, EMPRESA)).thenReturn(Optional.of(proceso));
        when(manualRepository.save(any(Manual.class))).thenAnswer(llamada -> {
            Manual manual = llamada.getArgument(0);
            manual.setId(5L);
            return manual;
        });
        when(preferenciasService.obtener(EMPRESA, AUTOR))
                .thenReturn(new PreferenciasResponse("es-CO", "America/Bogota", null, null, "es-CO", "America/Bogota"));
    }

    private Manual manual(EstadoManual estado) {
        return Manual.builder().id(5L).empresa(proceso.getEmpresa()).proceso(proceso).versionNumero(3)
                .idioma("es-CO").estado(estado).pedidoEn(LocalDateTime.of(2026, 11, 12, 10, 0)).build();
    }

    private void conVersion() {
        VersionResponse version = mock(VersionResponse.class);
        when(version.numero()).thenReturn(3);
        when(version.fechaPublicacion()).thenReturn(LocalDateTime.of(2026, 11, 10, 9, 30));
        when(versionService.obtener(EMPRESA, PROCESO, 3)).thenReturn(version);
        when(versionService.definicion(EMPRESA, PROCESO, 3)).thenReturn(DIAGRAMA);
    }

    @Test
    @DisplayName("pedir sin version es pedir la vigente, en el idioma de quien pide; la cola lo arma")
    void pedirLaVigente() {
        proceso.setVersionPublicada(3);

        ManualResponse respuesta = servicio.pedir(EMPRESA, AUTOR, PROCESO, null);

        assertThat(respuesta.version()).isEqualTo(3);
        assertThat(respuesta.idioma()).isEqualTo("es-CO");
        assertThat(respuesta.estado()).isEqualTo(EstadoManual.PENDIENTE);
        assertThat(respuesta.pedidoEn()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));
        verify(cola).encolar(EMPRESA, ManualServiceImpl.TRABAJO_DE_GENERACION, "5");
    }

    @Test
    @DisplayName("pedir una version que existe vale; sin vigente y sin version, no hay manual que armar")
    void pedirOtra() {
        conVersion();

        assertThat(servicio.pedir(EMPRESA, AUTOR, PROCESO, 3).version()).isEqualTo(3);
        assertThatThrownBy(() -> servicio.pedir(EMPRESA, AUTOR, PROCESO, null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no tiene una versión vigente");
        assertThatThrownBy(() -> servicio.pedir(EMPRESA, AUTOR, 99L, 3))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("armar junta la ficha, el dibujo, la documentacion y los pasos en orden, y deja HTML y PDF")
    void generar() throws IOException {
        conVersion();
        Manual manual = manual(EstadoManual.PENDIENTE);
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(manual));
        when(versionService.dibujo(EMPRESA, PROCESO, 3)).thenReturn(Optional.of(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\"><rect width=\"40\" "
                        + "height=\"20\"/></svg>"));
        when(documentacionService.caracterizacion(EMPRESA, PROCESO)).thenReturn(new CaracterizacionResponse(
                "Entregar a tiempo", null, null, null, null, null, null, "Retrasos", null, 0L, null));
        when(documentacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(PROCESO, EMPRESA)).thenReturn(List.of(
                Documentacion.builder().html("<p>Todo el proceso</p>").build(),
                Documentacion.builder().bpmnId("Activity_2").html("<p>Doble cinta</p>").build()));
        when(documentoService.listar(EMPRESA, PROCESO, null)).thenReturn(List.of(
                documento("instrucciones.pdf", EstadoDocumento.DISPONIBLE),
                documento("virus.txt", EstadoDocumento.BLOQUEADO)));

        servicio.generar(EMPRESA, 5L);

        assertThat(manual.getEstado()).isEqualTo(EstadoManual.DISPONIBLE);
        assertThat(manual.getListoEn()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));
        String html = leer(manual.getClaveHtml());
        assertThat(html).contains("Despacho", "Versión 3, publicada el 2026-11-10", "Categoría: Fulfillment",
                "Objetivo", "Entregar a tiempo", "Riesgos", "<rect", "Todo el proceso", "Bodega", "Lo hace: Bodeguero",
                "Doble cinta", "Con cinta", "instrucciones.pdf (versión 2)").doesNotContain("virus.txt", "Vacia",
                "Alcance");
        assertThat(html.indexOf("Pedido pagado")).isLessThan(html.indexOf("Revisar"));
        assertThat(html.indexOf("Revisar")).isLessThan(html.indexOf("Decisión"));
        assertThat(html.indexOf("Decisión")).isLessThan(html.indexOf("Empacar"));
        try (InputStream pdf = almacen.abrir(manual.getClavePdf())) {
            assertThat(new String(pdf.readNBytes(5), StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        }
        assertThat(manual.getClaveHtml()).isEqualTo("empresas/1/manuales/5/html");
        assertThat(manual.getClavePdf()).isEqualTo("empresas/1/manuales/5/pdf");
        assertThat(paso).as("los archivos de paso se borran al guardarse").isEmptyDirectory();
        assertThat(abiertasAlImprimir).as("imprimir no retiene ninguna transaccion").containsExactly(0);
        assertThat(transacciones.soloLectura).as("leer el pedido, leer el contenido, anotar").containsExactly(true,
                true, false);
        assertThat(transacciones.abiertas).isZero();
    }

    @Test
    @DisplayName("sin dibujo el manual lo dice; en ingles, sus titulos estan en ingles")
    void sinDibujoEnIngles() throws IOException {
        conVersion();
        Manual manual = Manual.builder().id(5L).empresa(proceso.getEmpresa()).proceso(proceso).versionNumero(3)
                .idioma("en-US").estado(EstadoManual.PENDIENTE).pedidoEn(LocalDateTime.now()).build();
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(manual));
        when(versionService.dibujo(EMPRESA, PROCESO, 3)).thenReturn(Optional.empty());
        when(documentacionService.caracterizacion(EMPRESA, PROCESO)).thenReturn(new CaracterizacionResponse(null,
                null, null, null, null, null, null, null, null, null, null));

        servicio.generar(EMPRESA, 5L);

        assertThat(leer(manual.getClaveHtml())).contains("This version was published without its drawing.", "Steps",
                "Version 3, published on 2026-11-10").doesNotContain("Characterization");
    }

    @Test
    @DisplayName("si no se puede armar, queda fallido con el motivo; lo que ya no esta pendiente no se vuelve a armar")
    void fallido() {
        Manual manual = manual(EstadoManual.PENDIENTE);
        Manual listo = manual(EstadoManual.DISPONIBLE);
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(manual));
        when(manualRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(listo));
        when(versionService.obtener(EMPRESA, PROCESO, 3)).thenThrow(new RecursoNoEncontradoException("Sin versión."));

        servicio.generar(EMPRESA, 5L);
        servicio.generar(EMPRESA, 6L);
        servicio.generar(EMPRESA, 7L);

        assertThat(manual.getEstado()).isEqualTo(EstadoManual.FALLIDO);
        assertThat(manual.getMotivo()).isEqualTo("Sin versión.");
        assertThat(listo.getEstado()).isEqualTo(EstadoManual.DISPONIBLE);
        assertThat(listo.getListoEn()).isNull();
        assertThat(abiertasAlImprimir).isEmpty();
        assertThat(transacciones.deshechas).as("la lectura que fallo se deshace").isEqualTo(1);
        assertThat(transacciones.abiertas).isZero();
    }

    @Test
    @DisplayName("el motivo se corta en 300 letras; una falla sin mensaje se nombra por su clase")
    void motivos() {
        Manual largo = manual(EstadoManual.PENDIENTE);
        Manual mudo = manual(EstadoManual.PENDIENTE);
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(largo));
        when(manualRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(mudo));
        when(versionService.obtener(EMPRESA, PROCESO, 3)).thenThrow(new IllegalStateException("x".repeat(400)))
                .thenThrow(new IllegalStateException());

        servicio.generar(EMPRESA, 5L);
        servicio.generar(EMPRESA, 6L);

        assertThat(largo.getMotivo()).isEqualTo("x".repeat(300));
        assertThat(mudo.getMotivo()).isEqualTo("IllegalStateException");
        assertThat(List.of(largo, mudo)).allSatisfy(manual -> {
            assertThat(manual.getEstado()).isEqualTo(EstadoManual.FALLIDO);
            assertThat(manual.getClaveHtml()).isNull();
            assertThat(manual.getListoEn()).isEqualTo(LocalDateTime.of(2026, 11, 12, 10, 0));
        });
    }

    @Test
    @DisplayName("si otro lo anoto mientras se imprimia, no se pisa: queda como lo dejo el otro")
    void noSePisa() {
        conVersion();
        Manual pendiente = manual(EstadoManual.PENDIENTE);
        Manual cancelado = manual(EstadoManual.FALLIDO);
        cancelado.setMotivo("Cancelado.");
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(pendiente),
                Optional.of(cancelado));
        when(documentacionService.caracterizacion(EMPRESA, PROCESO)).thenReturn(new CaracterizacionResponse(null,
                null, null, null, null, null, null, null, null, null, null));

        servicio.generar(EMPRESA, 5L);

        assertThat(abiertasAlImprimir).containsExactly(0);
        assertThat(cancelado.getEstado()).isEqualTo(EstadoManual.FALLIDO);
        assertThat(cancelado.getMotivo()).isEqualTo("Cancelado.");
        assertThat(cancelado.getClavePdf()).isNull();
        assertThat(cancelado.getListoEn()).isNull();
        assertThat(pendiente.getClavePdf()).as("el que se leyo al principio no se toca").isNull();
    }

    @Test
    @DisplayName("el enlace es de un manual listo, en pdf o html; armandose o fallido no tiene")
    void enlace() {
        Manual listo = manual(EstadoManual.DISPONIBLE);
        Manual armandose = manual(EstadoManual.PENDIENTE);
        Manual fallido = manual(EstadoManual.FALLIDO);
        fallido.setMotivo("Sin versión.");
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(listo));
        when(manualRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(armandose));
        when(manualRepository.findByIdAndEmpresaId(7L, EMPRESA)).thenReturn(Optional.of(fallido));

        EnlaceDeDescargaResponse enlace = servicio.enlace(EMPRESA, 5L, "pdf");

        assertThat(enlace.url()).startsWith("/api/v1/descargas/manuales/5/pdf?empresa=1&vence=");
        assertThat(enlace.venceEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThatThrownBy(() -> servicio.enlace(EMPRESA, 6L, "pdf")).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("todavía se está armando");
        assertThatThrownBy(() -> servicio.enlace(EMPRESA, 7L, "html")).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("El manual no se pudo armar: Sin versión.");
        assertThatThrownBy(() -> servicio.enlace(EMPRESA, 5L, "docx")).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    @DisplayName("bajar con un enlace bueno da el archivo con un nombre limpio; uno malo o vencido no lleva a nada")
    void descargar() throws IOException {
        conVersion();
        Manual manual = manual(EstadoManual.PENDIENTE);
        proceso.setNombre("Despacho / Devoluciones: <2026>");
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(manual));
        when(documentacionService.caracterizacion(EMPRESA, PROCESO)).thenReturn(new CaracterizacionResponse(null,
                null, null, null, null, null, null, null, null, null, null));
        servicio.generar(EMPRESA, 5L);
        long vence = AHORA.plusSeconds(60).getEpochSecond();
        String firma = enlaces.firmar("manual:1:5:pdf", Instant.ofEpochSecond(vence));

        Descarga pdf = servicio.descargar(EMPRESA, 5L, "pdf", vence, firma);

        assertThat(pdf.tipo()).isEqualTo("application/pdf");
        assertThat(pdf.nombre()).isEqualTo("Despacho  Devoluciones 2026 v3.pdf");
        try (InputStream contenido = pdf.contenido()) {
            assertThat(contenido.readAllBytes()).hasSize((int) pdf.tamano());
        }
        assertThatThrownBy(() -> servicio.descargar(EMPRESA, 5L, "html", vence, firma))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.descargar(EMPRESA, 5L, "pdf", vence + 1, firma))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(ManualServiceImpl.archivo("   ", 1, "html")).isEqualTo("manual v1.html");
        assertThat(ContextoDeTienda.actual()).as("bajar no deja la tienda puesta en el hilo").isEmpty();
    }

    @Test
    @DisplayName("un manual fallido no se baja aunque la firma sea buena, y el hilo vuelve al alcance de antes")
    void descargarFallido() {
        Manual fallido = manual(EstadoManual.FALLIDO);
        when(manualRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(fallido));
        long vence = AHORA.plusSeconds(60).getEpochSecond();
        String firma = enlaces.firmar("manual:1:5:html", Instant.ofEpochSecond(vence));
        Alcance antes = ContextoDeTienda.fijar(Alcance.SISTEMA);
        try {
            assertThatThrownBy(() -> servicio.descargar(EMPRESA, 5L, "html", vence, firma))
                    .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Manual no encontrado.");
            assertThat(ContextoDeTienda.actual()).contains(Alcance.SISTEMA);
        } finally {
            ContextoDeTienda.restaurar(antes);
        }
    }

    @Test
    @DisplayName("la lista es la del proceso; un manual de otra tienda no existe")
    void listarYObtener() {
        when(manualRepository.findAllByProcesoIdAndEmpresaIdOrderByIdDesc(PROCESO, EMPRESA))
                .thenReturn(List.of(manual(EstadoManual.DISPONIBLE)));

        assertThat(servicio.listar(EMPRESA, PROCESO)).extracting(ManualResponse::id).containsExactly(5L);
        assertThatThrownBy(() -> servicio.obtener(EMPRESA, 9L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.listar(EMPRESA, 99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    private String leer(String clave) throws IOException {
        try (InputStream contenido = almacen.abrir(clave)) {
            return new String(contenido.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Transacciones de mentira que llevan la cuenta: cuantas hay abiertas y si cada una era de solo lectura. */
    private static final class Transacciones implements PlatformTransactionManager {

        private final List<Boolean> soloLectura = new ArrayList<>();
        private int abiertas;
        private int deshechas;

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
            deshechas++;
        }
    }

    private static DocumentoResponse documento(String nombre, EstadoDocumento estado) {
        return new DocumentoResponse(1L, PROCESO, null, nombre, 2, estado, null, true, "application/pdf", 10, 0L,
                null);
    }
}
