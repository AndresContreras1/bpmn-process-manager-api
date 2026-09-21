package com.facimus.procesos.modelado.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.dto.response.CorrelacionResponse;
import com.facimus.procesos.modelado.mapper.CorrelacionMapper;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.service.CorrelacionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorrelacionServiceImpl implements CorrelacionService {

    private final CorrelacionRepository correlacionRepository;
    private final MensajeRepository mensajeRepository;
    private final CorrelacionMapper correlacionMapper;

    /** Crea el criterio de correlacion del mensaje, o lo reemplaza si ya tenia uno. */
    @Override
    @Transactional
    public CorrelacionResponse definir(Long empresaId, Long mensajeId, String criterio) {
        Mensaje mensaje = mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));

        Correlacion correlacion = correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId)
                .orElseGet(() -> Correlacion.builder()
                        .empresa(mensaje.getEmpresa())
                        .mensaje(mensaje)
                        .build());
        correlacion.setCriterio(criterio);
        return correlacionMapper.toResponse(correlacionRepository.save(correlacion));
    }

    @Override
    public CorrelacionResponse obtener(Long empresaId, Long mensajeId) {
        return correlacionMapper.toResponse(correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Este mensaje no tiene correlacion definida.")));
    }
}
