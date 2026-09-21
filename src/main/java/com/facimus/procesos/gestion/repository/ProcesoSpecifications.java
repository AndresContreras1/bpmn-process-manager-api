package com.facimus.procesos.gestion.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

/** Filtros combinables para HU-07 (busqueda por nombre, estado y categoria). */
public final class ProcesoSpecifications {

    private ProcesoSpecifications() {
    }

    public static Specification<Proceso> conFiltros(Long empresaId, String nombre, EstadoProceso estado,
            String categoria) {
        return (root, query, cb) -> {
            var predicado = cb.and(
                    cb.equal(root.get("empresa").get("id"), empresaId),
                    cb.isTrue(root.get("activo")));

            if (StringUtils.hasText(nombre)) {
                predicado = cb.and(predicado, cb.like(cb.lower(root.get("nombre")), "%" + nombre.toLowerCase() + "%"));
            }
            if (estado != null) {
                predicado = cb.and(predicado, cb.equal(root.get("estado"), estado));
            }
            if (StringUtils.hasText(categoria)) {
                predicado = cb.and(predicado, cb.equal(root.get("categoria"), categoria));
            }
            return predicado;
        };
    }
}
