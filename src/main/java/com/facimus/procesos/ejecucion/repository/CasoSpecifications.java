package com.facimus.procesos.ejecucion.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;

/** Los filtros del listado de casos: por proceso, por estado y por la referencia del pedido. */
public final class CasoSpecifications {

    private CasoSpecifications() {
    }

    public static Specification<Caso> conFiltros(Long empresaId, Long procesoId, EstadoCaso estado,
            String referencia) {
        return (root, query, cb) -> {
            var predicado = cb.equal(root.get("empresa").get("id"), empresaId);
            if (procesoId != null) {
                predicado = cb.and(predicado, cb.equal(root.get("proceso").get("id"), procesoId));
            }
            if (estado != null) {
                predicado = cb.and(predicado, cb.equal(root.get("estado"), estado));
            }
            if (StringUtils.hasText(referencia)) {
                predicado = cb.and(predicado, cb.equal(root.get("referencia"), referencia));
            }
            return predicado;
        };
    }
}
