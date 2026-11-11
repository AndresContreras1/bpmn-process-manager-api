package com.facimus.procesos.modelado.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.PrecondicionFallidaException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.api.EtiquetasHttp;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.InformeImportacionResponse;
import com.facimus.procesos.modelado.dto.response.RevisionBorradorResponse;
import com.facimus.procesos.modelado.model.MotivoDeRevision;
import com.facimus.procesos.modelado.model.RevisionBorrador;
import com.facimus.procesos.modelado.repository.RevisionBorradorRepository;
import com.facimus.procesos.modelado.service.GuardadoDelModeloService;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;
import com.facimus.procesos.modelado.service.ModeloBpmnService;

/**
 * PR 40: la huella es la del archivo que el proceso exporta, no la de la version: esa nombra filas, y restaurar crea
 * filas nuevas. El archivo solo nombra bpmn_id, asi que restaurar una revision vuelve a dar su huella.
 */
@Service
@Transactional(readOnly = true)
public class GuardadoDelModeloServiceImpl implements GuardadoDelModeloService {

    private static final int CAMBIOS_EN_EL_AVISO = 10;

    private final ImportacionBpmnService importacionBpmnService;
    private final ModeloBpmnService modeloBpmnService;
    private final ProcesoRepository procesoRepository;
    private final RevisionBorradorRepository revisionRepository;
    private final HistorialCambioService historialCambioService;
    private final Duration intervalo;
    private final int maximo;

    public GuardadoDelModeloServiceImpl(ImportacionBpmnService importacionBpmnService,
            ModeloBpmnService modeloBpmnService, ProcesoRepository procesoRepository,
            RevisionBorradorRepository revisionRepository, HistorialCambioService historialCambioService,
            @Value("${modelado.revisiones.intervalo:PT5M}") Duration intervalo,
            @Value("${modelado.revisiones.maximo:100}") int maximo) {
        this.importacionBpmnService = importacionBpmnService;
        this.modeloBpmnService = modeloBpmnService;
        this.procesoRepository = procesoRepository;
        this.revisionRepository = revisionRepository;
        this.historialCambioService = historialCambioService;
        this.intervalo = intervalo;
        this.maximo = maximo;
    }

    @Override
    @Transactional
    public ImportacionResponse guardar(Long empresaId, Long usuarioId, RolAcceso rol, Long procesoId,
            InputStream archivo, String ifMatch) {
        Proceso proceso = bloquear(empresaId, procesoId);
        byte[] bytes = leer(archivo);
        String actual = huella(xmlDe(empresaId, procesoId));
        if (ifMatch != null && !coincide(ifMatch, actual)) {
            throw new PrecondicionFallidaException("El borrador cambió desde que lo leíste.", Map.of("huellaActual",
                    actual, "ultimosCambios", ultimosCambios(empresaId, procesoId)));
        }
        if (Huella.de(bytes).equals(actual)) {
            return sinCambios(proceso, actual);
        }
        ImportacionResponse respuesta = importacionBpmnService.autoguardar(empresaId, usuarioId, rol, procesoId,
                new ByteArrayInputStream(bytes));
        String xml = xmlDe(empresaId, procesoId);
        if (respuesta.creados() + respuesta.editados() + respuesta.dadosDeBaja() > 0 && toca(empresaId, procesoId)) {
            guardarRevision(proceso, usuarioId, MotivoDeRevision.AUTOGUARDADO, xml);
        }
        return respuesta.conHuella(huella(xml));
    }

    @Override
    @Transactional
    public RevisionBorradorResponse puntoDeGuardado(Long empresaId, Long usuarioId, Long procesoId,
            MotivoDeRevision motivo) {
        Proceso proceso = bloquear(empresaId, procesoId);
        String xml = xmlDe(empresaId, procesoId);
        List<RevisionBorradorResponse> ultimas = ultima(empresaId, procesoId);
        if (!ultimas.isEmpty() && ultimas.getFirst().huella().equals(huella(xml))) {
            return ultimas.getFirst();
        }
        RevisionBorrador revision = guardarRevision(proceso, usuarioId, motivo, xml);
        return new RevisionBorradorResponse(revision.getId(), revision.getMotivo(), revision.getHuella(),
                revision.getUsuarioId(), revision.getFecha());
    }

    @Override
    public List<RevisionBorradorResponse> revisiones(Long empresaId, Long procesoId) {
        exigirProceso(empresaId, procesoId);
        return revisionRepository.resumenes(procesoId, empresaId);
    }

    @Override
    public String modelo(Long empresaId, Long procesoId, Long revisionId) {
        exigirProceso(empresaId, procesoId);
        return descomprimir(revision(empresaId, procesoId, revisionId).getXml());
    }

    @Override
    @Transactional
    public ImportacionResponse restaurar(Long empresaId, Long usuarioId, RolAcceso rol, Long procesoId,
            Long revisionId) {
        Proceso proceso = bloquear(empresaId, procesoId);
        RevisionBorrador revision = revision(empresaId, procesoId, revisionId);
        String actual = xmlDe(empresaId, procesoId);
        if (huella(actual).equals(revision.getHuella())) {
            return sinCambios(proceso, revision.getHuella());
        }
        guardarRevision(proceso, usuarioId, MotivoDeRevision.ANTES_DE_RESTAURAR, actual);
        ImportacionResponse respuesta = importacionBpmnService.reemplazar(empresaId, usuarioId, rol, procesoId,
                new ByteArrayInputStream(descomprimir(revision.getXml()).getBytes(StandardCharsets.UTF_8)));
        return respuesta.conHuella(huella(xmlDe(empresaId, procesoId)));
    }

    private RevisionBorrador guardarRevision(Proceso proceso, Long usuarioId, MotivoDeRevision motivo, String xml) {
        Long empresaId = proceso.getEmpresa().getId();
        RevisionBorrador revision = revisionRepository.save(RevisionBorrador.builder()
                .empresa(proceso.getEmpresa())
                .procesoId(proceso.getId())
                .usuarioId(usuarioId)
                .motivo(motivo)
                .huella(huella(xml))
                .xml(comprimir(xml))
                .fecha(LocalDateTime.now())
                .build());
        List<Long> sobra = revisionRepository.idsDesde(proceso.getId(), empresaId, PageRequest.of(maximo, 1));
        if (!sobra.isEmpty()) {
            revisionRepository.borrarHasta(proceso.getId(), empresaId, sobra.getFirst());
        }
        return revision;
    }

    /** Toca otra si no hay ninguna, o si la ultima ya tiene sus minutos. */
    private boolean toca(Long empresaId, Long procesoId) {
        List<RevisionBorradorResponse> ultimas = ultima(empresaId, procesoId);
        return ultimas.isEmpty() || !ultimas.getFirst().fecha().plus(intervalo).isAfter(LocalDateTime.now());
    }

    private List<RevisionBorradorResponse> ultima(Long empresaId, Long procesoId) {
        return revisionRepository.ultimas(procesoId, empresaId, PageRequest.of(0, 1));
    }

    private static ImportacionResponse sinCambios(Proceso proceso, String huella) {
        return new ImportacionResponse(proceso.getId(), proceso.getNombre(), 0, 0, 0,
                new InformeImportacionResponse(Map.of(), List.of(), List.of(), List.of(), List.of()), huella);
    }

    private List<HistorialCambioResponse> ultimosCambios(Long empresaId, Long procesoId) {
        return historialCambioService.listarPorProceso(empresaId, procesoId).stream().limit(CAMBIOS_EN_EL_AVISO)
                .toList();
    }

    private static boolean coincide(String ifMatch, String actual) {
        String valor = EtiquetasHttp.valor(ifMatch);
        return "*".equals(valor) || valor.equals(actual);
    }

    private Proceso bloquear(Long empresaId, Long procesoId) {
        return procesoRepository.bloquear(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private void exigirProceso(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
    }

    private RevisionBorrador revision(Long empresaId, Long procesoId, Long revisionId) {
        return revisionRepository.findByIdAndProcesoIdAndEmpresaId(revisionId, procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Revisión no encontrada."));
    }

    private String xmlDe(Long empresaId, Long procesoId) {
        return modeloBpmnService.exportar(empresaId, procesoId).xml();
    }

    private static String huella(String xml) {
        return Huella.de(xml.getBytes(StandardCharsets.UTF_8));
    }

    /** Lo que llega, hasta un byte mas del tope: asi la lectura del archivo dice que se paso. */
    private static byte[] leer(InputStream archivo) {
        try {
            return archivo.readNBytes((int) LimitesXml.BPMN.bytes() + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] comprimir(String xml) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(xml.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String descomprimir(byte[] comprimido) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido))) {
            return new String(gzip.readNBytes((int) LimitesXml.BPMN.bytes() + 1), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
