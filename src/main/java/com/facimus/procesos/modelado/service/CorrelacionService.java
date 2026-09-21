package com.facimus.procesos.modelado.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;

import lombok.RequiredArgsConstructor;

/** HU-28: correlacion (criterio que identifica a que caso concreto pertenece un mensaje). */
@Service
@RequiredArgsConstructor
public class CorrelacionService {

    private final CorrelacionRepository correlacionRepository;
    private final MensajeRepository mensajeRepository;

    @Transactional
    public Correlacion definir(Long empresaId, Long mensajeId, String criterio) {
        Mensaje mensaje = mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));

        Correlacion correlacion = correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId)
                .orElseGet(() -> {
                    Correlacion nueva = new Correlacion();
                    nueva.setEmpresa(mensaje.getEmpresa());
                    nueva.setMensaje(mensaje);
                    return nueva;
                });
        correlacion.setCriterio(criterio);
        return correlacionRepository.save(correlacion);
    }

    public Correlacion obtener(Long empresaId, Long mensajeId) {
        return correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Este mensaje no tiene correlacion definida."));
    }
}
