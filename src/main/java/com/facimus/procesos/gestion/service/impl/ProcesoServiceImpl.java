package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoDetalleResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoResponse;
import com.facimus.procesos.gestion.mapper.ProcesoMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.ProcesoSpecifications;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcesoServiceImpl implements ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoolRepository poolRepository;
    private final HistorialCambioService historialCambioService;
    private final ProcesoMapper procesoMapper;

    @Override
    public PageResponse<ProcesoResponse> buscar(Long empresaId, String nombre, EstadoProceso estado,
            String categoria, Pageable pageable) {
        return PageResponse.from(procesoRepository
                .findAll(ProcesoSpecifications.conFiltros(empresaId, nombre, estado, categoria), pageable)
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

        LocalDateTime ahora = LocalDateTime.now();
        Proceso proceso = procesoRepository.save(Proceso.builder()
                .empresa(empresa)
                .nombre(nombre)
                .descripcion(descripcion)
                .categoria(categoria)
                .fechaCreacion(ahora)
                .fechaModificacion(ahora)
                .build());

        poolRepository.save(Pool.builder()
                .empresa(empresa)
                .proceso(proceso)
                .nombre(empresa.getNombre())
                .tipoParticipante(TipoParticipante.EMPRESA)
                .orden(0)
                .build());

        historialCambioService.registrar(proceso, autor, "Proceso creado.");
        return procesoMapper.toResponse(proceso);
    }

    @Override
    public ProcesoResponse obtener(Long empresaId, Long procesoId) {
        return procesoMapper.toResponse(buscarActivo(empresaId, procesoId));
    }

    @Override
    public ProcesoDetalleResponse obtenerDetalle(Long empresaId, Long procesoId) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        return new ProcesoDetalleResponse(procesoMapper.toResponse(proceso),
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
            String descripcion, String categoria) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        Usuario autor = autor(empresaId, usuarioId);
        if (!proceso.getNombre().equalsIgnoreCase(nombre)) {
            validarNombreLibre(empresaId, nombre);
        }

        proceso.setNombre(nombre);
        proceso.setDescripcion(descripcion);
        proceso.setCategoria(categoria);
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso editado.");
        return procesoMapper.toResponse(proceso);
    }

    @Override
    @Transactional
    public ProcesoResponse cambiarEstado(Long empresaId, Long procesoId, Long usuarioId,
            EstadoProceso nuevoEstado) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        if (proceso.getEstado() == nuevoEstado) {
            return procesoMapper.toResponse(proceso);
        }
        if (proceso.getEstado() == EstadoProceso.PUBLICADO && nuevoEstado == EstadoProceso.BORRADOR) {
            throw new ReglaNegocioException("Un proceso publicado no puede volver a borrador.");
        }
        Usuario autor = autor(empresaId, usuarioId);

        proceso.setEstado(nuevoEstado);
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor,
                nuevoEstado == EstadoProceso.PUBLICADO ? "Proceso publicado." : "Estado del proceso actualizado.");
        return procesoMapper.toResponse(proceso);
    }

    @Override
    @Transactional
    public void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId) {
        Proceso proceso = buscarActivo(empresaId, procesoId);
        Usuario autor = autor(empresaId, usuarioId);

        proceso.setActivo(false);
        proceso.setFechaModificacion(LocalDateTime.now());
        procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso eliminado (baja logica).");
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
