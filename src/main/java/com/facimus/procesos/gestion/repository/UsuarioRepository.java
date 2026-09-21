package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Usuario;

public interface UsuarioRepository extends RepositorioTenant<Usuario> {

    Optional<Usuario> findByEmpresaIdAndEmail(Long empresaId, String email);

    boolean existsByEmpresaIdAndEmail(Long empresaId, String email);

    List<Usuario> findAllByEmpresaIdAndActivoTrue(Long empresaId);

    /**
     * Login-only: el formulario de inicio de sesion no conoce todavia el
     * empresaId (esa es justamente la informacion que la sesion va a fijar
     * despues de autenticar), asi que esta unica consulta rompe la regla
     * general de acotar por tenant.
     */
    Optional<Usuario> findByEmail(String email);
}
