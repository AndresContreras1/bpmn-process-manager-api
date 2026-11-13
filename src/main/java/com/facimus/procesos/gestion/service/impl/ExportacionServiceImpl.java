package com.facimus.procesos.gestion.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.almacen.Almacen;
import com.facimus.procesos.common.almacen.ArchivoNoEncontradoException;
import com.facimus.procesos.common.almacen.ClaveDeAlmacen;
import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.archivos.ArchivosTemporales;
import com.facimus.procesos.common.archivos.ArchivosTemporales.Temporal;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
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
import com.facimus.procesos.gestion.service.ExportacionService;
import com.facimus.procesos.gestion.service.ParteDeLaExportacion;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 66: la exportacion completa de una tienda. Pedirla deja una fila PENDIENTE y un trabajo en la cola, con la tienda
 * bloqueada para que no haya dos en curso. La cola arma el ZIP en un archivo de paso, con la tienda fijada en el
 * contexto para que la base no le muestre filas de otra: cada modulo escribe su parte, y el paquete mide cada archivo
 * para el manifiesto. El ZIP va al almacen, la fila queda LISTA con su huella y su vencimiento, y el correo con el
 * enlace firmado sale por la cola. Leer y anotar van en transacciones cortas: armar puede tardar, y la base corta una
 * transaccion abierta que no hace nada.
 * <p>
 * La cola puede armar la misma exportacion dos veces a la vez: reabre un trabajo en curso que pasa de su umbral, y una
 * tienda grande puede tardar mas. Por eso cada intento sube su ZIP a su propia carpeta, y anotar bloquea la fila: el
 * primero que llega la deja lista, y el que llega tarde solo borra lo suyo.
 */
@Service
public class ExportacionServiceImpl implements ExportacionService {

    /** El tipo de trabajo de la cola que arma una exportacion. */
    public static final String TRABAJO_DE_GENERACION = "exportaciones.generar";

    /** La version del formato del ZIP: cambia si cambia la disposicion que documenta docs/export-format.md. */
    static final int FORMATO = 1;

    static final String RECURSO = "EXPORTACION";

    private static final Logger log = LoggerFactory.getLogger(ExportacionServiceImpl.class);
    private static final String NO_ENCONTRADA = "Exportación no encontrada.";
    private static final String MOTIVO = "motivo";

    /**
     * Lo que dice una exportacion que no se pudo armar, en la API y en la bitacora. Nunca el mensaje de la excepcion:
     * puede llevar SQL, nombres de buckets o rutas internas. Ese va al log, con el id de la exportacion.
     */
    static final String NO_SE_PUDO_ARMAR = "No se pudo armar la exportación: pide otra.";

    /** Lo que dice una que la limpieza dio por atascada. */
    static final String ATASCADA = "La cola no la terminó a tiempo: pide otra.";

    private final ExportacionRepository exportacionRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectProvider<ParteDeLaExportacion> partes;
    private final ColaDeTrabajos cola;
    private final Auditoria auditoria;
    private final Almacen almacen;
    private final EnlacesFirmados enlaces;
    private final ArchivosTemporales temporales;
    private final JsonMapper json;
    private final Clock reloj;
    private final TransactionTemplate lectura;
    private final TransactionTemplate escritura;
    private final Duration vidaDelEnlace;
    private final Duration pendienteMaxima;

    @SuppressWarnings("java:S107") // cada pieza de la exportacion, por su nombre
    public ExportacionServiceImpl(ExportacionRepository exportacionRepository, EmpresaRepository empresaRepository,
            UsuarioRepository usuarioRepository, ObjectProvider<ParteDeLaExportacion> partes, ColaDeTrabajos cola,
            Auditoria auditoria, Almacen almacen, EnlacesFirmados enlaces, ArchivosTemporales temporales,
            JsonMapper json, Clock reloj, PlatformTransactionManager transacciones,
            @Value("${exportaciones.enlace.vida:24h}") Duration vidaDelEnlace,
            @Value("${exportaciones.pendiente-maxima:6h}") Duration pendienteMaxima) {
        this.exportacionRepository = exportacionRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.partes = partes;
        this.cola = cola;
        this.auditoria = auditoria;
        this.almacen = almacen;
        this.enlaces = enlaces;
        this.temporales = temporales;
        this.json = json;
        this.reloj = reloj;
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
        this.escritura = new TransactionTemplate(transacciones);
        this.vidaDelEnlace = vidaDelEnlace;
        this.pendienteMaxima = pendienteMaxima;
    }

    @Override
    @Transactional
    public ExportacionResponse pedir(Long empresaId, Long usuarioId) {
        // Bloqueada: dos pedidos a la vez de la misma tienda no dejan dos exportaciones en curso
        Empresa empresa = empresaRepository.bloquear(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        if (exportacionRepository.existsByEmpresaIdAndEstado(empresaId, EstadoExportacion.PENDIENTE)) {
            throw new ReglaNegocioException("Ya hay una exportación en curso: su enlace llega por correo cuando esté "
                    + "lista.");
        }
        Exportacion exportacion = exportacionRepository.save(Exportacion.builder().empresa(empresa)
                .estado(EstadoExportacion.PENDIENTE).pedidaPor(usuarioId).pedidaEn(ahora()).build());
        cola.encolar(empresaId, TRABAJO_DE_GENERACION, String.valueOf(exportacion.getId()));
        auditoria.registrar(empresaId, usuarioId, "EXPORTACION_PEDIDA", RECURSO, exportacion.getId(), Map.of());
        return respuesta(exportacion);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hayEnCurso(Long empresaId) {
        return exportacionRepository.existsByEmpresaIdAndEstado(empresaId, EstadoExportacion.PENDIENTE);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExportacionResponse> listar(Long empresaId, Pageable pagina) {
        return PageResponse.from(exportacionRepository.findAllByEmpresaIdOrderByIdDesc(empresaId, pagina)
                .map(ExportacionServiceImpl::respuesta));
    }

    @Override
    @Transactional(readOnly = true)
    public ExportacionResponse obtener(Long empresaId, Long exportacionId) {
        return respuesta(exportacionRepository.findByIdAndEmpresaId(exportacionId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA)));
    }

    /**
     * Sin transaccion propia: la bitacora anota la descarga en la suya, y en una de solo lectura no podria. La
     * exportacion se lee con la tienda que dice el enlace, y solo despues de comprobar su firma. El enlace es de quien
     * la pidio mientras siga siendo un administrador activo: si en esas 24 horas lo desactivan o le quitan el rol, deja
     * de abrir.
     */
    @Override
    public Descarga descargar(Long empresaId, Long exportacionId, long vence, String firma) {
        if (!enlaces.vale(recursoFirmado(empresaId, exportacionId), Instant.ofEpochSecond(vence), firma)) {
            throw new RecursoNoEncontradoException("El enlace no existe o ya venció: pide otra exportación.");
        }
        Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(empresaId));
        try {
            Exportacion exportacion = exportacionRepository.findByIdAndEmpresaId(exportacionId, empresaId)
                    .filter(this::vigente)
                    .filter(una -> pedidaPorUnAdministrador(empresaId, una))
                    .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
            InputStream contenido = abrir(exportacion.getClave());
            anotarDescarga(empresaId, exportacionId, contenido);
            return new Descarga(archivo(exportacion), exportacion.getTamano(), contenido);
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    /** Si la bitacora falla, el ZIP ya abierto se cierra: si no, la conexion al almacen quedaria colgada. */
    private void anotarDescarga(Long empresaId, Long exportacionId, InputStream contenido) {
        try {
            auditoria.registrar(empresaId, null, "EXPORTACION_DESCARGADA", RECURSO, exportacionId, Map.of());
        } catch (RuntimeException e) {
            try {
                contenido.close();
            } catch (IOException cierre) {
                e.addSuppressed(cierre);
            }
            throw e;
        }
    }

    private boolean pedidaPorUnAdministrador(Long empresaId, Exportacion exportacion) {
        return exportacion.getPedidaPor() != null && usuarioRepository.findByIdAndEmpresaId(
                exportacion.getPedidaPor(), empresaId).filter(ExportacionServiceImpl::puedeRecibirla).isPresent();
    }

    /** Quien puede recibir el enlace y bajar la exportacion: un administrador activo de la tienda. */
    static boolean puedeRecibirla(Usuario usuario) {
        return usuario.isActivo() && usuario.getRolAcceso() == RolAcceso.ADMINISTRADOR;
    }

    private InputStream abrir(String clave) {
        try {
            return almacen.abrir(clave);
        } catch (ArchivoNoEncontradoException _) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADA);
        }
    }

    @Override
    public void generar(Long empresaId, Long exportacionId) {
        Optional<Pedido> pendiente = Objects.requireNonNull(lectura.execute(estado -> exportacionRepository
                .findByIdAndEmpresaId(exportacionId, empresaId)
                .filter(exportacion -> exportacion.getEstado() == EstadoExportacion.PENDIENTE)
                .map(exportacion -> pedido(empresaId, exportacion))));
        pendiente.ifPresent(pedido -> {
            Resultado resultado = armar(empresaId, exportacionId, pedido);
            escritura.executeWithoutResult(estado -> anotar(empresaId, exportacionId, resultado));
        });
    }

    /** Lo que el manifiesto dice de quien la pidio y de su tienda, leido mientras dura la transaccion. */
    private Pedido pedido(Long empresaId, Exportacion exportacion) {
        Optional<Usuario> quien = exportacion.getPedidaPor() == null ? Optional.empty()
                : usuarioRepository.findByIdAndEmpresaId(exportacion.getPedidaPor(), empresaId);
        return new Pedido(exportacion.getEmpresa().getNombre(), exportacion.getPedidaPor(),
                quien.map(Usuario::getNombre).orElse(null), quien.map(Usuario::getEmail).orElse(null),
                exportacion.getPedidaEn());
    }

    /** El ZIP entero, ya en el almacen; si algo no se puede armar, el motivo. */
    private Resultado armar(Long empresaId, Long exportacionId, Pedido pedido) {
        Alcance anterior = ContextoDeTienda.fijar(Alcance.deLaTienda(empresaId));
        try (Temporal temporal = temporales.nuevo("exportacion-")) {
            PaqueteZip.Resumen resumen;
            try (OutputStream salida = Files.newOutputStream(temporal.ruta());
                    PaqueteZip paquete = new PaqueteZip(salida, json)) {
                partes.orderedStream().forEach(parte -> parte.escribir(empresaId, paquete));
                resumen = paquete.terminar(cabecera(empresaId, exportacionId, pedido));
            }
            String intento = UUID.randomUUID().toString();
            String clave = ClaveDeAlmacen.deExportacion(empresaId, exportacionId, intento);
            almacen.guardar(clave, temporal.ruta(), "application/zip");
            return new Resultado(ClaveDeAlmacen.carpetaDelIntento(empresaId, exportacionId, intento), clave, resumen,
                    null);
        } catch (IOException e) {
            return fallida(empresaId, exportacionId, new UncheckedIOException(e));
        } catch (RuntimeException e) {
            return fallida(empresaId, exportacionId, e);
        } finally {
            ContextoDeTienda.restaurar(anterior);
        }
    }

    private static Resultado fallida(Long empresaId, Long exportacionId, RuntimeException e) {
        log.warn("No se pudo armar la exportacion {} de la tienda {}", exportacionId, empresaId, e);
        return new Resultado(null, null, null, NO_SE_PUDO_ARMAR);
    }

    /** Lo que el manifiesto dice antes de la lista de archivos. */
    private Map<String, Object> cabecera(Long empresaId, Long exportacionId, Pedido pedido) {
        Map<String, Object> tienda = new LinkedHashMap<>();
        tienda.put("id", empresaId);
        tienda.put("nombre", pedido.tienda());
        Map<String, Object> quien = new LinkedHashMap<>();
        quien.put("id", pedido.usuarioId());
        quien.put("nombre", pedido.nombre());
        quien.put("email", pedido.email());
        Map<String, Object> cabecera = new LinkedHashMap<>();
        cabecera.put("formato", FORMATO);
        cabecera.put("exportacion", exportacionId);
        cabecera.put("tienda", tienda);
        cabecera.put("pedidaPor", quien);
        cabecera.put("pedidaEn", pedido.pedidaEn().toString());
        cabecera.put("generadaEn", ahora().toString());
        cabecera.put("zonaHoraria", "UTC");
        return cabecera;
    }

    /**
     * Anota como quedo, con la fila bloqueada, si sigue pendiente. Si mientras se armaba otro intento la dejo lista o
     * la limpieza la dio por atascada, lo que subio este intento sobra y se borra, y solo eso: el ZIP de la que quedo
     * lista es el de otro intento, en otra carpeta. Si este intento no la pudo armar, queda FALLIDA y se borra la
     * carpeta entera de la exportacion: un intento anterior pudo subir su ZIP y caerse antes de anotarlo.
     */
    private void anotar(Long empresaId, Long exportacionId, Resultado resultado) {
        Optional<Exportacion> pendiente = exportacionRepository.bloquear(exportacionId, empresaId)
                .filter(exportacion -> exportacion.getEstado() == EstadoExportacion.PENDIENTE);
        if (pendiente.isEmpty()) {
            if (resultado.clave() != null) {
                almacen.borrarPrefijo(resultado.carpeta());
            }
            return;
        }
        Exportacion exportacion = pendiente.get();
        if (resultado.motivo() != null) {
            fallar(empresaId, exportacion, resultado.motivo());
            return;
        }
        // En segundos enteros: el vencimiento es lo que firma el enlace
        LocalDateTime lista = ahora().truncatedTo(ChronoUnit.SECONDS);
        exportacion.setEstado(EstadoExportacion.LISTA);
        exportacion.setClave(resultado.clave());
        exportacion.setTamano(resultado.resumen().tamano());
        exportacion.setSha256(resultado.resumen().sha256());
        exportacion.setArchivos(resultado.resumen().archivos());
        exportacion.setListaEn(lista);
        exportacion.setVenceEn(lista.plus(vidaDelEnlace));
        cola.encolar(empresaId, CorreoDeExportacion.TRABAJO_DE_CORREO, String.valueOf(exportacionId));
        auditoria.registrar(empresaId, null, "EXPORTACION_LISTA", RECURSO, exportacionId,
                Map.of("sha256", resultado.resumen().sha256(), "tamano", resultado.resumen().tamano(),
                        "archivos", resultado.resumen().archivos()));
    }

    /**
     * Cada una en su transaccion, y una que falla no frena a las demas: la siguiente pasada la intenta otra vez. El
     * ZIP se borra antes de anotarla vencida; si anotar falla, borrarlo otra vez no hace nada.
     */
    @Override
    public int limpiar() {
        LocalDateTime ahora = ahora();
        int cambiadas = 0;
        List<Clave> vencidas = Objects.requireNonNull(lectura.execute(estado -> claves(exportacionRepository
                .findAllByEstadoAndVenceEnBeforeOrderByIdAsc(EstadoExportacion.LISTA, ahora))));
        for (Clave vencida : vencidas) {
            cambiadas += cambiar(vencida, () -> vencer(vencida));
        }
        List<Clave> atascadas = Objects.requireNonNull(lectura.execute(estado -> claves(exportacionRepository
                .findAllByEstadoAndPedidaEnBeforeOrderByIdAsc(EstadoExportacion.PENDIENTE,
                        ahora.minus(pendienteMaxima)))));
        for (Clave atascada : atascadas) {
            cambiadas += cambiar(atascada, () -> abandonar(atascada));
        }
        return cambiadas;
    }

    private static List<Clave> claves(List<Exportacion> exportaciones) {
        return exportaciones.stream().map(una -> new Clave(una.getEmpresa().getId(), una.getId())).toList();
    }

    private static int cambiar(Clave clave, Runnable cambio) {
        try {
            cambio.run();
            return 1;
        } catch (RuntimeException e) {
            log.warn("No se pudo limpiar la exportacion {}", clave, e);
            return 0;
        }
    }

    private void vencer(Clave clave) {
        almacen.borrarPrefijo(ClaveDeAlmacen.carpetaDeExportacion(clave.empresaId(), clave.id()));
        escritura.executeWithoutResult(estado -> exportacionRepository.bloquear(clave.id(), clave.empresaId())
                .filter(exportacion -> exportacion.getEstado() == EstadoExportacion.LISTA)
                .ifPresent(exportacion -> {
                    exportacion.setEstado(EstadoExportacion.VENCIDA);
                    exportacion.setClave(null);
                    auditoria.registrar(clave.empresaId(), null, "EXPORTACION_VENCIDA", RECURSO, clave.id(),
                            Map.of());
                }));
    }

    private void abandonar(Clave clave) {
        escritura.executeWithoutResult(estado -> exportacionRepository.bloquear(clave.id(), clave.empresaId())
                .filter(exportacion -> exportacion.getEstado() == EstadoExportacion.PENDIENTE)
                .ifPresent(exportacion -> fallar(clave.empresaId(), exportacion, ATASCADA)));
    }

    /**
     * La deja FALLIDA, con la fila ya bloqueada, y borra lo que hubiera subido cualquier intento: nada apunta al ZIP de
     * una fallida, y sin esto quedaria en el almacen hasta que se purgue la tienda. Si borrar falla, la transaccion se
     * deshace y la fila sigue PENDIENTE: la cola o la limpieza vuelven a intentarlo.
     */
    private void fallar(Long empresaId, Exportacion exportacion, String motivo) {
        exportacion.setEstado(EstadoExportacion.FALLIDA);
        exportacion.setMotivo(motivo);
        almacen.borrarPrefijo(ClaveDeAlmacen.carpetaDeExportacion(empresaId, exportacion.getId()));
        auditoria.registrar(empresaId, null, "EXPORTACION_FALLIDA", RECURSO, exportacion.getId(),
                Map.of(MOTIVO, motivo));
    }

    /** Lista, y con su enlace todavia vigente. */
    private boolean vigente(Exportacion exportacion) {
        return exportacion.getEstado() == EstadoExportacion.LISTA && ahora().isBefore(exportacion.getVenceEn());
    }

    /** Lo que firma el enlace de una exportacion: de que tienda es y cual. */
    static String recursoFirmado(Long empresaId, Long exportacionId) {
        return "exportacion:" + empresaId + ":" + exportacionId;
    }

    /** La ruta firmada que baja una exportacion lista, hasta su vencimiento. */
    static String ruta(EnlacesFirmados enlaces, Long empresaId, Long exportacionId, LocalDateTime vence) {
        Instant hasta = vence.toInstant(ZoneOffset.UTC);
        return "/api/v1/descargas/exportaciones/" + exportacionId + "?empresa=" + empresaId + "&vence="
                + hasta.getEpochSecond() + "&firma=" + enlaces.firmar(recursoFirmado(empresaId, exportacionId), hasta);
    }

    /** {@code exportacion-2026-11-12-4.zip}: el dia en que quedo lista y cual es. */
    static String archivo(Exportacion exportacion) {
        return "exportacion-" + exportacion.getListaEn().toLocalDate() + "-" + exportacion.getId() + ".zip";
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }

    private static ExportacionResponse respuesta(Exportacion exportacion) {
        return new ExportacionResponse(exportacion.getId(), exportacion.getEstado(), exportacion.getPedidaPor(),
                exportacion.getPedidaEn(), exportacion.getListaEn(), exportacion.getVenceEn(), exportacion.getTamano(),
                exportacion.getArchivos(), exportacion.getSha256(), exportacion.getMotivo());
    }

    /** Lo que hace falta de una exportacion pendiente para armarla. */
    private record Pedido(String tienda, Long usuarioId, String nombre, String email, LocalDateTime pedidaEn) {
    }

    /** Como quedo: la carpeta del intento, la clave del ZIP y su resumen, o el motivo por el que no se pudo armar. */
    private record Resultado(String carpeta, String clave, PaqueteZip.Resumen resumen, String motivo) {
    }

    /** Una exportacion por su tienda y su id, para cambiarla en su propia transaccion. */
    private record Clave(Long empresaId, Long id) {
    }
}
