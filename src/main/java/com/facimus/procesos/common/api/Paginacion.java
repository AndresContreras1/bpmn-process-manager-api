package com.facimus.procesos.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * La pagina que pide el cliente en un listado. Cada controller valida el campo de orden contra su propia lista blanca,
 * y el id desempata: dos filas con el mismo valor quedan siempre en el mismo orden, asi ninguna se repite ni se salta
 * al pasar de pagina.
 */
public final class Paginacion {

    public static final int TAMANO_MAXIMO = 50;
    public static final String PAGINA_INVALIDA = "La página no puede ser negativa.";
    public static final String TAMANO_INVALIDO = "El tamaño de página va de 1 a 50.";

    private Paginacion() {
    }

    /** El orden llega como "campo" o "campo,asc|desc"; sin direccion es ascendente. */
    /** Una pagina de un listado que ya sale ordenado por la consulta, como el historial. */
    public static Pageable de(int pagina, int tamano) {
        return PageRequest.of(pagina, tamano);
    }

    public static Pageable de(int pagina, int tamano, String orden) {
        String[] partes = orden.split(",");
        Sort.Direction direccion = partes.length > 1 && "desc".equalsIgnoreCase(partes[1])
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return PageRequest.of(pagina, tamano, Sort.by(direccion, partes[0]).and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
