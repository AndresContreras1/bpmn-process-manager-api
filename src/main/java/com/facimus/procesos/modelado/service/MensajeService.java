package com.facimus.procesos.modelado.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.modelado.model.Correlacion;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;

import lombok.RequiredArgsConstructor;

/** HU-25 a HU-27: mensajes (comunicacion entre pools: throw / catch). */
@Service
@RequiredArgsConstructor
public class MensajeService {

    private final MensajeRepository mensajeRepository;
    private final PoolRepository poolRepository;
    private final ProcesoRepository procesoRepository;
    private final CorrelacionRepository correlacionRepository;

    @Transactional
    public Mensaje crear(Long empresaId, Long procesoId, String nombre, String contenido, Long poolOrigenId,
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

        Mensaje mensaje = new Mensaje();
        mensaje.setEmpresa(proceso.getEmpresa());
        mensaje.setProceso(proceso);
        mensaje.setNombre(nombre);
        mensaje.setContenido(contenido);
        mensaje.setPoolOrigen(poolOrigen);
        mensaje.setPoolDestino(poolDestino);
        return mensajeRepository.save(mensaje);
    }

    @Transactional
    public Mensaje editar(Long empresaId, Long mensajeId, String nombre, String contenido) {
        Mensaje mensaje = obtener(empresaId, mensajeId);
        mensaje.setNombre(nombre);
        mensaje.setContenido(contenido);
        return mensajeRepository.save(mensaje);
    }

    @Transactional
    public void eliminar(Long empresaId, Long mensajeId) {
        Mensaje mensaje = obtener(empresaId, mensajeId);
        Optional<Correlacion> correlacion = correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId, empresaId);
        correlacion.ifPresent(correlacionRepository::delete);
        mensajeRepository.delete(mensaje);
    }

    public List<Mensaje> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaId(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return mensajeRepository.findAllByProcesoIdAndEmpresaId(procesoId, empresaId);
    }

    public Mensaje obtener(Long empresaId, Long mensajeId) {
        return mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));
    }
}
