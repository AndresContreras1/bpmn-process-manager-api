package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.Usuario;

public interface UsuarioRepository extends RepositorioTenant<Usuario> {

    Optional<Usuario> findByEmpresaIdAndEmail(Long empresaId, String email);

    Page<Usuario> findAllByEmpresaIdAndActivoTrue(Long empresaId, Pageable pageable);

    /*
     * Las dos consultas por correo no se acotan por empresa a proposito: el login todavia no conoce el
     * empresaId, y por eso el correo es unico en todo el sistema (restriccion uk_usuarios_email).
     */

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email);
}
