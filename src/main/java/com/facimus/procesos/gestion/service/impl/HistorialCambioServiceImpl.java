package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.HistorialCambioResponse;
import com.facimus.procesos.gestion.mapper.HistorialCambioMapper;
import com.facimus.procesos.gestion.model.HistorialCambio;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.HistorialCambioRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistorialCambioServiceImpl implements HistorialCambioService {

    private final HistorialCambioRepository historialCambioRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioMapper historialCambioMapper;

    @Override
    @Transactional
    public void registrar(Proceso proceso, Usuario autor, String descripcion) {
        historialCambioRepository.save(HistorialCambio.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .recursoTipo(RecursoDeHistorial.PROCESO)
                .recursoId(proceso.getId())
                .autor(autor)
                .fechaCambio(LocalDateTime.now())
                .descripcionCambio(descripcion)
                .build());
    }

    /** La empresa sale del autor: quien anota un cambio de la tienda siempre es alguien de esa tienda. */
    @Override
    @Transactional
    public void registrarDeTienda(Long empresaId, Long usuarioId, RecursoDeHistorial recurso, Long recursoId,
            String descripcion) {
        Usuario autor = autor(empresaId, usuarioId);
        historialCambioRepository.save(HistorialCambio.builder()
                .empresa(autor.getEmpresa())
                .recursoTipo(recurso)
                .recursoId(recursoId)
                .autor(autor)
                .fechaCambio(LocalDateTime.now())
                .descripcionCambio(descripcion)
                .build());
    }

    @Override
    @Transactional
    public void registrar(Long empresaId, Long usuarioId, Proceso proceso, String descripcion) {
        registrar(proceso, autor(empresaId, usuarioId), descripcion);
    }

    @Override
    public List<HistorialCambioResponse> listarPorProceso(Long empresaId, Long procesoId) {
        return historialCambioMapper.toResponses(
                historialCambioRepository.findAllByProcesoIdAndEmpresaIdOrderByFechaCambioDescIdDesc(procesoId,
                        empresaId));
    }

    @Override
    public PageResponse<HistorialCambioResponse> listarDeLaTienda(Long empresaId, Pageable pageable) {
        return PageResponse.from(historialCambioRepository
                .findAllByEmpresaIdOrderByFechaCambioDescIdDesc(empresaId, pageable)
                .map(historialCambioMapper::toResponse));
    }

    private Usuario autor(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
