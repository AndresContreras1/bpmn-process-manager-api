package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Usuario;

public interface UsuarioRepository extends RepositorioTenant<Usuario> {

    Optional<Usuario> findByEmpresaIdAndEmail(Long empresaId, String email);

    List<Usuario> findAllByEmpresaIdAndActivoTrue(Long empresaId);

    /*
     * Las dos consultas por correo no se acotan por empresa a proposito: el login todavia no conoce el
     * empresaId, y por eso el correo es unico en todo el sistema (restriccion uk_usuarios_email).
     */

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email);
}
