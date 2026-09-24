package com.facimus.procesos.gestion.service.impl;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoLectura;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.event.ProcesoCreado;
import com.facimus.procesos.gestion.event.ProcesoEliminado;
import com.facimus.procesos.gestion.mapper.ProcesoMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.ProcesoSpecifications;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.DiagnosticoDelModelo;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.Instantanea;
import com.facimus.procesos.gestion.service.InstantaneaDelModelo;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcesoServiceImpl implements ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;
    private final HistorialCambioService historialCambioService;
    private final VersionService versionService;
    private final DiagnosticoDelModelo diagnosticoDelModelo;
    private final InstantaneaDelModelo instantaneaDelModelo;
    private final ProcesoMapper procesoMapper;

    @Override
    public PageResponse<ProcesoResponse> buscar(Long empresaId, String nombre, EstadoProceso estado,
            String categoria, boolean incluirInactivos, Pageable pageable) {
        return PageResponse.from(procesoRepository
                .findAll(ProcesoSpecifications.conFiltros(empresaId, nombre, estado, categoria, incluirInactivos),
                        pageable)
                .map(procesoMapper::toResponse));
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
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        Long duena = proceso.getEmpresa().getId();
        return new ProcesoLectura(procesoMapper.toResponse(proceso), duena, !duena.equals(empresaId),
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
            return procesoMapper.toResponse(proceso).conBorradorPendiente(false);
        }
        return publicar(proceso, autor(empresaId, usuarioId));
    }

    /** R-44 y R-45: no se publica lo que no se puede ejecutar, ni se publica dos veces lo mismo. */
    private ProcesoResponse publicar(Proceso proceso, Usuario autor) {
        Long empresaId = proceso.getEmpresa().getId();
        List<String> errores = diagnosticoDelModelo.errores(empresaId, procesoMapper.toResponse(proceso));
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException("El proceso no se puede publicar: " + errores.size()
                    + (errores.size() == 1 ? " error de diagnóstico." : " errores de diagnóstico."), errores);
        }
        int numero = versionService.siguienteNumero(empresaId, proceso.getId());
        Instantanea instantanea = instantaneaDelModelo.tomar(empresaId,
                procesoMapper.toResponse(proceso).publicadoComo(numero));
        if (instantanea.huella().equals(proceso.getHuellaPublicada())) {
            throw new ReglaNegocioException("No hay cambios desde la versión " + proceso.getVersionPublicada() + ".");
        }

        versionService.publicar(proceso, numero, autor.getId(), instantanea);
        proceso.setEstado(EstadoProceso.PUBLICADO);
        proceso.setVersionPublicada(numero);
        proceso.setHuellaPublicada(instantanea.huella());
        proceso = procesoRepository.saveAndFlush(proceso);

        historialCambioService.registrar(proceso, autor, "Versión " + numero + " publicada.");
        return procesoMapper.toResponse(proceso).conBorradorPendiente(false);
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
        ProcesoResponse respuesta = procesoMapper.toResponse(proceso);
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
                        .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."))
                : buscarActivo(empresaId, procesoId);
    }

    private Proceso buscarActivo(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
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
