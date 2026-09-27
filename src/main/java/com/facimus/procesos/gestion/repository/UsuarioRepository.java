package com.facimus.procesos.gestion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;

/** El listado con filtros va por UsuarioSpecifications; lo demas son consultas con nombre propio. */
public interface UsuarioRepository extends RepositorioTenant<Usuario>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByEmpresaIdAndEmail(Long empresaId, String email);

    long countByEmpresaIdAndRolAccesoAndActivoTrue(Long empresaId, RolAcceso rolAcceso);

    /*
     * Las dos consultas por correo no se acotan por empresa a proposito: el login todavia no conoce el
     * empresaId, y por eso el correo es unico en todo el sistema (restriccion uk_usuarios_email).
     */

    boolean existsByEmail(String email);

    Optional<Usuario> findByEmail(String email);
}
