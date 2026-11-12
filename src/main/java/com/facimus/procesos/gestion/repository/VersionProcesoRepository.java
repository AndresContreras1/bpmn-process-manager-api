package com.facimus.procesos.gestion.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.gestion.model.EstadoVersion;
import com.facimus.procesos.gestion.model.VersionProceso;

/**
 * Las versiones publicadas de un proceso. Solo se agregan y se retiran: ninguna consulta las modifica.
 *
 * <p>PR 68: cada consulta pide las de tipo PUBLICADA. Las de PRUEBA, con las que se prueba un borrador, comparten la
 * tabla pero no son versiones del proceso para nadie: ni se listan, ni se leen por numero, ni pueden ser la vigente.
 */
public interface VersionProcesoRepository extends RepositorioTenant<VersionProceso> {

    /** De la mas reciente a la mas vieja, que es como las lee quien quiere ver la ultima. */
    @Query("""
            select v from VersionProceso v
            where v.proceso.id = :procesoId and v.empresa.id = :empresaId
              and v.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            order by v.numero desc
            """)
    List<VersionProceso> findAllByProcesoIdAndEmpresaIdOrderByNumeroDesc(@Param("procesoId") Long procesoId,
            @Param("empresaId") Long empresaId);

    @Query("""
            select v from VersionProceso v
            where v.proceso.id = :procesoId and v.numero = :numero and v.empresa.id = :empresaId
              and v.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            """)
    Optional<VersionProceso> findByProcesoIdAndNumeroAndEmpresaId(@Param("procesoId") Long procesoId,
            @Param("numero") int numero, @Param("empresaId") Long empresaId);

    /** La vigente es la ultima que no se retiro; si se retiraron todas, el proceso se queda sin ninguna. */
    @Query("""
            select v from VersionProceso v
            where v.proceso.id = :procesoId and v.empresa.id = :empresaId and v.estado = :estado
              and v.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            order by v.numero desc limit 1
            """)
    Optional<VersionProceso> findFirstByProcesoIdAndEmpresaIdAndEstadoOrderByNumeroDesc(
            @Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId,
            @Param("estado") EstadoVersion estado);

    /**
     * El id de la version vigente, sin traerse el diagrama con ella. Es la mitad que nunca se puede guardar en
     * memoria -publicar o retirar la cambia-, y se pregunta siempre antes de mirar la cache (D19). Sale de un
     * indice y no lee el CLOB, asi que cuesta lo que cuesta una consulta por clave.
     */
    @Query("""
            select v.id from VersionProceso v
            where v.proceso.id = :procesoId and v.empresa.id = :empresaId and v.estado = :estado
              and v.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            order by v.numero desc limit 1
            """)
    Optional<Long> idDeLaVigente(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId,
            @Param("estado") EstadoVersion estado);

    /** Lo mismo para una version concreta: su id, que es la clave con la que se le pregunta a la cache. */
    @Query("""
            select v.id from VersionProceso v
            where v.proceso.id = :procesoId and v.numero = :numero and v.empresa.id = :empresaId
              and v.tipo = com.facimus.procesos.gestion.model.TipoVersion.PUBLICADA
            """)
    Optional<Long> idDeLaVersion(@Param("procesoId") Long procesoId, @Param("numero") int numero,
            @Param("empresaId") Long empresaId);

    /**
     * El diagrama de una version, comprobando la tienda. Lo unico que se guarda en memoria es esto: el texto de una
     * version concreta, que no cambia mientras la version exista.
     */
    @Query("select v.definicion from VersionProceso v where v.id = :id and v.empresa.id = :empresaId")
    Optional<String> definicionDe(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** PR 53: el BPMN de una version, sin cargar la entidad entera ni su JSON. */
    @Query("select v.modeloBpmn from VersionProceso v where v.id = :id and v.empresa.id = :empresaId")
    Optional<String> modeloBpmnDe(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /**
     * PR 68: las versiones de prueba armadas antes de ese momento, de todas las tiendas. Es lo unico que la purga
     * necesita saber de ellas; borrarlas, con sus casos, es de la ejecucion.
     */
    @Query("""
            select v.id from VersionProceso v
            where v.tipo = com.facimus.procesos.gestion.model.TipoVersion.PRUEBA and v.fechaPublicacion < :limite
            order by v.id
            """)
    List<Long> pruebasAntesDe(@Param("limite") LocalDateTime limite);

    /** El numero de la ultima publicada, retirada o no: los numeros no se reusan. */
    @Query("select max(v.numero) from VersionProceso v where v.proceso.id = :procesoId and v.empresa.id = :empresaId")
    Optional<Integer> ultimoNumero(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
