package com.facimus.procesos.modelado.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import com.facimus.procesos.common.ArchivoDemasiadoGrandeException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.TipoNoPermitidoException;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.antivirus.Antivirus;
import com.facimus.procesos.common.antivirus.ResultadoDelAntivirus;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.archivos.ArchivosTemporales.Temporal;
import com.facimus.procesos.common.archivos.DetectorDeTipo;
import com.facimus.procesos.common.archivos.InspeccionDeZip;
import com.facimus.procesos.common.archivos.NombreDeArchivo;
import com.facimus.procesos.common.archivos.TipoDeArchivo;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.security.ContextoDeTienda.Alcance;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.DocumentoDetalleResponse;
import com.facimus.procesos.modelado.dto.response.DocumentoResponse;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.dto.response.VersionDocumentoResponse;
import com.facimus.procesos.modelado.model.Documento;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.VersionDocumento;
import com.facimus.procesos.modelado.repository.DocumentoRepository;
import com.facimus.procesos.modelado.repository.VersionDocumentoRepository;
import com.facimus.procesos.modelado.service.Descarga;
import com.facimus.procesos.modelado.service.DocumentoService;

/**
 * PR 61: los documentos. Subir es leer el cuerpo a un archivo temporal sin pasar el tope, sacando su huella al
 * mismo tiempo; mirar su tipo por el contenido; comprobar la cuota de la tienda; guardarlo en el almacen con una
 * clave que no dice nada del archivo; y dejar en la cola que lo revisen. Hasta que la cola lo da por bueno, nadie lo
 * puede bajar. Bajar es con un enlace firmado de unos minutos, que la API sirve siempre como archivo adjunto.
 */
@Service
public class DocumentoServiceImpl implements DocumentoService {

    /** El tipo de trabajo de la cola que revisa cada version. */
    public static final String TRABAJO_DE_REVISION = "documentos.revisar";

    private static final String NO_ENCONTRADO = "Documento no encontrado.";
    private static final String VERSION_NO_ENCONTRADA = "Versión no encontrada.";
    private static final String BINARIO = "application/octet-stream";
    private static final String RECURSO = "DOCUMENTO";
    private static final String SUBIDO = "DOCUMENTO_SUBIDO";
    private static final String VERSION = "version";
    private static final String TAMANO = "tamano";
    private static final String TIPO = "tipo";
    private static final String COMILLA = "Documento \"";

    private final DocumentoRepository documentoRepository;
    private final VersionDocumentoRepository versionRepository;
    private final ProcesoRepository procesoRepository;
    private final ElementosDelProceso elementos;
    private final HistorialCambioService historial;
    private final Auditoria auditoria;
    private final ColaDeTrabajos cola;
    private final Almacen almacen;
    private final Antivirus antivirus;
    private final DetectorDeTipo detector;
    private final InspeccionDeZip inspeccionDeZip;
    private final EnlacesFirmados enlaces;
    private final ArchivosTemporales temporales;
    private final Clock reloj;
    private final Limites limites;

    /** Lo que se acepta: tamano por archivo, espacio por tienda, si se espera al antivirus y vida de un enlace. */
    public record Limites(long tamanoMaximo, long cuotaPorTienda, boolean exigirAntivirus, Duration vidaDelEnlace) {
    }

    @Autowired
    @SuppressWarnings("java:S107")
    public DocumentoServiceImpl(DocumentoRepository documentoRepository, VersionDocumentoRepository versionRepository,
            ProcesoRepository procesoRepository, ElementosDelProceso elementos, HistorialCambioService historial,
            Auditoria auditoria, ColaDeTrabajos cola, Almacen almacen, Antivirus antivirus, DetectorDeTipo detector,
            InspeccionDeZip inspeccionDeZip, EnlacesFirmados enlaces, ArchivosTemporales temporales, Clock reloj,
            @Value("${documentos.tamano-maximo:25MB}") DataSize tamanoMaximo,
            @Value("${documentos.cuota-por-tienda:1GB}") DataSize cuota,
            @Value("${documentos.exigir-antivirus:false}") boolean exigirAntivirus,
            @Value("${documentos.enlace.vida:5m}") Duration vidaDelEnlace) {
        this(documentoRepository, versionRepository, procesoRepository, elementos, historial, auditoria, cola, almacen,
                antivirus, detector, inspeccionDeZip, enlaces, temporales, reloj,
                new Limites(tamanoMaximo.toBytes(), cuota.toBytes(), exigirAntivirus, vidaDelEnlace));
    }

    @SuppressWarnings("java:S107")
    DocumentoServiceImpl(DocumentoRepository documentoRepository, VersionDocumentoRepository versionRepository,
            ProcesoRepository procesoRepository, ElementosDelProceso elementos, HistorialCambioService historial,
            Auditoria auditoria, ColaDeTrabajos cola, Almacen almacen, Antivirus antivirus, DetectorDeTipo detector,
            InspeccionDeZip inspeccionDeZip, EnlacesFirmados enlaces, ArchivosTemporales temporales, Clock reloj,
            Limites limites) {
        this.documentoRepository = documentoRepository;
        this.versionRepository = versionRepository;
        this.procesoRepository = procesoRepository;
        this.elementos = elementos;
        this.historial = historial;
        this.auditoria = auditoria;
        this.cola = cola;
        this.almacen = almacen;
        this.antivirus = antivirus;
        this.detector = detector;
        this.inspeccionDeZip = inspeccionDeZip;
        this.enlaces = enlaces;
        this.temporales = temporales;
        this.reloj = reloj;
        this.limites = limites;
    }

    @Override
    @Transactional
    public DocumentoResponse subir(Long empresaId, Long usuarioId, Long procesoId, String bpmnId, String nombre,
            InputStream contenido) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        String elemento = bpmnId == null || bpmnId.isBlank() ? null : bpmnId.strip();
        if (elemento != null && !elementos.existe(empresaId, procesoId, elemento)) {
            throw new RecursoNoEncontradoException("El proceso no tiene el elemento " + elemento + ".");
        }
        return conArchivo(empresaId, nombre, contenido, recibido -> {
            Documento documento = documentoRepository.save(Documento.builder().empresa(proceso.getEmpresa())
                    .proceso(proceso).bpmnId(elemento).nombre(recibido.nombre(nombre)).versionActual(1).build());
            VersionDocumento version = guardarVersion(documento, 1, usuarioId, nombre, recibido);
            historial.registrar(empresaId, usuarioId, proceso, COMILLA + documento.getNombre() + "\" agregado.");
            auditoria.registrar(empresaId, usuarioId, SUBIDO, RECURSO, documento.getId(),
                    Map.of(VERSION, 1, TAMANO, version.getTamano(), TIPO, version.getTipo()));
            return respuesta(documento, version);
        });
    }

    @Override
    @Transactional
    public DocumentoResponse subirVersion(Long empresaId, Long usuarioId, Long documentoId, String nombre,
            InputStream contenido) {
        Documento documento = buscar(empresaId, documentoId);
        return conArchivo(empresaId, nombre, contenido, recibido -> {
            int numero = documento.getVersionActual() + 1;
            VersionDocumento version = guardarVersion(documento, numero, usuarioId, nombre, recibido);
            documento.setVersionActual(numero);
            documento.setNombre(version.getNombre());
            Documento guardado = documentoRepository.saveAndFlush(documento);
            historial.registrar(empresaId, usuarioId, documento.getProceso(),
                    COMILLA + guardado.getNombre() + "\": versión " + numero + ".");
            auditoria.registrar(empresaId, usuarioId, SUBIDO, RECURSO, documentoId,
                    Map.of(VERSION, numero, TAMANO, version.getTamano(), TIPO, version.getTipo()));
            return respuesta(guardado, version);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(Long empresaId, Long procesoId, String bpmnId) {
        procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        List<Documento> documentos = bpmnId == null || bpmnId.isBlank()
                ? documentoRepository.findAllByProcesoIdAndEmpresaIdOrderByNombreAscIdAsc(procesoId, empresaId)
                : documentoRepository.findAllByProcesoIdAndBpmnIdAndEmpresaIdOrderByNombreAscIdAsc(procesoId,
                        bpmnId.strip(), empresaId);
        if (documentos.isEmpty()) {
            return List.of();
        }
        Map<Long, VersionDocumento> ultimas = versionRepository
                .ultimas(empresaId, documentos.stream().map(Documento::getId).toList()).stream()
                .collect(Collectors.toMap(version -> version.getDocumento().getId(), Function.identity()));
        return documentos.stream().map(documento -> respuesta(documento, ultimas.get(documento.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentoDetalleResponse obtener(Long empresaId, Long documentoId) {
        Documento documento = buscar(empresaId, documentoId);
        List<VersionDocumento> versiones = versionRepository
                .findAllByDocumento_IdAndEmpresaIdOrderByNumeroDesc(documentoId, empresaId);
        return new DocumentoDetalleResponse(respuesta(documento, versiones.getFirst()),
                versiones.stream().map(DocumentoServiceImpl::respuesta).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EnlaceDeDescargaResponse enlace(Long empresaId, Long documentoId, Integer numero) {
        Documento documento = buscar(empresaId, documentoId);
        int cual = numero == null ? documento.getVersionActual() : numero;
        VersionDocumento version = versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(documentoId, cual,
                empresaId).orElseThrow(() -> new RecursoNoEncontradoException(VERSION_NO_ENCONTRADA));
        if (version.getEstado() == EstadoDocumento.PENDIENTE) {
            throw new ReglaNegocioException("El documento todavía se está revisando: inténtalo en un momento.");
        }
        if (version.getEstado() == EstadoDocumento.BLOQUEADO) {
            throw new ReglaNegocioException("Esta versión está retenida: " + version.getMotivo());
        }
        Instant vence = enlaces.vencimiento(limites.vidaDelEnlace());
        String firma = enlaces.firmar(recurso(empresaId, documentoId, cual), vence);
        String url = "/api/v1/descargas/documentos/" + documentoId + "/" + cual + "?empresa=" + empresaId + "&vence="
                + vence.getEpochSecond() + "&firma=" + firma;
        return new EnlaceDeDescargaResponse(url, vence);
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long documentoId) {
        Documento documento = buscar(empresaId, documentoId);
        documentoRepository.delete(documento);
        historial.registrar(empresaId, usuarioId, documento.getProceso(),
                COMILLA + documento.getNombre() + "\" eliminado.");
        auditoria.registrar(empresaId, usuarioId, "DOCUMENTO_ELIMINADO", RECURSO, documentoId, Map.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Descarga descargar(Long empresaId, Long documentoId, int numero, long vence, String firma) {
        if (!enlaces.vale(recurso(empresaId, documentoId, numero), Instant.ofEpochSecond(vence), firma)) {
            throw new RecursoNoEncontradoException("El enlace no existe o ya venció: pide otro.");
        }
        // Sin sesion, la tienda la dice el enlace, y solo despues de comprobar su firma
        Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(empresaId));
        try {
            Documento documento = buscar(empresaId, documentoId);
            VersionDocumento version = versionRepository.findByDocumento_IdAndNumeroAndEmpresaId(documento.getId(),
                    numero, empresaId).filter(encontrada -> encontrada.getEstado() == EstadoDocumento.DISPONIBLE)
                    .orElseThrow(() -> new RecursoNoEncontradoException(VERSION_NO_ENCONTRADA));
            String tipo = TipoDeArchivo.deMime(version.getTipo()).filter(TipoDeArchivo::seMuestra)
                    .map(TipoDeArchivo::mime).orElse(BINARIO);
            return new Descarga(version.getNombre(), tipo, version.getTamano(), almacen.abrir(version.getClave()));
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    @Override
    @Transactional
    public void revisar(Long empresaId, Long versionId) {
        Optional<VersionDocumento> pendiente = versionRepository.findByIdAndEmpresaId(versionId, empresaId)
                .filter(version -> version.getEstado() == EstadoDocumento.PENDIENTE);
        if (pendiente.isEmpty()) {
            return;
        }
        VersionDocumento version = pendiente.get();
        Optional<String> problema = TipoDeArchivo.deMime(version.getTipo()).filter(TipoDeArchivo::esContenedor)
                .flatMap(tipo -> problemaDeZip(version.getClave()));
        if (problema.isPresent()) {
            bloquear(version, problema.get());
            return;
        }
        if (!antivirus.disponible()) {
            if (limites.exigirAntivirus()) {
                version.setMotivo("Sin antivirus configurado: el documento espera su revisión.");
                return;
            }
            version.setEstado(EstadoDocumento.DISPONIBLE);
            version.setMotivo(null);
            return;
        }
        ResultadoDelAntivirus resultado;
        try (InputStream contenido = almacen.abrir(version.getClave())) {
            resultado = antivirus.revisar(contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + version.getClave(), e);
        }
        version.setRevisado(true);
        if (resultado.limpio()) {
            version.setEstado(EstadoDocumento.DISPONIBLE);
            version.setMotivo(null);
        } else {
            bloquear(version, "El antivirus encontró " + resultado.firma() + ".");
        }
    }

    private void bloquear(VersionDocumento version, String motivo) {
        version.setEstado(EstadoDocumento.BLOQUEADO);
        version.setMotivo(motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
        auditoria.registrar(version.getEmpresa().getId(), null, "DOCUMENTO_BLOQUEADO", RECURSO,
                version.getDocumento().getId(), Map.of(VERSION, version.getNumero()));
    }

    /** Un ZIP por dentro se revisa sobre una copia local: la inspeccion lo recorre entero. */
    private Optional<String> problemaDeZip(String clave) {
        try (Temporal copia = temporales.nuevo("zip-"); InputStream contenido = almacen.abrir(clave)) {
            Files.copy(contenido, copia.ruta(), StandardCopyOption.REPLACE_EXISTING);
            return inspeccionDeZip.problema(copia.ruta());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo revisar " + clave, e);
        }
    }

    /** Lo que se sabe de un archivo recibido y donde quedo mientras se guarda. */
    private record Recibido(Path archivo, long tamano, String sha256, TipoDeArchivo tipo) {

        String nombre(String pedido) {
            return NombreDeArchivo.sanear(pedido, tipo);
        }
    }

    /** Lee el cuerpo a un temporal, lo valida, y siempre borra el temporal al terminar. */
    private DocumentoResponse conArchivo(Long empresaId, String nombre, InputStream contenido,
            Function<Recibido, DocumentoResponse> guardar) {
        try (Temporal recibido = temporales.nuevo("subida-")) {
            Path temporal = recibido.ruta();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long tamano = copiar(new DigestInputStream(contenido, sha256), temporal);
            if (tamano == 0) {
                throw new SolicitudInvalidaException("El archivo está vacío.");
            }
            TipoDeArchivo tipo = detector.detectar(temporal, nombre)
                    .orElseThrow(() -> new TipoNoPermitidoException("Ese tipo de archivo no se acepta. Se aceptan PDF, "
                            + "imágenes, texto, CSV, Markdown, BPMN y documentos de Office u OpenOffice."));
            if (versionRepository.ocupado(empresaId) + tamano > limites.cuotaPorTienda()) {
                throw new ArchivoDemasiadoGrandeException("La organización llegó a su espacio para documentos.");
            }
            return guardar.apply(new Recibido(temporal, tamano, HexFormat.of().formatHex(sha256.digest()), tipo));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo recibir el archivo", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }

    private VersionDocumento guardarVersion(Documento documento, int numero, Long usuarioId, String nombre,
            Recibido recibido) {
        long empresaId = documento.getEmpresa().getId();
        String clave = ClaveDeAlmacen.deDocumento(empresaId, documento.getId(), numero,
                UUID.randomUUID().toString());
        almacen.guardar(clave, recibido.archivo(), recibido.tipo().mime());
        VersionDocumento version = versionRepository.save(VersionDocumento.builder().empresa(documento.getEmpresa())
                .documento(documento).numero(numero).nombre(recibido.nombre(nombre)).tipo(recibido.tipo().mime())
                .tamano(recibido.tamano()).sha256(recibido.sha256()).clave(clave).estado(EstadoDocumento.PENDIENTE)
                .subidoPor(usuarioId).fecha(LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS)).build());
        cola.encolar(empresaId, TRABAJO_DE_REVISION, String.valueOf(version.getId()));
        return version;
    }

    /** Copia sin pasar el tope: un cuerpo sin Content-Length tampoco entra entero. */
    private long copiar(InputStream entrada, Path destino) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream salida = Files.newOutputStream(destino)) {
            int leidos;
            while ((leidos = entrada.read(buffer)) > 0) {
                total += leidos;
                if (total > limites.tamanoMaximo()) {
                    throw new ArchivoDemasiadoGrandeException("Un documento no puede pasar de "
                            + (limites.tamanoMaximo() / (1024 * 1024)) + " MB.");
                }
                salida.write(buffer, 0, leidos);
            }
        }
        return total;
    }

    private Documento buscar(Long empresaId, Long documentoId) {
        return documentoRepository.findByIdAndEmpresaId(documentoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    /** Lo que firma un enlace: la tienda, el documento y la version. */
    static String recurso(Long empresaId, Long documentoId, int numero) {
        return "documento:" + empresaId + ":" + documentoId + ":" + numero;
    }

    private static DocumentoResponse respuesta(Documento documento, VersionDocumento ultima) {
        return new DocumentoResponse(documento.getId(), documento.getProceso().getId(), documento.getBpmnId(),
                documento.getNombre(), documento.getVersionActual(), ultima == null ? null : ultima.getEstado(),
                ultima == null ? null : ultima.getMotivo(), ultima != null && ultima.isRevisado(),
                ultima == null ? null : ultima.getTipo(), ultima == null ? 0 : ultima.getTamano(),
                documento.getVersion(), documento.getFechaModificacion());
    }

    private static VersionDocumentoResponse respuesta(VersionDocumento version) {
        return new VersionDocumentoResponse(version.getNumero(), version.getNombre(), version.getTipo(),
                version.getTamano(), version.getSha256(), version.getEstado(), version.getMotivo(),
                version.isRevisado(), version.getSubidoPor(), version.getFecha());
    }
}
