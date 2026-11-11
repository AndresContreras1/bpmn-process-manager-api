package com.facimus.procesos.gestion.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.ConcesionResponse;
import com.facimus.procesos.gestion.model.Concesion;
import com.facimus.procesos.gestion.model.SujetoConcesion;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.ConcesionRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.ConcesionService;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Recurso;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcesionServiceImpl implements ConcesionService {

    private final ConcesionRepository concesionRepository;
    private final CarpetaRepository carpetaRepository;
    private final ProcesoRepository procesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final EmpresaRepository empresaRepository;
    private final Permisos permisos;
    private final Auditoria auditoria;

    @Override
    public List<ConcesionResponse> listar(Long empresaId, Recurso recurso) {
        comprobarRecurso(empresaId, recurso);
        List<Concesion> concesiones = recurso.tipo() == Recurso.Tipo.PROCESO
                ? concesionRepository.findAllByEmpresaIdAndProcesoIdOrderByIdAsc(empresaId, recurso.id())
                : concesionRepository.findAllByEmpresaIdAndCarpetaIdOrderByIdAsc(empresaId, recurso.id());
        return concesiones.stream().map(ConcesionServiceImpl::respuesta).toList();
    }

    @Override
    @Transactional
    public ConcesionResponse conceder(Actor actor, Recurso recurso, Accion accion, SujetoConcesion sujeto,
            Long usuarioId, Long rolProcesoId) {
        Long empresaId = actor.empresaId();
        comprobarRecurso(empresaId, recurso);
        comprobarSujeto(empresaId, sujeto, usuarioId, rolProcesoId);
        boolean esProceso = recurso.tipo() == Recurso.Tipo.PROCESO;
        boolean repetida = (esProceso
                ? concesionRepository.findAllByEmpresaIdAndProcesoIdOrderByIdAsc(empresaId, recurso.id())
                : concesionRepository.findAllByEmpresaIdAndCarpetaIdOrderByIdAsc(empresaId, recurso.id()))
                .stream().anyMatch(una -> una.getAccion() == accion && una.getSujeto() == sujeto
                        && java.util.Objects.equals(una.getUsuarioId(), usuarioId)
                        && java.util.Objects.equals(una.getRolProcesoId(), rolProcesoId));
        if (repetida) {
            throw new ReglaNegocioException("Esa concesión ya existe.");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Concesion concesion = concesionRepository.save(Concesion.builder().empresa(empresa)
                .procesoId(esProceso ? recurso.id() : null).carpetaId(esProceso ? null : recurso.id()).sujeto(sujeto)
                .usuarioId(usuarioId).rolProcesoId(rolProcesoId).accion(accion).build());
        auditoria.registrar(empresaId, actor.usuarioId(), "CONCESION_CREADA", recurso.tipo().name(), recurso.id(),
                Map.of("accion", accion.name(), "sujeto", sujeto.name()));
        return respuesta(concesion);
    }

    @Override
    @Transactional
    public void retirar(Actor actor, Long concesionId) {
        Concesion concesion = concesionRepository.findByIdAndEmpresaId(concesionId, actor.empresaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Concesión no encontrada."));
        Recurso recurso = concesion.getProcesoId() != null ? Recurso.proceso(concesion.getProcesoId())
                : Recurso.carpeta(concesion.getCarpetaId());
        permisos.exigir(actor, Accion.ADMINISTRAR, recurso);
        concesionRepository.delete(concesion);
        auditoria.registrar(actor.empresaId(), actor.usuarioId(), "CONCESION_RETIRADA", recurso.tipo().name(),
                recurso.id(), Map.of("accion", concesion.getAccion().name(), "sujeto", concesion.getSujeto().name()));
    }

    private void comprobarRecurso(Long empresaId, Recurso recurso) {
        if (recurso.tipo() == Recurso.Tipo.PROCESO) {
            if (procesoRepository.findByIdAndEmpresaIdAndActivoTrue(recurso.id(), empresaId).isEmpty()) {
                throw new RecursoNoEncontradoException("Proceso no encontrado.");
            }
        } else if (!carpetaRepository.existsByIdAndEmpresaId(recurso.id(), empresaId)) {
            throw new RecursoNoEncontradoException("Carpeta no encontrada.");
        }
    }

    private void comprobarSujeto(Long empresaId, SujetoConcesion sujeto, Long usuarioId, Long rolProcesoId) {
        boolean completo = switch (sujeto) {
            case PERSONA -> usuarioId != null && rolProcesoId == null;
            case ROL -> rolProcesoId != null && usuarioId == null;
            case ORGANIZACION -> usuarioId == null && rolProcesoId == null;
        };
        if (!completo) {
            throw new SolicitudInvalidaException("El sujeto no coincide con la persona o el rol indicados.");
        }
        if (usuarioId != null && !usuarioRepository.existsByIdAndEmpresaId(usuarioId, empresaId)) {
            throw new RecursoNoEncontradoException("Usuario no encontrado.");
        }
        if (rolProcesoId != null && rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolProcesoId, empresaId)
                .isEmpty()) {
            throw new RecursoNoEncontradoException("Rol de proceso no encontrado.");
        }
    }

    private static ConcesionResponse respuesta(Concesion concesion) {
        return new ConcesionResponse(concesion.getId(), concesion.getCarpetaId(), concesion.getProcesoId(),
                concesion.getSujeto(), concesion.getUsuarioId(), concesion.getRolProcesoId(), concesion.getAccion());
    }
}
