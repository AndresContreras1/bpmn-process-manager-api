package com.facimus.procesos.modelado.service.impl;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.PoolResponse;
import com.facimus.procesos.modelado.mapper.PoolMapper;
import com.facimus.procesos.modelado.model.Integracion;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.model.TipoParticipante;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.LaneRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.PoolService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PoolServiceImpl implements PoolService {

    private final HistorialCambioService historialCambioService;
    private final PoolRepository poolRepository;
    private final ProcesoRepository procesoRepository;
    private final LaneRepository laneRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final MensajeRepository mensajeRepository;
    private final CorrelacionRepository correlacionRepository;
    private final PoolMapper poolMapper;

    @Override
    @Transactional
    public PoolResponse crear(Long empresaId, Long usuarioId, Long procesoId, String nombre,
            TipoParticipante tipoParticipante, boolean cajaNegra, Integracion integracion) {
        Proceso proceso = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        int orden = poolRepository.siguienteOrden(procesoId, empresaId);

        Pool pool = poolRepository.save(Pool.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .nombre(nombre)
                .tipoParticipante(tipoParticipante)
                .cajaNegra(cajaNegra)
                .integracion(ninguna(integracion))
                .orden(orden)
                .build());
        historialCambioService.registrar(empresaId, usuarioId, proceso, "Pool \"" + nombre + "\" agregado.");
        return poolMapper.toResponse(pool);
    }

    @Override
    @Transactional
    public PoolResponse editar(Long empresaId, Long usuarioId, Long poolId, String nombre,
            TipoParticipante tipoParticipante, boolean cajaNegra, Integracion integracion, Long version) {
        Pool pool = buscar(empresaId, poolId);
        pool.verificarVersion(version);
        // R-33: una caja negra no se modela por dentro, asi que las lanes que ya tiene contradicen la marca.
        if (cajaNegra && laneRepository.existsByPoolIdAndEmpresaId(poolId, empresaId)) {
            throw new ReglaNegocioException(ReglasDePools.CAJA_NEGRA_SIN_LANES);
        }
        pool.setCajaNegra(cajaNegra);
        pool.setNombre(nombre);
        pool.setTipoParticipante(tipoParticipante);
        pool.setIntegracion(ninguna(integracion));
        historialCambioService.registrar(empresaId, usuarioId, pool.getProceso(), "Pool \"" + nombre + "\" editado.");
        return poolMapper.toResponse(poolRepository.saveAndFlush(pool));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long poolId) {
        Pool pool = buscar(empresaId, poolId);
        if (nodoFlujoRepository.existsByLane_Pool_IdAndEmpresaId(poolId, empresaId)) {
            throw new ReglaNegocioException("El pool \"" + pool.getNombre() + "\" tiene lanes con actividades; no se puede eliminar.");
        }
        // Los mensajes que entran o salen del pool se van con el, y cada uno con su clave de correlacion.
        Stream.concat(mensajeRepository.findAllByPoolOrigenIdAndEmpresaId(poolId, empresaId).stream(),
                        mensajeRepository.findAllByPoolDestinoIdAndEmpresaId(poolId, empresaId).stream())
                .forEach(mensaje -> {
                    correlacionRepository.findByMensajeIdAndEmpresaId(mensaje.getId(), empresaId)
                            .ifPresent(correlacionRepository::delete);
                    mensajeRepository.delete(mensaje);
                });
        laneRepository.deleteAll(laneRepository.findAllByPoolIdAndEmpresaIdOrderByOrdenAsc(poolId, empresaId));
        poolRepository.delete(pool);
        historialCambioService.registrar(empresaId, usuarioId, pool.getProceso(),
                "Pool \"" + pool.getNombre() + "\" eliminado.");
    }

    @Override
    public List<PoolResponse> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return poolMapper.toResponses(poolRepository.findAllByProcesoIdAndEmpresaIdOrderByOrdenAsc(procesoId,
                empresaId));
    }

    @Override
    public PoolResponse obtener(Long empresaId, Long poolId) {
        return poolMapper.toResponse(buscar(empresaId, poolId));
    }

    /** Un pool sin socio declarado es NINGUNA: la columna no admite vacio. */
    private static Integracion ninguna(Integracion integracion) {
        return integracion == null ? Integracion.NINGUNA : integracion;
    }

    private Pool buscar(Long empresaId, Long poolId) {
        return poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool no encontrado."));
    }
}
