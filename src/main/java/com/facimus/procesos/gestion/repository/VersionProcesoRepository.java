package com.facimus.procesos.gestion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.VersionProceso;

/** Las versiones publicadas de un proceso. Solo se agregan y se retiran: ninguna consulta las modifica. */
public interface VersionProcesoRepository extends RepositorioTenant<VersionProceso> {

    /** De la mas reciente a la mas vieja, que es como las lee quien quiere ver la ultima. */
    List<VersionProceso> findAllByProcesoIdAndEmpresaIdOrderByNumeroDesc(Long procesoId, Long empresaId);

    Optional<VersionProceso> findByProcesoIdAndNumeroAndEmpresaId(Long procesoId, int numero, Long empresaId);

    /** La vigente es la ultima que no se retiro; si se retiraron todas, el proceso se queda sin ninguna. */
    Optional<VersionProceso> findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(Long procesoId, Long empresaId,
            EstadoVersion estado);

    /** El numero de la ultima publicada, retirada o no: los numeros no se reusan. */
    @Query("select max(v.numero) from VersionProceso v where v.proceso.id = :procesoId and v.empresa.id = :empresaId")
    Optional<Integer> ultimoNumero(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
