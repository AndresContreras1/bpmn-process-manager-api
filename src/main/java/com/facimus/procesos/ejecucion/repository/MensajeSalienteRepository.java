package com.facimus.procesos.ejecucion.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import com.facimus.procesos.common.RepositorioTenant;
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
}
