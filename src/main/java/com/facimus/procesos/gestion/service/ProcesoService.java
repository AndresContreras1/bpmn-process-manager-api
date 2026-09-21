package com.facimus.procesos.gestion.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.ProcesoSpecifications;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/** HU-04 a HU-07: ciclo de vida y consulta de procesos. */
@Service
@RequiredArgsConstructor
public class ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PoolRepository poolRepository;
    private final HistorialCambioService historialCambioService;

    @Transactional
    public Proceso crear(Long empresaId, Long usuarioId, String nombre, String descripcion, String categoria) {
        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
            throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Usuario autor = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        LocalDateTime ahora = LocalDateTime.now();
        Proceso proceso = new Proceso();
        proceso.setEmpresa(empresa);
        proceso.setNombre(nombre);
        proceso.setDescripcion(descripcion);
        proceso.setCategoria(categoria);
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setActivo(true);
        proceso.setFechaCreacion(ahora);
        proceso.setFechaModificacion(ahora);
        proceso = procesoRepository.save(proceso);

        Pool poolInicial = new Pool();
        poolInicial.setEmpresa(empresa);
        poolInicial.setProceso(proceso);
        poolInicial.setNombre(empresa.getNombre());
        poolInicial.setTipoParticipante(TipoParticipante.EMPRESA);
        poolInicial.setCajaNegra(false);
        poolInicial.setOrden(0);
        poolRepository.save(poolInicial);

        historialCambioService.registrar(proceso, autor, "Proceso creado.");
        return proceso;
    }

    @Transactional
    public Proceso editarDatos(Long empresaId, Long procesoId, Long usuarioId, String nombre, String descripcion,
            String categoria) {
        Proceso proceso = obtener(empresaId, procesoId);
        Usuario autor = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        if (!proceso.getNombre().equalsIgnoreCase(nombre)
                && procesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(empresaId, nombre)) {
            throw new ReglaNegocioException("Ya existe un proceso activo con el nombre \"" + nombre + "\" en esta empresa.");
        }

        proceso.setNombre(nombre);
        proceso.setDescripcion(descripcion);
        proceso.setCategoria(categoria);
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso editado.");
        return proceso;
    }

    @Transactional
    public Proceso publicar(Long empresaId, Long procesoId, Long usuarioId) {
        return cambiarEstado(empresaId, procesoId, usuarioId, EstadoProceso.PUBLICADO);
    }

    @Transactional
    public Proceso cambiarEstado(Long empresaId, Long procesoId, Long usuarioId, EstadoProceso nuevoEstado) {
        Proceso proceso = obtener(empresaId, procesoId);
        if (proceso.getEstado() == nuevoEstado) {
            return proceso;
        }
        if (proceso.getEstado() == EstadoProceso.PUBLICADO && nuevoEstado == EstadoProceso.BORRADOR) {
            throw new ReglaNegocioException("Un proceso publicado no puede volver a borrador.");
        }
        Usuario autor = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        proceso.setEstado(nuevoEstado);
        proceso.setFechaModificacion(LocalDateTime.now());
        proceso = procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor,
                nuevoEstado == EstadoProceso.PUBLICADO ? "Proceso publicado." : "Estado del proceso actualizado.");
        return proceso;
    }

    @Transactional
    public void eliminarLogico(Long empresaId, Long procesoId, Long usuarioId) {
        Proceso proceso = obtener(empresaId, procesoId);
        Usuario autor = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        proceso.setActivo(false);
        proceso.setFechaModificacion(LocalDateTime.now());
        procesoRepository.save(proceso);

        historialCambioService.registrar(proceso, autor, "Proceso eliminado (baja logica).");
    }

    public Page<Proceso> buscar(Long empresaId, String nombre, EstadoProceso estado, String categoria,
            Pageable pageable) {
        return procesoRepository.findAll(ProcesoSpecifications.conFiltros(empresaId, nombre, estado, categoria),
                pageable);
    }

    public Proceso obtener(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }
}
