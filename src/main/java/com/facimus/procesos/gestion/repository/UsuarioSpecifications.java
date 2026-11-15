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

    /** Con que se escapan los comodines de LIKE en lo que se escribe. */
    private static final char ESCAPE = '\\';

    private UsuarioSpecifications() {
    }

    public static Specification<Usuario> conFiltros(Long empresaId, String nombre, boolean incluirInactivos) {
        return (root, query, cb) -> {
            var predicado = incluirInactivos
                    ? cb.equal(root.get("empresa").get("id"), empresaId)
                    : cb.and(cb.equal(root.get("empresa").get("id"), empresaId), cb.isTrue(root.get("activo")));

            if (StringUtils.hasText(nombre)) {
                predicado = cb.and(predicado, cb.like(cb.lower(root.get("nombre")),
                        "%" + literal(nombre.trim().toLowerCase()) + "%", ESCAPE));
            }
            return predicado;
        };
    }

    /**
     * Lo escrito tal cual: un {@code %} o un {@code _} buscan ese caracter, no cualquier cosa. La busqueda de a quien
     * mencionar (F21) la puede hacer cualquier rol, y con un {@code _} traeria a cada persona de la tienda.
     */
    static String literal(String texto) {
        StringBuilder escapado = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                escapado.append(ESCAPE);
            }
            escapado.append(c);
        }
        return escapado.toString();
    }
}
