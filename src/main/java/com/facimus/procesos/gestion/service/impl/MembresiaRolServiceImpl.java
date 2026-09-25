package com.facimus.procesos.gestion.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.dto.response.RolDeUsuarioResponse;
import com.facimus.procesos.gestion.model.MembresiaRol;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.MembresiaRolService;

import lombok.RequiredArgsConstructor;

/**
 * Quien pertenece a que rol de proceso. Se reemplaza la lista entera de una persona en vez de agregar y quitar de
 * uno en uno: asi la pantalla manda lo que ve y no hay forma de que dos peticiones se pisen a medias.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembresiaRolServiceImpl implements MembresiaRolService {

    private final MembresiaRolRepository membresiaRolRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final HistorialCambioService historialCambioService;

    @Override
    public List<RolDeUsuarioResponse> rolesDe(Long empresaId, Long usuarioId) {
        exigirUsuario(empresaId, usuarioId);
        return membresiaRolRepository.findAllByUsuarioIdAndEmpresaIdOrderByIdAsc(usuarioId, empresaId).stream()
                .map(MembresiaRol::getRolProceso)
                .map(MembresiaRolServiceImpl::comoRespuesta)
                .toList();
    }

    @Override
    @Transactional
    public List<RolDeUsuarioResponse> reemplazar(Long empresaId, Long autorId, Long usuarioId,
            List<Long> rolesProcesoIds) {
        Usuario usuario = exigirUsuario(empresaId, usuarioId);
        // El orden en que llegan se conserva y los repetidos se ignoran: pedir dos veces el mismo rol no es un error.
        Set<Long> pedidos = new LinkedHashSet<>(rolesProcesoIds);
        List<RolProceso> roles = pedidos.stream().map(rolId -> exigirRol(empresaId, rolId)).toList();

        membresiaRolRepository.borrarLasDe(empresaId, usuarioId);
        roles.forEach(rol -> membresiaRolRepository.save(MembresiaRol.builder()
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .rolProceso(rol)
                .build()));
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.USUARIO, usuarioId,
                descripcion(usuario, roles));
        return roles.stream().map(MembresiaRolServiceImpl::comoRespuesta).toList();
    }

    @Override
    public List<Long> idsDeLosRolesDe(Long empresaId, Long usuarioId) {
        return membresiaRolRepository.rolesDe(empresaId, usuarioId);
    }

    /** La linea del historial dice con que roles quedo la persona, que es lo que alguien querria comprobar. */
    private static String descripcion(Usuario usuario, List<RolProceso> roles) {
        if (roles.isEmpty()) {
            return "\"" + usuario.getNombre() + "\" se queda sin roles de proceso.";
        }
        return "\"" + usuario.getNombre() + "\" queda en los roles de proceso "
                + roles.stream().map(rol -> "\"" + rol.getNombre() + "\"").toList() + ".";
    }

    private static RolDeUsuarioResponse comoRespuesta(RolProceso rol) {
        return new RolDeUsuarioResponse(rol.getId(), rol.getNombre(), rol.getDescripcion());
    }

    private Usuario exigirUsuario(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    /** Un rol retirado no se reparte: quedaria en una bandeja que ya nadie modela. */
    private RolProceso exigirRol(Long empresaId, Long rolId) {
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
    }
}
