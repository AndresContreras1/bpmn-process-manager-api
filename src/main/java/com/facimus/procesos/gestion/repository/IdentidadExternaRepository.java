package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.IdentidadExterna;

public interface IdentidadExternaRepository extends RepositorioTenant<IdentidadExterna> {

    /** Como el login, entrar con un proveedor todavia no conoce la tienda: el par (proveedor, sub) dice de quien es. */
    @EntityGraph(attributePaths = {"usuario", "empresa"})
    Optional<IdentidadExterna> findByProveedorAndSujeto(String proveedor, String sujeto);

    List<IdentidadExterna> findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(Long usuarioId, Long empresaId);

    Optional<IdentidadExterna> findByIdAndUsuarioIdAndEmpresaId(Long id, Long usuarioId, Long empresaId);

    @Modifying
    @Query("delete from IdentidadExterna i where i.empresa.id = :empresaId and i.usuario.id = :usuarioId")
    int borrarLasDe(Long empresaId, Long usuarioId);
}
