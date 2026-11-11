package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.ArtefactoResponse;
import com.facimus.procesos.modelado.mapper.ArtefactoMapper;
import com.facimus.procesos.modelado.model.Artefacto;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoArtefacto;
import com.facimus.procesos.modelado.repository.ArtefactoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.ArtefactoService;
import com.facimus.procesos.modelado.service.DatosDeArtefacto;

import lombok.RequiredArgsConstructor;

/**
 * PR 55: los artefactos describen el proceso sin cambiar su flujo. Un dato vive en el proceso de un pool, asi que
 * un objeto de datos o un almacen va en un pool modelado por dentro; una anotacion o un grupo puede ir suelto.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtefactoServiceImpl implements ArtefactoService {

    private final ArtefactoRepository artefactoRepository;
    private final ProcesoRepository procesoRepository;
    private final PoolRepository poolRepository;
    private final LaneRepository laneRepository;
    private final ArtefactoMapper artefactoMapper;
    private final HistorialCambioService historialCambioService;
    private final ElementosPegados pegados;

    @Override
    @Transactional
    public ArtefactoResponse crear(Long empresaId, Long usuarioId, Long procesoId, DatosDeArtefacto datos) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        Artefacto artefacto = Artefacto.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .tipo(datos.tipo())
                .build();
        ubicar(empresaId, artefacto, datos);
        artefacto = artefactoRepository.save(artefacto);
        historialCambioService.registrar(empresaId, usuarioId, proceso, nombre(artefacto) + " agregado.");
        return artefactoMapper.toResponse(artefacto);
    }

    @Override
    @Transactional
    public ArtefactoResponse editar(Long empresaId, Long usuarioId, Long artefactoId, DatosDeArtefacto datos,
            Long version) {
        Artefacto artefacto = buscar(empresaId, artefactoId);
        artefacto.verificarVersion(version);
        ubicar(empresaId, artefacto, new DatosDeArtefacto(artefacto.getTipo(), datos.texto(), datos.poolId(),
                datos.laneId(), datos.posicionX(), datos.posicionY(), datos.ancho(), datos.alto()));
        historialCambioService.registrar(empresaId, usuarioId, artefacto.getProceso(), nombre(artefacto)
                + " editado.");
        return artefactoMapper.toResponse(artefactoRepository.saveAndFlush(artefacto));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long artefactoId) {
        Artefacto artefacto = buscar(empresaId, artefactoId);
        pegados.soltarAsociaciones(empresaId, artefacto.getProceso().getId(), artefacto.getBpmnId());
        artefactoRepository.delete(artefacto);
        historialCambioService.registrar(empresaId, usuarioId, artefacto.getProceso(), nombre(artefacto)
                + " eliminado.");
    }

    @Override
    public ArtefactoResponse obtener(Long empresaId, Long artefactoId) {
        return artefactoMapper.toResponse(buscar(empresaId, artefactoId));
    }

    @Override
    public List<ArtefactoResponse> listar(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return artefactoMapper.toResponses(artefactoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId,
                empresaId));
    }

    /**
     * Donde va y cuanto ocupa. La lane manda: si llega, el pool es el suyo, y uno distinto es un error. Un dato
     * necesita el proceso de un pool modelado por dentro, porque es ahi donde BPMN lo declara.
     */
    private void ubicar(Long empresaId, Artefacto artefacto, DatosDeArtefacto datos) {
        Long procesoId = artefacto.getProceso().getId();
        Lane lane = null;
        Pool pool = null;
        if (datos.laneId() != null) {
            lane = laneRepository.findByIdAndEmpresaId(datos.laneId(), empresaId)
                    .filter(candidata -> candidata.getPool().getProceso().getId().equals(procesoId))
                    .orElseThrow(() -> new ReglaNegocioException("La lane no es de este proceso."));
            pool = lane.getPool();
            if (datos.poolId() != null && !datos.poolId().equals(pool.getId())) {
                throw new ReglaNegocioException("La lane no es del pool indicado.");
            }
        } else if (datos.poolId() != null) {
            pool = poolRepository.findByIdAndEmpresaId(datos.poolId(), empresaId)
                    .filter(candidato -> candidato.getProceso().getId().equals(procesoId))
                    .orElseThrow(() -> new ReglaNegocioException("El pool no es de este proceso."));
        }
        boolean esDato = datos.tipo() == TipoArtefacto.OBJETO_DATOS || datos.tipo() == TipoArtefacto.ALMACEN_DATOS;
        if (esDato && (pool == null || pool.isCajaNegra())) {
            throw new ReglaNegocioException("Un objeto de datos o un almacen va en un pool modelado por dentro: es "
                    + "un dato de su proceso.");
        }
        int[] porDefecto = Artefacto.tamanoPorDefecto(datos.tipo());
        artefacto.setPool(pool);
        artefacto.setLane(lane);
        artefacto.setTexto(datos.texto() == null || datos.texto().isBlank() ? null : datos.texto().strip());
        artefacto.setPosicionX(datos.posicionX());
        artefacto.setPosicionY(datos.posicionY());
        artefacto.setAncho(datos.ancho() == null ? porDefecto[0] : datos.ancho());
        artefacto.setAlto(datos.alto() == null ? porDefecto[1] : datos.alto());
    }

    private Artefacto buscar(Long empresaId, Long artefactoId) {
        return artefactoRepository.findByIdAndEmpresaId(artefactoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Artefacto no encontrado."));
    }

    private static String nombre(Artefacto artefacto) {
        String clase = switch (artefacto.getTipo()) {
            case ANOTACION -> "Anotacion";
            case GRUPO -> "Grupo";
            case OBJETO_DATOS -> "Objeto de datos";
            case ALMACEN_DATOS -> "Almacen de datos";
        };
        return artefacto.getTexto() == null ? clase : clase + " \"" + recortado(artefacto.getTexto()) + "\"";
    }

    private static String recortado(String texto) {
        return texto.length() <= 60 ? texto : texto.substring(0, 59) + "...";
    }
}
