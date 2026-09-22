package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.MensajeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MensajeServiceImpl implements MensajeService {

    private final MensajeRepository mensajeRepository;
    private final PoolRepository poolRepository;
    private final ProcesoRepository procesoRepository;
    private final CorrelacionRepository correlacionRepository;
    private final MensajeMapper mensajeMapper;

    @Override
    @Transactional
    public MensajeResponse crear(Long empresaId, Long procesoId, String nombre, String contenido, Long poolOrigenId,
            Long poolDestinoId) {
        if (poolOrigenId.equals(poolDestinoId)) {
            throw new ReglaNegocioException("Un mensaje debe conectar dos pools diferentes.");
        }
        Proceso proceso = procesoRepository.findByIdAndEmpresaId(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        Pool poolOrigen = poolRepository.findByIdAndEmpresaId(poolOrigenId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool de origen no encontrado."));
        Pool poolDestino = poolRepository.findByIdAndEmpresaId(poolDestinoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pool de destino no encontrado."));

        Mensaje mensaje = mensajeRepository.save(Mensaje.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .nombre(nombre)
                .contenido(contenido)
                .poolOrigen(poolOrigen)
                .poolDestino(poolDestino)
                .build());
        return mensajeMapper.toResponse(mensaje);
    }

    @Override
    @Transactional
    public MensajeResponse editar(Long empresaId, Long mensajeId, String nombre, String contenido, Long version) {
        Mensaje mensaje = buscar(empresaId, mensajeId);
        mensaje.verificarVersion(version);
        mensaje.setNombre(nombre);
        mensaje.setContenido(contenido);
        return mensajeMapper.toResponse(mensajeRepository.saveAndFlush(mensaje));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long mensajeId) {
        Mensaje mensaje = buscar(empresaId, mensajeId);
        correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId).ifPresent(correlacionRepository::delete);
        mensajeRepository.delete(mensaje);
    }

    @Override
    public List<MensajeResponse> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaId(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return mensajeMapper.toResponses(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId));
    }

    @Override
    public MensajeResponse obtener(Long empresaId, Long mensajeId) {
        return mensajeMapper.toResponse(buscar(empresaId, mensajeId));
    }

    private Mensaje buscar(Long empresaId, Long mensajeId) {
        return mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));
    }
}
