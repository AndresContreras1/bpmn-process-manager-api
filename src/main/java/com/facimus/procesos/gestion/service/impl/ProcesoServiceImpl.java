package com.facimus.procesos.gestion.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.event.ProcesoCreado;
import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.gestion.event.ProcesoPublicado;
import com.facimus.procesos.gestion.mapper.ProcesoMapper;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.ModoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.ProcesoSpecifications;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.BorradorCongelado;
import com.facimus.procesos.gestion.service.CarpetaService;
import com.facimus.procesos.gestion.service.DatosDeFicha;
import com.facimus.procesos.gestion.service.DiagnosticoDelModelo;
import com.facimus.procesos.gestion.service.FiltrosDeProceso;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.Instantanea;
import com.facimus.procesos.gestion.service.InstantaneaDelModelo;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.PoliticaDePublicacionService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.gestion.service.Visibilidad;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcesoServiceImpl implements ProcesoService {

    private static final String PROCESO_NO_ENCONTRADO = "Proceso no encontrado.";
    private static final String VERSION = "version";
    private static final int LARGO_DEL_MOTIVO = 2000;

    private final ProcesoRepository procesoRepository;
    private final CarpetaRepository carpetaRepository;
    private final CarpetaService carpetaService;
    private final Permisos permisos;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;
    private final HistorialCambioService historialCambioService;
    private final VersionService versionService;
    private final DiagnosticoDelModelo diagnosticoDelModelo;
    private final InstantaneaDelModelo instantaneaDelModelo;
    private final ProcesoMapper procesoMapper;
    private final Auditoria auditoria;
    private final PoliticaDePublicacionService politicaDePublicacion;
    private final Clock reloj;
    private final EntityManager entityManager;

    @Override
    public PageResponse<ProcesoResponse> buscar(Long empresaId, String nombre, EstadoProceso estado,
            String categoria, boolean incluirInactivos, Pageable pageable) {
        return PageResponse.from(procesoRepository
                .findAll(ProcesoSpecifications.conFiltros(empresaId, nombre, estado, categoria, incluirInactivos),
                        pageable)
                .map(this::respuesta));
    }

    @Override
    public PageResponse<ProcesoResponse> buscar(Long empresaId, FiltrosDeProceso filtros, Pageable pageable) {
        Set<Long> carpetas = filtros.carpetaId() == null ? null
                : carpetaService.conSubcarpetas(empresaId, filtros.carpetaId());
        Visibilidad visible = filtros.actor() == null ? Visibilidad.TODO : permisos.visibilidad(filtros.actor());
        Specification<Proceso> consulta = ProcesoSpecifications.conFiltros(empresaId, filtros.nombre(),
                filtros.estado(), filtros.categoria(), filtros.incluirInactivos())
                .and(ProcesoSpecifications.delRepositorio(carpetas, filtros.etiqueta(), filtros.duenoId(),
                        filtros.clasificacion(), filtros.revisionAntesDe()));
        if (!visible.todo()) {
            consulta = consulta.and(ProcesoSpecifications.visibles(visible.carpetasOcultas(),
                    visible.procesosOcultos(), visible.procesosVisibles()));
        }
        LocalDate hoy = LocalDate.now(reloj);
        return PageResponse.from(procesoRepository.findAll(consulta, pageable)
                .map(proceso -> procesoMapper.toResponse(proceso, hoy)));
    }

    @Override
    @Transactional
    public ProcesoResponse editarFicha(Long empresaId, Long procesoId, Long usuarioId, DatosDeFicha ficha) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        proceso.verificarVersion(ficha.version());
        Usuario autor = autor(empresaId, usuarioId);
        if (ficha.carpetaId() != null && !carpetaRepository.existsByIdAndEmpresaId(ficha.carpetaId(), empresaId)) {
            throw new RecursoNoEncontradoException("Carpeta no encontrada.");
        }
        exigirPuedeMoverlo(empresaId, usuarioId, proceso, ficha.carpetaId());
        if (ficha.duenoId() != null && !usuarioRepository.existsByIdAndEmpresaId(ficha.duenoId(), empresaId)) {
            throw new RecursoNoEncontradoException("Usuario no encontrado.");
        }
        proceso.setCarpetaId(ficha.carpetaId());
        proceso.setDuenoId(ficha.duenoId());
        proceso.setProximaRevision(ficha.proximaRevision());
        if (ficha.mesesEntreRevisiones() != null) {
            proceso.setMesesEntreRevisiones(ficha.mesesEntreRevisiones());
        }
        proceso.setClasificacion(ficha.clasificacion());
        proceso.setEtiquetas(new ArrayList<>(ficha.etiquetas().stream()
                .map(etiqueta -> etiqueta.trim().toLowerCase(Locale.ROOT)).distinct().sorted().toList()));
        proceso = procesoRepository.saveAndFlush(proceso);
        historialCambioService.registrar(proceso, autor, "Ficha del repositorio editada.");
        return conBorrador(proceso);
    }

    /** PR 60: mover un proceso cambia lo que hereda, asi que entre carpetas con concesiones hay que administrarlo. */
    private void exigirPuedeMoverlo(Long empresaId, Long usuarioId, Proceso proceso, Long carpetaNueva) {
        if (Objects.equals(proceso.getCarpetaId(), carpetaNueva)) {
            return;
        }
        boolean cambiaLoQueHereda = permisos.tieneConcesiones(empresaId, Recurso.proceso(proceso.getId()))
                || carpetaNueva != null && permisos.tieneConcesiones(empresaId, Recurso.carpeta(carpetaNueva));
        if (cambiaLoQueHereda) {
            permisos.exigir(empresaId, usuarioId, Accion.ADMINISTRAR, Recurso.proceso(proceso.getId()));
        }
    }

    @Override
    public List<String> etiquetasEnUso(Long empresaId) {
        return procesoRepository.etiquetasEnUso(empresaId);
    }

    @Override
    @Transactional
    public ProcesoResponse crear(Long empresaId, Long usuarioId, String nombre, String descripcion,
            String categoria) {
        validarNombreLibre(empresaId, nombre);
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Usuario autor = autor(empresaId, usuarioId);

        Proceso proceso = procesoRepository.save(Proceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .descripcion(descripcion)
                .categoria(categoria)
                .build());

        // El modulo de modelado crea el pool de la empresa dentro de esta misma transaccion.
        eventos.publishEvent(new ProcesoCreado(empresaId, proceso.getId()));

        historialCambioService.registrar(proceso, autor, "Proceso creado.");
        return conBorrador(proceso);
    }

    @Override
    public ProcesoResponse obtener(Long empresaId, Long procesoId, boolean incluirInactivos) {
        return conBorrador(buscar(empresaId, procesoId, incluirInactivos));
    }

    /**
     * La puerta de lectura no calcula si el borrador tiene cambios: quien arma el diagrama ya tendra la huella de
     * hoy, y le basta con la de la version vigente para compararlas sin volver a leer el modelo.
     */
    @Override
    public ProcesoLectura obtenerParaLectura(Long empresaId, Long procesoId) {
        Proceso proceso = procesoRepository.paraLectura(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(PROCESO_NO_ENCONTRADO));
        Long duena = proceso.getEmpresa().getId();
        return new ProcesoLectura(respuesta(proceso), duena, !duena.equals(empresaId),
                proceso.getHuellaPublicada());
    }

    @Override
    public ProcesoDetalleResponse obtenerDetalle(Long empresaId, Long procesoId, boolean incluirInactivos) {
        Proceso proceso = buscar(empresaId, procesoId, incluirInactivos);
        return new ProcesoDetalleResponse(conBorrador(proceso),
                historialCambioService.listarPorProceso(empresaId, procesoId));
    }

    @Override
    public List<HistorialCambioResponse> listarHistorial(Long empresaId, Long procesoId) {
        buscarActivo(empresaId, procesoId);
        return historialCambioService.listarPorProceso(empresaId, procesoId);
    }

    @Override
    @Transactional
    public ProcesoResponse editarDatos(Long empresaId, Long procesoId, Long usuarioId, String nombre,
            String descripcion, String categoria, Long version) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        proceso.verificarVersion(version);
        Usuario autor = autor(empresaId, usuarioId);
        if (!proceso.getNombre().equalsIgnoreCase(nombre)) {
            validarNombreLibre(empresaId, nombre);
        }

        proceso.setNombre(nombre);
        proceso.setDescripcion(descripcion);
        proceso.setCategoria(categoria);
        // Con flush la version nueva ya esta en la entidad al armar la respuesta.
        proceso = procesoRepository.saveAndFlush(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso editado.");
        return conBorrador(proceso);
    }

    /**
     * D2: pedir el estado PUBLICADO publica. Un proceso ya publicado se vuelve a publicar, y eso crea la version
     * siguiente: lo que se edita despues es el borrador de trabajo, y la version anterior se queda como estaba.
     */
    @Override
    @Transactional
    public ProcesoResponse cambiarEstado(Long empresaId, Long procesoId, Long usuarioId,
            EstadoProceso nuevoEstado, Long version) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        proceso.verificarVersion(version);
        if (nuevoEstado == EstadoProceso.BORRADOR) {
            if (proceso.getEstado() == EstadoProceso.PUBLICADO) {
                throw new ReglaNegocioException("Un proceso publicado no puede volver a borrador.");
            }
            return respuesta(proceso).conBorradorPendiente(false);
        }
        // D32: con la aprobacion encendida nadie publica directo, tampoco quien administra: la politica se apaga a la
        // vista, en la bitacora, y no con una excepcion que no deja rastro
        if (politicaDePublicacion.conAprobacion(empresaId)) {
            throw new ReglaNegocioException("Esta organización publica con aprobación: hay que pedir una revisión del "
                    + "proceso, y lo publica la última aprobación que haga falta.");
        }
        return publicar(proceso, autor(empresaId, usuarioId));
    }

    @Override
    @Transactional
    public ProcesoResponse cambiarModo(Long empresaId, Long procesoId, Long usuarioId, ModoProceso modo,
            Long version) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        proceso.verificarVersion(version);
        Usuario autor = autor(empresaId, usuarioId);
        if (proceso.getModo() == modo) {
            return conBorrador(proceso);
        }
        proceso.setModo(modo);
        proceso = procesoRepository.saveAndFlush(proceso);
        historialCambioService.registrar(proceso, autor, modo.ejecuta() ? "Proceso pasa a ejecutable."
                : "Proceso pasa a documental: se publica y se documenta, pero no abre casos.");
        return conBorrador(proceso);
    }

    /**
     * R-44 y R-45: no se publica lo que no se puede ejecutar, ni se publica dos veces lo mismo. Publicar directo es un
     * cambio de quien publica: sube la version del proceso y lo deja modificado por el.
     */
    private ProcesoResponse publicar(Proceso proceso, Usuario autor) {
        Long empresaId = proceso.getEmpresa().getId();
        exigirSinErrores("El proceso no se puede publicar: ",
                diagnosticoDelModelo.errores(empresaId, procesoMapper.toResponse(proceso)));
        int numero = versionService.siguienteNumero(empresaId, proceso.getId());
        Instantanea instantanea = instantaneaDelModelo.tomar(empresaId,
                procesoMapper.toResponse(proceso).publicadoComo(numero));
        exigirCambios(proceso, instantanea);
        versionService.publicar(proceso, numero, autor.getId(), instantanea);
        proceso.setEstado(EstadoProceso.PUBLICADO);
        proceso.setVersionPublicada(numero);
        proceso.setHuellaPublicada(instantanea.huella());
        proceso = procesoRepository.saveAndFlush(proceso);
        registrarPublicacion(proceso, autor, numero, Map.of(VERSION, numero));
        return respuesta(proceso).conBorradorPendiente(false);
    }

    /** Lo que deja una publicacion, la misma venga de publicar directo o de una solicitud aprobada (D2). */
    private void registrarPublicacion(Proceso proceso, Usuario autor, int numero, Map<String, Object> detalle) {
        Long empresaId = proceso.getEmpresa().getId();
        historialCambioService.registrar(proceso, autor, "Versión " + numero + " publicada.");
        eventos.publishEvent(new ProcesoPublicado(empresaId, proceso.getId(), numero, autor.getNombre()));
        auditoria.registrar(empresaId, autor.getId(), "PROCESO_PUBLICADO", "PROCESO", proceso.getId(), detalle);
    }

    private static void exigirSinErrores(String que, List<String> errores) {
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException(que + errores.size()
                    + (errores.size() == 1 ? " error de diagnóstico." : " errores de diagnóstico."), errores);
        }
    }

    private static void exigirCambios(Proceso proceso, Instantanea instantanea) {
        if (instantanea.huella().equals(proceso.getHuellaPublicada())) {
            throw new ReglaNegocioException("No hay cambios desde la versión " + proceso.getVersionPublicada() + ".");
        }
    }

    @Override
    public BorradorCongelado congelar(Long empresaId, Long procesoId) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        exigirSinErrores("No se puede pedir la revisión: ",
                diagnosticoDelModelo.errores(empresaId, procesoMapper.toResponse(proceso)));
        int numero = versionService.siguienteNumero(empresaId, procesoId);
        Instantanea instantanea = instantaneaDelModelo.tomar(empresaId,
                procesoMapper.toResponse(proceso).publicadoComo(numero));
        exigirCambios(proceso, instantanea);
        return new BorradorCongelado(numero, proceso.getVersionPublicada(), instantanea);
    }

    @Override
    public Optional<String> impedimento(Long empresaId, Long procesoId, BorradorCongelado congelado) {
        return impedimento(buscarActivo(empresaId, procesoId), congelado);
    }

    /**
     * Lo que se aprobo es la diferencia contra la vigente de ese dia: si despues se publico o se retiro otra version,
     * publicarlo pisaria lo que paso entre medio sin que nadie lo hubiera visto.
     */
    private Optional<String> impedimento(Proceso proceso, BorradorCongelado congelado) {
        Long empresaId = proceso.getEmpresa().getId();
        if (versionService.siguienteNumero(empresaId, proceso.getId()) != congelado.numero()
                || !Objects.equals(proceso.getVersionPublicada(), congelado.versionBase())) {
            return Optional.of("La versión vigente cambió desde que se pidió la revisión (era "
                    + nombrar(congelado.versionBase()) + " y ahora es " + nombrar(proceso.getVersionPublicada())
                    + "): hay que pedirla de nuevo.");
        }
        List<String> errores = diagnosticoDelModelo.errores(empresaId, proceso.getId(),
                congelado.instantanea().definicion());
        if (!errores.isEmpty()) {
            String motivo = "El diagnóstico ya no está limpio: " + String.join(" ", errores);
            return Optional.of(motivo.length() <= LARGO_DEL_MOTIVO ? motivo
                    : motivo.substring(0, LARGO_DEL_MOTIVO - 3) + "...");
        }
        return Optional.empty();
    }

    private static String nombrar(Integer version) {
        return version == null ? "ninguna" : "la " + version;
    }

    @Override
    @Transactional
    public Optional<String> publicarCongelado(Long empresaId, Long procesoId, Long usuarioId,
            BorradorCongelado congelado, Long solicitudId) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        Optional<String> impedimento = impedimento(proceso, congelado);
        if (impedimento.isPresent()) {
            return impedimento;
        }
        Usuario autor = autor(empresaId, usuarioId);
        int numero = congelado.numero();
        versionService.publicar(proceso, numero, usuarioId, congelado.instantanea());
        // D31: publicar lo que otros revisaron no es cambiar el proceso. Si se guardara como un cambio, quien dio la
        // ultima aprobacion quedaria como el ultimo en modificarlo y la separacion de funciones lo sacaria de las
        // proximas solicitudes; tampoco sube la version que tiene abierta quien edita la ficha
        procesoRepository.anotarPublicacion(procesoId, empresaId, numero, congelado.instantanea().huella());
        entityManager.refresh(proceso);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put(VERSION, numero);
        detalle.put("solicitud", solicitudId);
        registrarPublicacion(proceso, autor, numero, detalle);
        return Optional.empty();
    }

    @Override
    @Transactional
    public ProcesoResponse recertificar(Long empresaId, Long procesoId, Long usuarioId, String nota) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        Usuario autor = autor(empresaId, usuarioId);
        // Lo confirma quien responde por el; si ya no esta, quien puede administrarlo
        if (!usuarioId.equals(proceso.getDuenoId())) {
            permisos.exigir(empresaId, usuarioId, Accion.ADMINISTRAR, Recurso.proceso(procesoId));
        }
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate anterior = proceso.getProximaRevision();
        LocalDate siguiente = hoy.plusMonths(proceso.getMesesEntreRevisiones());
        // Confirmar que sigue vigente no es cambiarlo: no le sube la version a quien edita la ficha, ni deja a quien
        // lo confirma como el ultimo en modificarlo, que la separacion de funciones sacaria de sus aprobadores (D31)
        procesoRepository.anotarRecertificacion(procesoId, empresaId, siguiente);
        entityManager.refresh(proceso);

        historialCambioService.registrar(proceso, autor, "Revisión hecha: el proceso sigue vigente. Próxima revisión: "
                + siguiente + ".");
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("proximaRevision", siguiente.toString());
        if (anterior != null) {
            detalle.put("revisionAnterior", anterior.toString());
            detalle.put("vencida", anterior.isBefore(hoy));
        }
        if (nota != null && !nota.isBlank()) {
            detalle.put("nota", nota.strip());
        }
        auditoria.registrar(empresaId, usuarioId, "PROCESO_RECERTIFICADO", "PROCESO", procesoId, detalle);
        return conBorrador(proceso);
    }

    @Override
    @Transactional
    public void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        Usuario autor = autor(empresaId, usuarioId);

        proceso.setActivo(false);
        procesoRepository.save(proceso);
        // El modulo de modelado da de baja el modelo del proceso dentro de esta misma transaccion.
        eventos.publishEvent(new ProcesoEliminado(empresaId, procesoId));

        historialCambioService.registrar(proceso, autor, "Proceso eliminado (baja logica).");
    }

    /**
     * Si el borrador tiene cambios sin publicar se sabe comparando el modelo de hoy con la huella de la version
     * vigente, asi que cuesta leer el diagrama entero. Se calcula al responder un proceso concreto, no en un
     * listado, donde seria un diagrama por fila.
     */
    private ProcesoResponse conBorrador(Proceso proceso) {
        ProcesoResponse respuesta = respuesta(proceso);
        if (proceso.getVersionPublicada() == null) {
            return respuesta.conBorradorPendiente(false);
        }
        Instantanea instantanea = instantaneaDelModelo.tomar(proceso.getEmpresa().getId(), respuesta);
        return respuesta.conBorradorPendiente(!instantanea.huella().equals(proceso.getHuellaPublicada()));
    }

    /** HU-06.3: un proceso eliminado sigue ahi, y el administrador lo lee con activo en false. */
    private Proceso buscar(Long empresaId, Long procesoId, boolean incluirInactivos) {
        return incluirInactivos
                ? procesoRepository.findByIdAndEmpresaId(procesoId, empresaId)
                        .orElseThrow(() -> new RecursoNoEncontradoException(PROCESO_NO_ENCONTRADO))
                : buscarActivo(empresaId, procesoId);
    }

    private Proceso buscarActivo(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(PROCESO_NO_ENCONTRADO));
    }

    /** PR 64: lo que se responde de un proceso dice si su revision vencio, contra el dia del reloj de la API. */
    private ProcesoResponse respuesta(Proceso proceso) {
        return procesoMapper.toResponse(proceso, LocalDate.now(reloj));
    }

    private Usuario autor(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    private void validarNombreLibre(Long empresaId, String nombre) {
        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
            throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
        }
    }
}
