package com.facimus.procesos.modelado.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.security.ContextoDeTienda;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.VersionResponse;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.PreferenciasService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.dto.response.CaracterizacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.dto.response.EnlaceDeDescargaResponse;
import com.facimus.procesos.modelado.dto.response.EventoResponse;
import com.facimus.procesos.modelado.dto.response.GatewayResponse;
import com.facimus.procesos.modelado.dto.response.LaneResponse;
import com.facimus.procesos.modelado.dto.response.ManualResponse;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.model.Documentacion;
import com.facimus.procesos.modelado.model.EstadoDocumento;
import com.facimus.procesos.modelado.model.EstadoManual;
import com.facimus.procesos.modelado.model.Manual;
import com.facimus.procesos.modelado.repository.DocumentacionRepository;
import com.facimus.procesos.modelado.repository.ManualRepository;
import com.facimus.procesos.modelado.service.Descarga;
import com.facimus.procesos.modelado.service.DocumentacionService;
import com.facimus.procesos.modelado.service.DocumentoService;
import com.facimus.procesos.modelado.service.ManualService;
import com.facimus.procesos.modelado.service.impl.RedaccionDelManual.Adjunto;
import com.facimus.procesos.modelado.service.impl.RedaccionDelManual.Campo;
import com.facimus.procesos.modelado.service.impl.RedaccionDelManual.Contenido;
import com.facimus.procesos.modelado.service.impl.RedaccionDelManual.Paso;
import com.facimus.procesos.modelado.service.impl.RedaccionDelManual.Seccion;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 62: el manual de una version. Se pide, la cola lo arma con lo que la version dice (su diagrama, tal como se
 * publico, y su dibujo) y lo que el proceso tiene escrito hoy (la ficha, la documentacion y los documentos), y quedan
 * el HTML y el PDF en el almacen. Si algo no se puede armar, el manual queda fallido con el motivo: intentarlo otra
 * vez daria lo mismo.
 */
@Service
public class ManualServiceImpl implements ManualService {

    /** El tipo de trabajo de la cola que arma un manual. */
    public static final String GENERAR = "manuales.generar";

    private static final Logger log = LoggerFactory.getLogger(ManualServiceImpl.class);
    private static final String PDF = "pdf";
    private static final String HTML = "html";

    private final ManualRepository manualRepository;
    private final ProcesoRepository procesoRepository;
    private final DocumentacionRepository documentacionRepository;
    private final VersionService versionService;
    private final DocumentacionService documentacionService;
    private final DocumentoService documentoService;
    private final PreferenciasService preferenciasService;
    private final RedaccionDelManual redaccion;
    private final ColaDeTrabajos cola;
    private final Almacen almacen;
    private final EnlacesFirmados enlaces;
    private final JsonMapper json;
    private final Clock reloj;
    private final Duration vidaDelEnlace;

    @SuppressWarnings("java:S107")
    public ManualServiceImpl(ManualRepository manualRepository, ProcesoRepository procesoRepository,
            DocumentacionRepository documentacionRepository, VersionService versionService,
            DocumentacionService documentacionService, DocumentoService documentoService,
            PreferenciasService preferenciasService, RedaccionDelManual redaccion, ColaDeTrabajos cola,
            Almacen almacen, EnlacesFirmados enlaces, JsonMapper json, Clock reloj,
            @Value("${documentos.enlace.vida:5m}") Duration vidaDelEnlace) {
        this.manualRepository = manualRepository;
        this.procesoRepository = procesoRepository;
        this.documentacionRepository = documentacionRepository;
        this.versionService = versionService;
        this.documentacionService = documentacionService;
        this.documentoService = documentoService;
        this.preferenciasService = preferenciasService;
        this.redaccion = redaccion;
        this.cola = cola;
        this.almacen = almacen;
        this.enlaces = enlaces;
        this.json = json;
        this.reloj = reloj;
        this.vidaDelEnlace = vidaDelEnlace;
    }

    @Override
    @Transactional
    public ManualResponse pedir(Long empresaId, Long usuarioId, Long procesoId, Integer version) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        int numero;
        if (version == null) {
            if (proceso.getVersionPublicada() == null) {
                throw new ReglaNegocioException("El proceso no tiene una versión vigente: publícalo primero.");
            }
            numero = proceso.getVersionPublicada();
        } else {
            numero = versionService.obtener(empresaId, procesoId, version).numero();
        }
        Manual manual = manualRepository.save(Manual.builder().empresa(proceso.getEmpresa()).proceso(proceso)
                .versionNumero(numero).idioma(preferenciasService.obtener(empresaId, usuarioId).idioma())
                .estado(EstadoManual.PENDIENTE).pedidoPor(usuarioId).pedidoEn(ahora()).build());
        cola.encolar(empresaId, GENERAR, String.valueOf(manual.getId()));
        return respuesta(manual);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualResponse> listar(Long empresaId, Long procesoId) {
        procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        return manualRepository.findAllByProcesoIdAndEmpresaIdOrderByIdDesc(procesoId, empresaId).stream()
                .map(ManualServiceImpl::respuesta).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManualResponse obtener(Long empresaId, Long manualId) {
        return respuesta(buscar(empresaId, manualId));
    }

    @Override
    @Transactional(readOnly = true)
    public EnlaceDeDescargaResponse enlace(Long empresaId, Long manualId, String formato) {
        String cual = formato(formato);
        Manual manual = buscar(empresaId, manualId);
        if (manual.getEstado() != EstadoManual.DISPONIBLE) {
            throw new ReglaNegocioException(manual.getEstado() == EstadoManual.PENDIENTE
                    ? "El manual todavía se está armando: inténtalo en un momento."
                    : "El manual no se pudo armar: " + manual.getMotivo());
        }
        Instant vence = enlaces.vencimiento(vidaDelEnlace);
        String firma = enlaces.firmar(recurso(empresaId, manualId, cual), vence);
        return new EnlaceDeDescargaResponse("/api/v1/descargas/manuales/" + manualId + "/" + cual + "?empresa="
                + empresaId + "&vence=" + vence.getEpochSecond() + "&firma=" + firma, vence);
    }

    @Override
    @Transactional(readOnly = true)
    public Descarga descargar(Long empresaId, Long manualId, String formato, long vence, String firma) {
        String cual = formato(formato);
        if (!enlaces.vale(recurso(empresaId, manualId, cual), Instant.ofEpochSecond(vence), firma)) {
            throw new RecursoNoEncontradoException("El enlace no existe o ya venció: pide otro.");
        }
        return ContextoDeTienda.deLaTienda(empresaId, () -> {
            Manual manual = buscar(empresaId, manualId);
            if (manual.getEstado() != EstadoManual.DISPONIBLE) {
                throw new RecursoNoEncontradoException("Manual no encontrado.");
            }
            String nombre = archivo(manual.getProceso().getNombre(), manual.getVersionNumero(), cual);
            String clave = PDF.equals(cual) ? manual.getClavePdf() : manual.getClaveHtml();
            String tipo = PDF.equals(cual) ? "application/pdf" : "text/html;charset=UTF-8";
            byte[] contenido;
            try (InputStream entrada = almacen.abrir(clave)) {
                contenido = entrada.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer " + clave, e);
            }
            return new Descarga(nombre, tipo, contenido.length, new ByteArrayInputStream(contenido));
        });
    }

    @Override
    @Transactional
    public void generar(Long empresaId, Long manualId) {
        Optional<Manual> pendiente = manualRepository.findByIdAndEmpresaId(manualId, empresaId)
                .filter(manual -> manual.getEstado() == EstadoManual.PENDIENTE);
        if (pendiente.isEmpty()) {
            return;
        }
        Manual manual = pendiente.get();
        try {
            Contenido contenido = contenido(empresaId, manual);
            String html = redaccion.html(contenido, manual.getIdioma());
            byte[] pdf = redaccion.pdf(html);
            manual.setClaveHtml(guardar(ClaveDeAlmacen.deManual(empresaId, manualId, HTML),
                    html.getBytes(StandardCharsets.UTF_8), "text/html"));
            manual.setClavePdf(guardar(ClaveDeAlmacen.deManual(empresaId, manualId, PDF), pdf, "application/pdf"));
            manual.setEstado(EstadoManual.DISPONIBLE);
            manual.setMotivo(null);
        } catch (RuntimeException e) {
            log.warn("No se pudo armar el manual {} de la tienda {}", manualId, empresaId, e);
            manual.setEstado(EstadoManual.FALLIDO);
            String motivo = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            manual.setMotivo(motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
        }
        manual.setListoEn(ahora());
    }

    /** Lo que el manual dice, en el orden en que se lee. */
    private Contenido contenido(Long empresaId, Manual manual) {
        Long procesoId = manual.getProceso().getId();
        int numero = manual.getVersionNumero();
        VersionResponse version = versionService.obtener(empresaId, procesoId, numero);
        DiagramaResponse diagrama = json.readValue(versionService.definicion(empresaId, procesoId, numero),
                DiagramaResponse.class);
        Map<String, String> documentacion = new HashMap<>();
        String delProceso = null;
        for (Documentacion texto : documentacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId,
                empresaId)) {
            if (texto.getBpmnId() == null) {
                delProceso = texto.getHtml();
            } else {
                documentacion.put(texto.getBpmnId(), texto.getHtml());
            }
        }
        List<Adjunto> documentos = documentoService.listar(empresaId, procesoId, null).stream()
                .filter(documento -> documento.estado() == EstadoDocumento.DISPONIBLE)
                .map(documento -> new Adjunto(documento.nombre(), documento.versionActual())).toList();
        return new Contenido(diagrama.proceso().nombre(), diagrama.proceso().descripcion(),
                diagrama.proceso().categoria(), numero, version.fechaPublicacion(),
                ficha(documentacionService.caracterizacion(empresaId, procesoId)),
                versionService.dibujo(empresaId, procesoId, numero).orElse(null), delProceso,
                secciones(diagrama, documentacion), documentos, ahora());
    }

    private static List<Campo> ficha(CaracterizacionResponse ficha) {
        List<Campo> campos = new ArrayList<>();
        agregar(campos, "objetivo", ficha.objetivo());
        agregar(campos, "alcance", ficha.alcance());
        agregar(campos, "entradas", ficha.entradas());
        agregar(campos, "salidas", ficha.salidas());
        agregar(campos, "proveedores", ficha.proveedores());
        agregar(campos, "clientes", ficha.clientes());
        agregar(campos, "indicadores", ficha.indicadores());
        agregar(campos, "riesgos", ficha.riesgos());
        agregar(campos, "normativa", ficha.normativa());
        return campos;
    }

    private static void agregar(List<Campo> campos, String clave, String valor) {
        if (valor != null && !valor.isBlank()) {
            campos.add(new Campo(clave, valor));
        }
    }

    /** Cada lane con sus pasos de izquierda a derecha, en el orden de los participantes del diagrama. */
    private static List<Seccion> secciones(DiagramaResponse diagrama, Map<String, String> documentacion) {
        Map<Long, String> participantes = new HashMap<>();
        for (PoolResponse pool : diagrama.pools()) {
            participantes.put(pool.id(), pool.nombre());
        }
        List<Seccion> secciones = new ArrayList<>();
        for (LaneResponse lane : diagrama.lanes()) {
            List<Paso> pasos = Stream.of(
                    diagrama.actividades().stream().filter(a -> Objects.equals(a.laneId(), lane.id()))
                            .map(a -> new Ordenado(a.posicionX(), paso(a, documentacion))),
                    diagrama.gateways().stream().filter(g -> Objects.equals(g.laneId(), lane.id()))
                            .map(g -> new Ordenado(g.posicionX(), paso(g, documentacion))),
                    diagrama.eventos().stream().filter(e -> Objects.equals(e.laneId(), lane.id()))
                            .map(e -> new Ordenado(e.posicionX(), paso(e, documentacion))))
                    .flatMap(s -> s).sorted(Comparator.comparingInt(Ordenado::x)).map(Ordenado::paso).toList();
            if (!pasos.isEmpty()) {
                secciones.add(new Seccion(participantes.getOrDefault(lane.poolId(), ""), lane.nombre(),
                        lane.rolProcesoNombre(), pasos));
            }
        }
        return secciones;
    }

    private record Ordenado(int x, Paso paso) {
    }

    private static Paso paso(ActividadResponse actividad, Map<String, String> documentacion) {
        return new Paso(actividad.nombre(), "tarea", vacioANulo(actividad.descripcion()),
                documentacion.get(actividad.bpmnId()));
    }

    private static Paso paso(GatewayResponse gateway, Map<String, String> documentacion) {
        return new Paso(nombre(gateway.nombre()), "decision", null, documentacion.get(gateway.bpmnId()));
    }

    private static Paso paso(EventoResponse evento, Map<String, String> documentacion) {
        return new Paso(nombre(evento.nombre()), "evento", null, documentacion.get(evento.bpmnId()));
    }

    private static String nombre(String nombre) {
        return nombre == null || nombre.isBlank() ? "—" : nombre;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    private String guardar(String clave, byte[] contenido, String tipo) {
        Path temporal = null;
        try {
            temporal = Files.createTempFile("manual-", ".tmp");
            Files.write(temporal, contenido);
            almacen.guardar(clave, temporal, tipo);
            return clave;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el manual", e);
        } finally {
            borrar(temporal);
        }
    }

    private static void borrar(Path archivo) {
        if (archivo != null) {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException e) {
                archivo.toFile().deleteOnExit();
            }
        }
    }

    private Manual buscar(Long empresaId, Long manualId) {
        return manualRepository.findByIdAndEmpresaId(manualId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Manual no encontrado."));
    }

    private static String formato(String formato) {
        if (!PDF.equals(formato) && !HTML.equals(formato)) {
            throw new SolicitudInvalidaException("El formato es pdf o html.");
        }
        return formato;
    }

    /** El nombre del archivo: el proceso, su version y el formato, sin nada que un sistema de archivos rechace. */
    static String archivo(String proceso, int version, String formato) {
        String limpio = proceso.replaceAll("[^\\p{L}\\p{N} ._-]", "").strip();
        return (limpio.isEmpty() ? "manual" : limpio) + " v" + version + "." + formato;
    }

    static String recurso(Long empresaId, Long manualId, String formato) {
        return "manual:" + empresaId + ":" + manualId + ":" + formato;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }

    private static ManualResponse respuesta(Manual manual) {
        return new ManualResponse(manual.getId(), manual.getProceso().getId(), manual.getVersionNumero(),
                manual.getIdioma(), manual.getEstado(), manual.getMotivo(), manual.getPedidoEn(), manual.getListoEn());
    }
}
