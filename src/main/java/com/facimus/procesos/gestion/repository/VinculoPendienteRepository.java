package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.VinculoPendiente;

public interface VinculoPendienteRepository extends RepositorioTenant<VinculoPendiente> {

    /** Quien confirma el vinculo todavia no tiene sesion: el token es la credencial y dice de quien es. */
    @EntityGraph(attributePaths = {"usuario", "empresa"})
    Optional<VinculoPendiente> findByTokenHash(String tokenHash);

    /** Lo gasta solo si sigue ahi: de dos confirmaciones a la vez, la segunda borra 0 filas. */
    @Modifying
    @Query("delete from VinculoPendiente v where v.id = :id")
    int gastar(Long id);

    /** Lo vencido ya no vincula nada; se barre cada vez que se crea uno, y cruza tiendas a proposito. */
    @Modifying
    @Query("delete from VinculoPendiente v where v.venceEn < :ahora")
    int borrarVencidos(LocalDateTime ahora);

    @Modifying
    @Query("delete from VinculoPendiente v where v.empresa.id = :empresaId and v.usuario.id = :usuarioId")
    int borrarLosDe(Long empresaId, Long usuarioId);
}
