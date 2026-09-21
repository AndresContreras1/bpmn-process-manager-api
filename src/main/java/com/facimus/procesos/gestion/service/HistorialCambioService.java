package com.facimus.procesos.gestion.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.HistorialCambioRepository;

import lombok.RequiredArgsConstructor;

/** Bitacora de trazabilidad: solo INSERT, nunca se edita ni se elimina. */
@Service
@RequiredArgsConstructor
public class HistorialCambioService {

    private final HistorialCambioRepository historialCambioRepository;

    @Transactional
    public void registrar(Proceso proceso, Usuario autor, String descripcion) {
        HistorialCambio historial = new HistorialCambio();
        historial.setEmpresa(proceso.getEmpresa());
        historial.setProceso(proceso);
        historial.setAutor(autor);
        historial.setFechaCambio(LocalDateTime.now());
        historial.setDescripcionCambio(descripcion);
        historialCambioRepository.save(historial);
    }

    public List<HistorialCambio> listarPorProceso(Long empresaId, Long procesoId) {
        return historialCambioRepository.findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDesc(procesoId, empresaId);
    }
}
