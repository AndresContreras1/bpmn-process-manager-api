package com.facimus.procesos.gestion.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.api.PageResponse;
import com.facimus.procesos.gestion.dto.response.EmpresaInvitadaResponse;
import com.facimus.procesos.gestion.dto.response.ProcesoRecibidoResponse;
import com.facimus.procesos.gestion.mapper.ProcesoCompartidoMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.ProcesoCompartido;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoCompartidoRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoCompartidoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcesoCompartidoServiceImpl implements ProcesoCompartidoService {

    private final ProcesoRepository procesoRepository;
    private final ProcesoCompartidoRepository procesoCompartidoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioService historialCambioService;
    private final ProcesoCompartidoMapper procesoCompartidoMapper;

    @Override
    @Transactional
    public EmpresaInvitadaResponse compartir(Long empresaId, Long procesoId, Long usuarioId, String nit) {
        Proceso proceso = propio(empresaId, procesoId);
        Empresa invitada = empresaRepository.findByNit(nit)
                .orElseThrow(() -> new RecursoNoEncontradoException("No hay una empresa registrada con ese NIT."));
        if (invitada.getId().equals(empresaId)) {
            throw new ReglaNegocioException("Un proceso no se comparte con su propia empresa.");
        }
        if (procesoCompartidoRepository.existsByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(procesoId, invitada.getId(),
                empresaId)) {
            throw new ReglaNegocioException("El proceso ya está compartido con " + invitada.getNombre() + ".");
        }
        ProcesoCompartido comparticion = procesoCompartidoRepository.save(ProcesoCompartido.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .empresaInvitada(invitada)
                .fechaCompartido(LocalDateTime.now())
                .build());
        // D2: la invitada ve la version publicada, no el borrador; si no hay ninguna, todavia no ve nada.
        historialCambioService.registrar(proceso, autor(empresaId, usuarioId),
                "Proceso compartido en solo lectura con " + invitada.getNombre() + ". " + queVera(proceso));
        return procesoCompartidoMapper.toInvitada(comparticion);
    }

    private static String queVera(Proceso proceso) {
        return proceso.getVersionPublicada() == null
                ? "No verá nada hasta que el proceso se publique."
                : "Verá la versión " + proceso.getVersionPublicada() + ".";
    }

    @Override
    public List<EmpresaInvitadaResponse> listarInvitadas(Long empresaId, Long procesoId) {
        propio(empresaId, procesoId);
        return procesoCompartidoRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId).stream()
                .map(procesoCompartidoMapper::toInvitada)
                .toList();
    }

    @Override
    public EmpresaInvitadaResponse obtenerInvitada(Long empresaId, Long procesoId, Long empresaInvitadaId) {
        return procesoCompartidoMapper.toInvitada(comparticion(empresaId, procesoId, empresaInvitadaId));
    }

    @Override
    @Transactional
    public void dejarDeCompartir(Long empresaId, Long procesoId, Long empresaInvitadaId, Long usuarioId) {
        ProcesoCompartido comparticion = comparticion(empresaId, procesoId, empresaInvitadaId);
        procesoCompartidoRepository.delete(comparticion);
        // El permiso se borra; la bitacora del proceso conserva cuando se dio y cuando se quito.
        historialCambioService.registrar(comparticion.getProceso(), autor(empresaId, usuarioId),
                "Se dejó de compartir el proceso con " + comparticion.getEmpresaInvitada().getNombre() + ".");
    }

    @Override
    public PageResponse<ProcesoRecibidoResponse> buscarRecibidos(Long empresaId, Pageable pageable) {
        return PageResponse.from(procesoRepository.compartidosCon(empresaId, pageable)
                .map(procesoCompartidoMapper::toRecibido));
    }

    /** La puerta de escritura: el proceso activo de la propia empresa. El de otra, aunque se lo compartan, no existe. */
    private Proceso propio(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private ProcesoCompartido comparticion(Long empresaId, Long procesoId, Long empresaInvitadaId) {
        propio(empresaId, procesoId);
        return procesoCompartidoRepository
                .findByProcesoIdAndEmpresaInvitadaIdAndEmpresaId(procesoId, empresaInvitadaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proceso no está compartido con esa empresa."));
    }

    private Usuario autor(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
