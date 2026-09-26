package com.facimus.procesos.ejecucion.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
import com.facimus.procesos.ejecucion.dto.response.MensajesPorEstadoResponse;
import com.facimus.procesos.ejecucion.dto.response.PendientesPorSocioResponse;
import com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente;
import com.facimus.procesos.ejecucion.model.MensajeSaliente;

/**
 * La bandeja de salida de una tienda. Las dos consultas sin cuerpo son las que declara la entidad (D22): Spring
 * Data las encuentra por {@code MensajeSaliente.<nombre del metodo>}, y un nombre mal escrito tumba el arranque.
 */
public interface MensajeSalienteRepository extends RepositorioTenant<MensajeSaliente> {

    /** Lo que un proceso ha mandado, entero o solo lo que sigue pendiente. */
    Page<MensajeSaliente> bandejaDeSalida(@Param("empresaId") Long empresaId, @Param("procesoId") Long procesoId,
            @Param("estado") EstadoMensajeSaliente estado, Pageable pagina);

    /** Lo que ya tendria que haber llegado: es lo que mover el reloj entrega, en orden de vencimiento. */
    List<MensajeSaliente> vencidos(@Param("empresaId") Long empresaId, @Param("tick") int tick);

    /** Lo que un caso mando, para su detalle. */
    List<MensajeSaliente> findAllByCasoIdAndEmpresaIdOrderByIdAsc(Long casoId, Long empresaId);

    /** Lo que se mando, por estado, de un proceso o de toda la tienda. Una consulta agrupada, no una por estado. */
    @Query("""
            select new com.facimus.procesos.ejecucion.dto.response.MensajesPorEstadoResponse(m.estado, count(m))
            from MensajeSaliente m
            where m.empresa.id = :empresaId
              and (:procesoId is null or m.caso.proceso.id = :procesoId)
            group by m.estado
            order by m.estado
            """)
    List<MensajesPorEstadoResponse> salientesPorEstado(@Param("empresaId") Long empresaId,
            @Param("procesoId") Long procesoId);

    /** Cuantos hay en un estado, para el panel de simulacion. */
    long countByEmpresaIdAndEstado(Long empresaId, EstadoMensajeSaliente estado);

    /** Los mismos de toda la instalacion: lo que publica el medidor de Actuator. */
    long countByEstado(EstadoMensajeSaliente estado);

    /**
     * Lo pendiente agrupado por la clase de socio que lo espera. Es una sola consulta agrupada y no una por socio:
     * el panel se pinta entero de una vez.
     */
    @Query("""
            select new com.facimus.procesos.ejecucion.dto.response.PendientesPorSocioResponse(
                    m.integracion, count(m))
            from MensajeSaliente m
            where m.empresa.id = :empresaId
              and m.estado = com.facimus.procesos.ejecucion.model.EstadoMensajeSaliente.PENDIENTE
            group by m.integracion
            order by m.integracion
            """)
    List<PendientesPorSocioResponse> pendientesPorSocio(@Param("empresaId") Long empresaId);
}
