package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.ExportacionResponse;
import com.facimus.procesos.gestion.model.EstadoExportacion;
import com.facimus.procesos.gestion.model.Exportacion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ExportacionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ExportacionService.Descarga;
import com.facimus.procesos.gestion.service.ParteDeLaExportacion;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 66: pedir, armar, bajar y limpiar una exportacion, con el paquete, los enlaces y los archivos de paso de verdad,
 * y lo demas de mentira.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportacionServiceImplTest {

    private static final Long EMPRESA = 1L;
    private static final Long ANA = 7L;
    private static final Long EXPORTACION = 4L;
    private static final Instant AHORA = Instant.parse("2026-11-12T10:00:00.123456Z");
    private static final LocalDateTime AHORA_LOCAL = LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final String CLAVE = "empresas/1/exportaciones/4/organizacion";
    private static final String CARPETA = "empresas/1/exportaciones/4/";

    @Mock
    private ExportacionRepository exportacionRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ObjectProvider<ParteDeLaExportacion> partes;
    @Mock
    private ColaDeTrabajos cola;
    @Mock
    private Auditoria auditoria;
    @Mock
    private Almacen almacen;

    @TempDir
    Path paso;

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final EnlacesFirmados enlaces = new EnlacesFirmados("secreto-de-las-pruebas-de-exportaciones", reloj);
    private final JsonMapper json = JsonMapper.builder().build();
    private final Transacciones transacciones = new Transacciones();
    private final List<Alcance> alcancesDeLasPartes = new ArrayList<>();
    private final List<Integer> transaccionesDeLasPartes = new ArrayList<>();
    private final Empresa acme = Empresa.builder().id(EMPRESA).nombre("Acme").build();
    private ExportacionServiceImpl servicio;
    private byte[] guardado;

    @BeforeEach
    void preparar() {
        servicio = new ExportacionServiceImpl(exportacionRepository, empresaRepository, usuarioRepository, partes,
                cola, auditoria, almacen, enlaces, new ArchivosTemporales(paso), json, reloj, transacciones,
                Duration.ofHours(24), Duration.ofHours(6));
        when(exportacionRepository.save(any(Exportacion.class))).thenAnswer(llamada -> {
            Exportacion exportacion = llamada.getArgument(0);
            exportacion.setId(EXPORTACION);
            return exportacion;
        });
        doAnswer(llamada -> {
            guardado = Files.readAllBytes(llamada.getArgument(1));
            return null;
        }).when(almacen).guardar(anyString(), any(Path.class), anyString());
        when(usuarioRepository.findByIdAndEmpresaId(ANA, EMPRESA)).thenReturn(Optional.of(
                Usuario.builder().id(ANA).nombre("Ana").email("ana@acme.test").build()));
        when(partes.orderedStream()).thenAnswer(llamada -> Stream.of(parte("organizacion.json"),
                parte("casos/casos.json")));
    }

    /** Una parte que escribe un archivo, y anota con que tienda y en cuantas transacciones corrio. */
    private ParteDeLaExportacion parte(String ruta) {
        return (empresaId, paquete) -> {
            alcancesDeLasPartes.add(ContextoDeTienda.actual().orElse(null));
            transaccionesDeLasPartes.add(transacciones.abiertas);
            paquete.json(ruta, Map.of("de", empresaId));
        };
    }

    private Exportacion exportacion(EstadoExportacion estado) {
        return Exportacion.builder().id(EXPORTACION).empresa(acme).estado(estado).pedidaPor(ANA)
                .pedidaEn(LocalDateTime.of(2026, 11, 12, 9, 59)).build();
    }

    private Exportacion lista(LocalDateTime vence) {
        Exportacion exportacion = exportacion(EstadoExportacion.LISTA);
        exportacion.setClave(CLAVE);
        exportacion.setTamano(321L);
        exportacion.setSha256("f".repeat(64));
        exportacion.setArchivos(2);
        exportacion.setListaEn(vence.minusHours(24));
        exportacion.setVenceEn(vence);
        return exportacion;
    }

    @Test
    @DisplayName("pedirla bloquea la tienda, deja una PENDIENTE de quien la pide, la encola y lo anota")
    void pedir() {
        when(empresaRepository.bloquear(EMPRESA)).thenReturn(Optional.of(acme));

        ExportacionResponse respuesta = servicio.pedir(EMPRESA, ANA);

        ArgumentCaptor<Exportacion> guardada = ArgumentCaptor.forClass(Exportacion.class);
        verify(exportacionRepository).save(guardada.capture());
        assertThat(guardada.getValue().getEmpresa()).isSameAs(acme);
        assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoExportacion.PENDIENTE);
        assertThat(guardada.getValue().getPedidaPor()).isEqualTo(ANA);
        assertThat(guardada.getValue().getPedidaEn()).isEqualTo(AHORA_LOCAL);
        verify(cola).encolar(EMPRESA, "exportaciones.generar", "4");
        verify(auditoria).registrar(EMPRESA, ANA, "EXPORTACION_PEDIDA", "EXPORTACION", EXPORTACION, Map.of());
        assertThat(respuesta).isEqualTo(new ExportacionResponse(EXPORTACION, EstadoExportacion.PENDIENTE, ANA,
                AHORA_LOCAL, null, null, null, null, null, null));
    }

    @Test
    @DisplayName("con una en curso responde 409 y no deja otra; sin la tienda, 404")
    void unaALaVez() {
        when(empresaRepository.bloquear(EMPRESA)).thenReturn(Optional.of(acme));
        when(exportacionRepository.existsByEmpresaIdAndEstado(EMPRESA, EstadoExportacion.PENDIENTE)).thenReturn(true);

        assertThatThrownBy(() -> servicio.pedir(EMPRESA, ANA)).isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("en curso");
        assertThatThrownBy(() -> servicio.pedir(2L, ANA)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(exportacionRepository, never()).save(any());
        verifyNoInteractions(cola, auditoria);
    }

    @Test
    @DisplayName("se listan las de la tienda, y una de otra tienda no existe")
    void listarYObtener() {
        when(exportacionRepository.findAllByEmpresaIdOrderByIdDesc(EMPRESA))
                .thenReturn(List.of(lista(AHORA_LOCAL.plusHours(1))));
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA))
                .thenReturn(Optional.of(lista(AHORA_LOCAL.plusHours(1))));

        assertThat(servicio.listar(EMPRESA)).singleElement()
                .satisfies(una -> assertThat(una.sha256()).isEqualTo("f".repeat(64)));
        assertThat(servicio.obtener(EMPRESA, EXPORTACION)).isEqualTo(new ExportacionResponse(EXPORTACION,
                EstadoExportacion.LISTA, ANA, LocalDateTime.of(2026, 11, 12, 9, 59), AHORA_LOCAL.minusHours(23),
                AHORA_LOCAL.plusHours(1), 321L, 2, "f".repeat(64), null));
        assertThatThrownBy(() -> servicio.obtener(2L, EXPORTACION)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("armarla corre cada parte con la tienda fijada y sin transaccion abierta, deja el ZIP con su "
            + "manifiesto en el almacen, la anota LISTA por 24 horas con su huella y manda el correo")
    void generar() throws IOException {
        Exportacion pendiente = exportacion(EstadoExportacion.PENDIENTE);
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(pendiente));
        Alcance antes = ContextoDeTienda.fijar(Alcance.SISTEMA);
        try {
            servicio.generar(EMPRESA, EXPORTACION);
            assertThat(ContextoDeTienda.actual()).contains(Alcance.SISTEMA);
        } finally {
            ContextoDeTienda.restaurar(antes);
        }

        assertThat(alcancesDeLasPartes).containsExactly(Alcance.deLaTienda(EMPRESA), Alcance.deLaTienda(EMPRESA));
        assertThat(transaccionesDeLasPartes).containsExactly(0, 0);
        verify(almacen).guardar(eq(CLAVE), any(Path.class), eq("application/zip"));
        Map<String, byte[]> archivos = PaqueteZipTest.abrir(guardado);
        assertThat(archivos.keySet()).containsExactly("organizacion.json", "casos/casos.json", "manifest.json");
        JsonNode manifiesto = json.readTree(archivos.get("manifest.json"));
        assertThat(manifiesto.get("formato").asInt()).isEqualTo(1);
        assertThat(manifiesto.get("exportacion").asLong()).isEqualTo(EXPORTACION);
        assertThat(manifiesto.get("tienda").get("id").asLong()).isEqualTo(EMPRESA);
        assertThat(manifiesto.get("tienda").get("nombre").asString()).isEqualTo("Acme");
        assertThat(manifiesto.get("pedidaPor").get("id").asLong()).isEqualTo(ANA);
        assertThat(manifiesto.get("pedidaPor").get("nombre").asString()).isEqualTo("Ana");
        assertThat(manifiesto.get("pedidaPor").get("email").asString()).isEqualTo("ana@acme.test");
        assertThat(manifiesto.get("pedidaEn").asString()).isEqualTo("2026-11-12T09:59");
        assertThat(manifiesto.get("generadaEn").asString()).isEqualTo(AHORA_LOCAL.toString());
        assertThat(manifiesto.get("zonaHoraria").asString()).isEqualTo("UTC");
        assertThat(manifiesto.get("archivos")).hasSize(2);

        LocalDateTime lista = LocalDateTime.of(2026, 11, 12, 10, 0);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoExportacion.LISTA);
        assertThat(pendiente.getClave()).isEqualTo(CLAVE);
        assertThat(pendiente.getSha256()).isEqualTo(Huella.de(guardado));
        assertThat(pendiente.getTamano()).isEqualTo(guardado.length);
        assertThat(pendiente.getArchivos()).isEqualTo(2);
        assertThat(pendiente.getListaEn()).isEqualTo(lista);
        assertThat(pendiente.getVenceEn()).isEqualTo(lista.plusHours(24));
        assertThat(pendiente.getMotivo()).isNull();
        verify(cola).encolar(EMPRESA, "exportaciones.correo", "4");
        verify(auditoria).registrar(EMPRESA, null, "EXPORTACION_LISTA", "EXPORTACION", EXPORTACION,
                Map.of("sha256", Huella.de(guardado), "tamano", (long) guardado.length, "archivos", 2));
        assertThat(transacciones.soloLectura).containsExactly(true, false);
        try (Stream<Path> quedan = Files.list(paso)) {
            assertThat(quedan).as("el archivo de paso se borra").isEmpty();
        }
    }

    @Test
    @DisplayName("una que ya no esta pendiente no se arma otra vez")
    void yaArmada() {
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA))
                .thenReturn(Optional.of(lista(AHORA_LOCAL.plusHours(1))));

        servicio.generar(EMPRESA, EXPORTACION);

        verifyNoInteractions(partes, almacen, cola, auditoria);
    }

    @Test
    @DisplayName("si una parte falla queda FALLIDA con el motivo, sin nada en el almacen, sin correo y sin el "
            + "archivo de paso")
    void fallida() throws IOException {
        Exportacion pendiente = exportacion(EstadoExportacion.PENDIENTE);
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(pendiente));
        when(partes.orderedStream()).thenAnswer(llamada -> Stream.<ParteDeLaExportacion>of((empresaId, paquete) -> {
            paquete.texto("uno.txt", "x");
            throw new IllegalStateException("No se pudo leer el documento 12 del almacén.");
        }));

        servicio.generar(EMPRESA, EXPORTACION);

        assertThat(pendiente.getEstado()).isEqualTo(EstadoExportacion.FALLIDA);
        assertThat(pendiente.getMotivo()).isEqualTo("No se pudo leer el documento 12 del almacén.");
        assertThat(pendiente.getClave()).isNull();
        verify(almacen, never()).guardar(anyString(), any(), anyString());
        verify(cola, never()).encolar(any(), anyString(), anyString());
        verify(auditoria).registrar(EMPRESA, null, "EXPORTACION_FALLIDA", "EXPORTACION", EXPORTACION,
                Map.of("motivo", "No se pudo leer el documento 12 del almacén."));
        assertThat(ContextoDeTienda.actual()).isEmpty();
        try (Stream<Path> quedan = Files.list(paso)) {
            assertThat(quedan).isEmpty();
        }
    }

    @Test
    @DisplayName("el motivo se corta en 300 caracteres, y uno sin mensaje dice que fallo")
    void motivos() {
        Exportacion larga = exportacion(EstadoExportacion.PENDIENTE);
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(larga));
        when(partes.orderedStream()).thenAnswer(llamada -> Stream.<ParteDeLaExportacion>of((empresaId, paquete) -> {
            throw new IllegalStateException("x".repeat(301));
        }));
        servicio.generar(EMPRESA, EXPORTACION);
        assertThat(larga.getMotivo()).hasSize(300);

        Exportacion muda = exportacion(EstadoExportacion.PENDIENTE);
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(muda));
        when(partes.orderedStream()).thenAnswer(llamada -> Stream.<ParteDeLaExportacion>of((empresaId, paquete) -> {
            throw new UnsupportedOperationException();
        }));
        servicio.generar(EMPRESA, EXPORTACION);
        assertThat(muda.getMotivo()).isEqualTo("UnsupportedOperationException");

        Exportacion justa = exportacion(EstadoExportacion.PENDIENTE);
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(justa));
        when(partes.orderedStream()).thenAnswer(llamada -> Stream.<ParteDeLaExportacion>of((empresaId, paquete) -> {
            throw new IllegalStateException("y".repeat(300));
        }));
        servicio.generar(EMPRESA, EXPORTACION);
        assertThat(justa.getMotivo()).isEqualTo("y".repeat(300));
    }

    @Test
    @DisplayName("si mientras se armaba la limpieza la dio por atascada, el ZIP que se subio se borra y nada cambia")
    void abandonadaMientrasSeArmaba() {
        Exportacion pendiente = exportacion(EstadoExportacion.PENDIENTE);
        Exportacion abandonada = exportacion(EstadoExportacion.FALLIDA);
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(pendiente),
                Optional.of(abandonada));

        servicio.generar(EMPRESA, EXPORTACION);

        verify(almacen).borrarPrefijo(CARPETA);
        assertThat(abandonada.getEstado()).isEqualTo(EstadoExportacion.FALLIDA);
        assertThat(abandonada.getClave()).isNull();
        verifyNoInteractions(cola, auditoria);
    }

    @Test
    @DisplayName("si fallo y ademas ya no esta pendiente, no hay nada que borrar ni que anotar")
    void fallidaYAbandonada() {
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(
                Optional.of(exportacion(EstadoExportacion.PENDIENTE)), Optional.empty());
        when(partes.orderedStream()).thenAnswer(llamada -> Stream.<ParteDeLaExportacion>of((empresaId, paquete) -> {
            throw new IllegalStateException("roto");
        }));

        servicio.generar(EMPRESA, EXPORTACION);

        verify(almacen, never()).borrarPrefijo(anyString());
        verifyNoInteractions(cola, auditoria);
    }

    @Test
    @DisplayName("con el enlace firmado se baja el ZIP, con la tienda del enlace, y la descarga queda anotada")
    void descargar() {
        Exportacion lista = lista(AHORA_LOCAL.plusHours(1));
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenAnswer(llamada -> {
            assertThat(ContextoDeTienda.actual()).contains(Alcance.deLaTienda(EMPRESA));
            return Optional.of(lista);
        });
        InputStream zip = new ByteArrayInputStream("zip".getBytes(StandardCharsets.UTF_8));
        when(almacen.abrir(CLAVE)).thenReturn(zip);
        Instant vence = lista.getVenceEn().toInstant(ZoneOffset.UTC);

        Descarga descarga = servicio.descargar(EMPRESA, EXPORTACION, vence.getEpochSecond(),
                enlaces.firmar("exportacion:1:4", vence));

        assertThat(descarga.nombre()).as("el dia en que quedo lista").isEqualTo("exportacion-2026-11-11-4.zip");
        assertThat(descarga.tamano()).isEqualTo(321L);
        assertThat(descarga.contenido()).isSameAs(zip);
        verify(auditoria).registrar(EMPRESA, null, "EXPORTACION_DESCARGADA", "EXPORTACION", EXPORTACION, Map.of());
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("un enlace cambiado, de otra tienda o vencido no existe, y no se mira la base")
    void enlacesMalos() {
        Instant vence = AHORA.plusSeconds(3600);
        long segundos = vence.getEpochSecond();
        String firma = enlaces.firmar("exportacion:1:4", vence);
        String vieja = enlaces.firmar("exportacion:1:4", AHORA.minusSeconds(1));
        long vencida = AHORA.minusSeconds(1).getEpochSecond();

        assertThatThrownBy(() -> servicio.descargar(EMPRESA, EXPORTACION, segundos, firma + "x"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.descargar(2L, EXPORTACION, segundos, firma))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.descargar(EMPRESA, 5L, segundos, firma))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.descargar(EMPRESA, EXPORTACION, vencida, vieja))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(exportacionRepository, almacen, auditoria);
    }

    @Test
    @DisplayName("con un enlace bueno, una que no esta LISTA o que ya vencio tampoco se baja")
    void noVigente() {
        Instant vence = AHORA.plusSeconds(3600);
        String firma = enlaces.firmar("exportacion:1:4", vence);
        long segundos = vence.getEpochSecond();
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(
                Optional.of(exportacion(EstadoExportacion.VENCIDA)), Optional.of(lista(AHORA_LOCAL)),
                Optional.empty());

        for (int intento = 0; intento < 3; intento++) {
            assertThatThrownBy(() -> servicio.descargar(EMPRESA, EXPORTACION, segundos, firma))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
        verifyNoInteractions(almacen, auditoria);
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("una lista cuyo ZIP ya no esta en el almacen no existe, y no se anota como bajada")
    void sinElArchivo() {
        Exportacion lista = lista(AHORA_LOCAL.plusHours(1));
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(lista));
        when(almacen.abrir(CLAVE)).thenThrow(new ArchivoNoEncontradoException(CLAVE));
        Instant vence = lista.getVenceEn().toInstant(ZoneOffset.UTC);
        long segundos = vence.getEpochSecond();
        String firma = enlaces.firmar("exportacion:1:4", vence);

        assertThatThrownBy(() -> servicio.descargar(EMPRESA, EXPORTACION, segundos, firma))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
        assertThat(ContextoDeTienda.actual()).isEmpty();
    }

    @Test
    @DisplayName("la limpieza borra del almacen las vencidas y las anota, da por fallidas las atascadas, y una que "
            + "falla no frena a las demas")
    void limpiar() {
        Exportacion vencida = lista(AHORA_LOCAL.minusMinutes(1));
        Exportacion otraVencida = lista(AHORA_LOCAL.minusMinutes(2));
        otraVencida.setId(5L);
        Exportacion atascada = exportacion(EstadoExportacion.PENDIENTE);
        atascada.setId(6L);
        when(exportacionRepository.findAllByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoExportacion.LISTA, AHORA_LOCAL))
                .thenReturn(List.of(vencida, otraVencida));
        when(exportacionRepository.findAllByEstadoAndPedidaEnBeforeOrderByIdAsc(EstadoExportacion.PENDIENTE,
                AHORA_LOCAL.minusHours(6))).thenReturn(List.of(atascada));
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(vencida));
        when(exportacionRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(atascada));
        doThrow(new IllegalStateException("almacen caido")).when(almacen).borrarPrefijo("empresas/1/exportaciones/5/");

        assertThat(servicio.limpiar()).isEqualTo(2);

        verify(almacen).borrarPrefijo(CARPETA);
        assertThat(vencida.getEstado()).isEqualTo(EstadoExportacion.VENCIDA);
        assertThat(vencida.getClave()).isNull();
        assertThat(vencida.getSha256()).as("la huella queda como constancia").isEqualTo("f".repeat(64));
        assertThat(otraVencida.getEstado()).isEqualTo(EstadoExportacion.LISTA);
        verify(auditoria).registrar(EMPRESA, null, "EXPORTACION_VENCIDA", "EXPORTACION", EXPORTACION, Map.of());
        assertThat(atascada.getEstado()).isEqualTo(EstadoExportacion.FALLIDA);
        assertThat(atascada.getMotivo()).isEqualTo("La cola no la terminó a tiempo: pide otra.");
        verify(auditoria).registrar(EMPRESA, null, "EXPORTACION_FALLIDA", "EXPORTACION", 6L,
                Map.of("motivo", "La cola no la terminó a tiempo: pide otra."));
    }

    @Test
    @DisplayName("la limpieza no toca una que cambio mientras tanto: ni una vencida que ya no esta LISTA ni una "
            + "atascada que termino")
    void limpiarLoQueCambio() {
        Exportacion yaVencida = exportacion(EstadoExportacion.VENCIDA);
        Exportacion terminada = lista(AHORA_LOCAL.plusHours(24));
        terminada.setId(6L);
        when(exportacionRepository.findAllByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoExportacion.LISTA, AHORA_LOCAL))
                .thenReturn(List.of(lista(AHORA_LOCAL.minusMinutes(1))));
        when(exportacionRepository.findAllByEstadoAndPedidaEnBeforeOrderByIdAsc(EstadoExportacion.PENDIENTE,
                AHORA_LOCAL.minusHours(6))).thenReturn(List.of(terminada));
        when(exportacionRepository.findByIdAndEmpresaId(EXPORTACION, EMPRESA)).thenReturn(Optional.of(yaVencida));
        when(exportacionRepository.findByIdAndEmpresaId(6L, EMPRESA)).thenReturn(Optional.of(terminada));

        servicio.limpiar();

        assertThat(yaVencida.getEstado()).isEqualTo(EstadoExportacion.VENCIDA);
        assertThat(terminada.getEstado()).isEqualTo(EstadoExportacion.LISTA);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("la ruta firmada lleva la tienda, el vencimiento y una firma que vale hasta entonces")
    void ruta() {
        LocalDateTime vence = LocalDateTime.of(2026, 11, 13, 10, 0);
        long segundos = vence.toInstant(ZoneOffset.UTC).getEpochSecond();

        String ruta = ExportacionServiceImpl.ruta(enlaces, EMPRESA, EXPORTACION, vence);

        assertThat(ruta).startsWith("/api/v1/descargas/exportaciones/4?empresa=1&vence=" + segundos + "&firma=");
        String firma = ruta.substring(ruta.indexOf("&firma=") + "&firma=".length());
        assertThat(enlaces.vale("exportacion:1:4", Instant.ofEpochSecond(segundos), firma)).isTrue();
        assertThat(enlaces.vale("exportacion:2:4", Instant.ofEpochSecond(segundos), firma)).isFalse();
    }

    /** Cuenta las transacciones abiertas y si son de solo lectura. */
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
