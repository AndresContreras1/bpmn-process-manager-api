package com.facimus.procesos.gestion.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.facimus.procesos.gestion.model.Clasificacion;
import com.facimus.procesos.gestion.model.EstadoProceso;
import com.facimus.procesos.gestion.model.Proceso;

/** Filtros combinables para HU-07 (busqueda por nombre, estado y categoria) y HU-06.3 (lo eliminado). */
public final class ProcesoSpecifications {

    private ProcesoSpecifications() {
    }

    public static Specification<Proceso> conFiltros(Long empresaId, String nombre, EstadoProceso estado,
            String categoria, boolean incluirInactivos) {
        return (root, query, cb) -> {
            // HU-06.3: lo eliminado sigue ahi y el administrador puede mirarlo; por defecto no estorba.
            var predicado = incluirInactivos
                    ? cb.equal(root.get("empresa").get("id"), empresaId)
                    : cb.and(cb.equal(root.get("empresa").get("id"), empresaId), cb.isTrue(root.get("activo")));

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

    /** PR 60: lo que el actor puede ver, en la misma consulta de la lista. */
    public static Specification<Proceso> visibles(Set<Long> carpetasOcultas, Set<Long> procesosOcultos,
            Set<Long> procesosVisibles) {
        return (root, query, cb) -> {
            var predicado = cb.conjunction();
            if (!carpetasOcultas.isEmpty()) {
                var enCarpetaVisible = cb.or(cb.isNull(root.get("carpetaId")),
                        cb.not(root.get("carpetaId").in(carpetasOcultas)));
                predicado = procesosVisibles.isEmpty() ? enCarpetaVisible
                        : cb.or(enCarpetaVisible, root.get("id").in(procesosVisibles));
            }
            if (!procesosOcultos.isEmpty()) {
                predicado = cb.and(predicado, cb.not(root.get("id").in(procesosOcultos)));
            }
            return predicado;
        };
    }

    /** PR 59: los filtros del repositorio. Los ids son los de una carpeta y las de adentro; vacio no filtra. */
    public static Specification<Proceso> delRepositorio(Collection<Long> carpetas, String etiqueta, Long duenoId,
            Clasificacion clasificacion, LocalDate revisionAntesDe) {
        return (root, query, cb) -> {
            var predicado = cb.conjunction();
            if (carpetas != null) {
                predicado = cb.and(predicado, root.get("carpetaId").in(carpetas));
            }
            if (StringUtils.hasText(etiqueta)) {
                predicado = cb.and(predicado, cb.isTrue(cb.function("array_contains", Boolean.class,
                        root.get("etiquetas"), cb.literal(etiqueta.trim().toLowerCase(Locale.ROOT)))));
            }
            if (duenoId != null) {
                predicado = cb.and(predicado, cb.equal(root.get("duenoId"), duenoId));
            }
            if (clasificacion != null) {
                predicado = cb.and(predicado, cb.equal(root.get("clasificacion"), clasificacion));
            }
            if (revisionAntesDe != null) {
                predicado = cb.and(predicado,
                        cb.lessThanOrEqualTo(root.<LocalDate>get("proximaRevision"), revisionAntesDe));
            }
            return predicado;
        };
    }
}
