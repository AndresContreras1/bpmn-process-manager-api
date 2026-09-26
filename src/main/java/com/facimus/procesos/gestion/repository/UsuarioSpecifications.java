package com.facimus.procesos.gestion.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.facimus.procesos.gestion.model.Usuario;

/**
 * Filtros combinables del listado de colaboradores (HU-02), los mismos dos que ya tenian los procesos: una parte
 * del nombre y si se quiere ver tambien lo que esta desactivado.
 *
 * <p>Un colaborador desactivado no se borra -sigue firmando el historial que firmo-, asi que tiene que poder
 * mirarse para reactivarlo. Por defecto no estorba: quien abre la pantalla quiere ver a su equipo de hoy.
 */
public final class UsuarioSpecifications {

    private UsuarioSpecifications() {
    }

    public static Specification<Usuario> conFiltros(Long empresaId, String nombre, boolean incluirInactivos) {
        return (root, query, cb) -> {
            var predicado = incluirInactivos
                    ? cb.equal(root.get("empresa").get("id"), empresaId)
                    : cb.and(cb.equal(root.get("empresa").get("id"), empresaId), cb.isTrue(root.get("activo")));

            if (StringUtils.hasText(nombre)) {
                predicado = cb.and(predicado,
                        cb.like(cb.lower(root.get("nombre")), "%" + nombre.trim().toLowerCase() + "%"));
            }
            return predicado;
        };
    }
}
