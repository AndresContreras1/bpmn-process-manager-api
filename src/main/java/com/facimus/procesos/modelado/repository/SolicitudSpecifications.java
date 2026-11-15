package com.facimus.procesos.modelado.repository;

import java.util.Collection;

import org.springframework.data.jpa.domain.Specification;

import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.modelado.model.EstadoSolicitud;
import com.facimus.procesos.modelado.model.SolicitudPublicacion;

import jakarta.persistence.criteria.Subquery;

/** PR 64: los filtros de las solicitudes de una tienda. */
public final class SolicitudSpecifications {

    private SolicitudSpecifications() {
    }

    /**
     * Las solicitudes de los procesos activos de la tienda, y de ellos los que {@code visibles} deja ver (PR 60), en
     * la misma consulta; sin {@code visibles}, todos. Un estado vacio no filtra; {@code soloEstas}, si llega, deja
     * solo esas.
     */
    public static Specification<SolicitudPublicacion> deLosProcesos(Long empresaId, Specification<Proceso> visibles,
            EstadoSolicitud estado, Collection<Long> soloEstas) {
        return (raiz, consulta, cb) -> {
            Subquery<Long> procesos = consulta.subquery(Long.class);
            var proceso = procesos.from(Proceso.class);
            var deLaTienda = cb.and(cb.equal(proceso.get("empresa").get("id"), empresaId),
                    cb.isTrue(proceso.get("activo")));
            if (visibles != null) {
                deLaTienda = cb.and(deLaTienda, visibles.toPredicate(proceso, consulta, cb));
            }
            procesos.select(proceso.get("id")).where(deLaTienda);
            var predicado = cb.and(cb.equal(raiz.get("empresa").get("id"), empresaId),
                    raiz.get("procesoId").in(procesos));
            if (estado != null) {
                predicado = cb.and(predicado, cb.equal(raiz.get("estado"), estado));
            }
            if (soloEstas != null) {
                predicado = cb.and(predicado, raiz.get("id").in(soloEstas));
            }
            return predicado;
        };
    }
}
