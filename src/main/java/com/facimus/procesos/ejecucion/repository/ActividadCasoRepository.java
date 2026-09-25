package com.facimus.procesos.ejecucion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.dto.response.TareasPorRolResponse;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;

/** Los pasos de un caso por los nodos de su version: los tokens del motor y las tareas de la bandeja. */
public interface ActividadCasoRepository extends RepositorioTenant<ActividadCaso> {

    /**
     * La bandeja de tareas, opcionalmente la de un rol o la de un proceso. Es la consulta con nombre que declara la
     * entidad (D22), con su gemela de conteo para poder paginarla.
     */
    Page<ActividadCaso> bandejaPorRol(@Param("empresaId") Long empresaId,
            @Param("rolProcesoId") Long rolProcesoId, @Param("procesoId") Long procesoId,
            @Param("estado") EstadoActividadCaso estado, Pageable pagina);

    /**
     * La bandeja de una persona: la misma consulta, acotada a los roles de proceso a los que pertenece (D13). Es
     * otra consulta con nombre y no un parametro mas de la anterior porque una lista vacia no se puede preguntar
     * con un {@code in}, y quien no tiene roles no tiene bandeja propia que consultar.
     */
    Page<ActividadCaso> bandejaDeMisRoles(@Param("empresaId") Long empresaId, @Param("roles") List<Long> roles,
            @Param("procesoId") Long procesoId, @Param("estado") EstadoActividadCaso estado, Pageable pagina);

    /**
     * Las tareas que esperan en cada bandeja, con el nombre del rol. El nombre se trae en la misma consulta: un
     * tablero con ids no lo lee nadie, y pedirlo aparte seria una consulta por rol.
     */
    @Query("""
            select new com.facimus.procesos.ejecucion.dto.response.TareasPorRolResponse(
                    a.rolProcesoId, r.nombre, count(a))
            from ActividadCaso a, com.facimus.procesos.gestion.model.RolProceso r
            where a.empresa.id = :empresaId
              and r.id = a.rolProcesoId
              and a.tipoNodo = com.facimus.procesos.ejecucion.model.TipoNodoCaso.ACTIVIDAD
              and a.subtipo = 'USUARIO'
              and a.estado = com.facimus.procesos.ejecucion.model.EstadoActividadCaso.EN_ESPERA
              and (:procesoId is null or a.caso.proceso.id = :procesoId)
            group by a.rolProcesoId, r.nombre
            order by r.nombre
            """)
    List<TareasPorRolResponse> tareasPorRol(@Param("empresaId") Long empresaId,
            @Param("procesoId") Long procesoId);

    /** Lo que el motor procesa en esta vuelta, en el orden en que se creo. */
    List<ActividadCaso> findAllByCasoIdAndEmpresaIdAndEstadoOrderByIdAsc(Long casoId, Long empresaId,
            EstadoActividadCaso estado);

    /** Por donde ha pasado el caso, para su detalle. */
    List<ActividadCaso> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);

    /** Los tokens vivos del caso: mientras quede alguno, el caso no ha terminado. */
    List<ActividadCaso> findAllByCasoIdAndEmpresaIdAndEstadoInOrderByIdAsc(Long casoId, Long empresaId,
            List<EstadoActividadCaso> estados);

    /**
     * De que caso es una tarea, sin traer la fila. Quien va a completarla necesita el caso para bloquearlo antes de
     * leer la tarea: leerla primero dejaria en memoria un estado que el bloqueo ya no refresca, y dos personas
     * podrian completarla a la vez.
     */
    @Query("select a.caso.id from ActividadCaso a where a.id = :id and a.empresa.id = :empresaId")
    Optional<Long> casoDe(@Param("id") Long id, @Param("empresaId") Long empresaId);

    /** El token que espera en un nodo concreto, que es como un join encuentra el suyo. */
    Optional<ActividadCaso> findFirstByCasoIdAndEmpresaIdAndNodoIdAndEstadoOrderByIdAsc(Long casoId, Long empresaId,
            Long nodoId, EstadoActividadCaso estado);
}
