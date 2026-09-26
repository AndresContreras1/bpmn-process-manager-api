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

    /**
     * El id de la version vigente, sin traerse el diagrama con ella. Es la mitad que nunca se puede guardar en
     * memoria -publicar o retirar la cambia-, y se pregunta siempre antes de mirar la cache (D19). Sale de un
     * indice y no lee el CLOB, asi que cuesta lo que cuesta una consulta por clave.
     */
    @Query("""
            select v.id from VersionProceso v
            where v.proceso.id = :procesoId and v.empresa.id = :empresaId and v.estado = :estado
            order by v.numero desc limit 1
            """)
    Optional<Long> idDeLaVigente(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId,
            @Param("estado") EstadoVersion estado);

    /** Lo mismo para una version concreta: su id, que es la clave con la que se le pregunta a la cache. */
    @Query("""
            select v.id from VersionProceso v
            where v.proceso.id = :procesoId and v.numero = :numero and v.empresa.id = :empresaId
            """)
    Optional<Long> idDeLaVersion(@Param("procesoId") Long procesoId, @Param("numero") int numero,
            @Param("empresaId") Long empresaId);

    /**
     * El diagrama de una version, comprobando la tienda. Lo unico que se guarda en memoria es esto: el texto de una
     * version concreta, que no cambia mientras la version exista.
     */
    @Query("select v.definicion from VersionProceso v where v.id = :id and v.empresa.id = :empresaId")
    Optional<String> definicionDe(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** El numero de la ultima publicada, retirada o no: los numeros no se reusan. */
    @Query("select max(v.numero) from VersionProceso v where v.proceso.id = :procesoId and v.empresa.id = :empresaId")
    Optional<Integer> ultimoNumero(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
