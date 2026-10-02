package com.facimus.procesos.common.api;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cada peticion lleva un id que la sigue de punta a punta: sale en la cabecera X-Request-Id de la respuesta, en cada
 * linea del log que se escribe mientras se atiende y en todo Problem Details. Con ese id, quien reporta un error le da
 * a quien lo investiga lo unico que hace falta para encontrarlo.
 *
 * <p>Si la peticion ya trae uno (se lo puso el proxy de delante, o un cliente que reintenta) se usa ese, siempre que
 * sea corto y de caracteres seguros: un id con saltos de linea o de mil caracteres serviria para ensuciar el log. Si
 * no, se genera.
 *
 * <p>Va antes que todo, la seguridad incluida, y tambien en el despacho de error del servidor: una peticion que falla
 * en un filtro y termina en /error conserva el mismo id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdDePeticionFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";

    /** La clave del id en el MDC: con ella sale en cada linea del log. */
    public static final String CLAVE_EN_EL_LOG = "requestId";

    private static final String ATRIBUTO = IdDePeticionFilter.class.getName() + ".ID";

    private static final Pattern ID_ACEPTADO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String id = idDe(request);
        request.setAttribute(ATRIBUTO, id);
        response.setHeader(CABECERA, id);
        MDC.put(CLAVE_EN_EL_LOG, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CLAVE_EN_EL_LOG);
        }
    }

    /** El despacho de error conserva el id de la peticion que fallo; una peticion nueva trae el suyo o recibe uno. */
    private static String idDe(HttpServletRequest request) {
        if (request.getAttribute(ATRIBUTO) instanceof String asignado) {
            return asignado;
        }
        String recibido = request.getHeader(CABECERA);
        return recibido != null && ID_ACEPTADO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
    }
}
