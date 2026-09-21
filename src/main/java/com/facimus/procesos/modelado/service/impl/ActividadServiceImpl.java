package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.modelado.dto.response.ActividadResponse;
import com.facimus.procesos.modelado.mapper.ActividadMapper;
import com.facimus.procesos.modelado.model.Actividad;
import com.facimus.procesos.modelado.model.Lane;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.ArcoRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.service.ActividadService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActividadServiceImpl implements ActividadService {

    private final ActividadRepository actividadRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final LaneRepository laneRepository;
    private final ArcoRepository arcoRepository;
    private final ActividadMapper actividadMapper;

    @Override
    @Transactional
    public ActividadResponse crear(Long empresaId, Long laneId, String nombre, String descripcion, int posX,
            int posY) {
        Lane lane = laneRepository.findByIdAndEmpresaId(laneId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lane no encontrada."));
        Long procesoId = lane.getPool().getProceso().getId();
        if (nodoFlujoRepository.existsByNombreIgnoreCaseAndLane_Pool_ProcesoIdAndEmpresaId(nombre, procesoId, empresaId)) {
            throw new ReglaNegocioException("Ya existe un nodo con el nombre \"" + nombre + "\" en este proceso.");
        }

        Actividad actividad = actividadRepository.save(Actividad.builder()
                .empresa(lane.getEmpresa())
                .lane(lane)
                .nombre(nombre)
                .descripcion(descripcion)
                .posicionX(posX)
                .posicionY(posY)
                .build());
        return actividadMapper.toResponse(actividad);
    }

    @Override
    @Transactional
    public ActividadResponse editar(Long empresaId, Long actividadId, String nombre, String descripcion, int posX,
            int posY) {
        Actividad actividad = buscar(empresaId, actividadId);
        actividad.setNombre(nombre);
        actividad.setDescripcion(descripcion);
        actividad.setPosicionX(posX);
        actividad.setPosicionY(posY);
        return actividadMapper.toResponse(actividadRepository.save(actividad));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long actividadId) {
        Actividad actividad = buscar(empresaId, actividadId);
        arcoRepository.deleteAll(arcoRepository.findAllByOrigenIdAndEmpresaId(actividadId, empresaId));
        arcoRepository.deleteAll(arcoRepository.findAllByDestinoIdAndEmpresaId(actividadId, empresaId));
        actividadRepository.delete(actividad);
    }

    @Override
    public ActividadResponse obtener(Long empresaId, Long actividadId) {
        return actividadMapper.toResponse(buscar(empresaId, actividadId));
    }

    @Override
    public List<ActividadResponse> listarPorLane(Long empresaId, Long laneId) {
        if (!laneRepository.existsByIdAndEmpresaId(laneId, empresaId)) {
            throw new RecursoNoEncontradoException("Lane no encontrada.");
        }
        return actividadMapper.toResponses(actividadRepository.findAllByLaneIdAndEmpresaId(laneId, empresaId));
    }

    private Actividad buscar(Long empresaId, Long actividadId) {
        return actividadRepository.findByIdAndEmpresaId(actividadId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad no encontrada."));
    }
}
