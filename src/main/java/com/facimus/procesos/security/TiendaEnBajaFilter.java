package com.facimus.procesos.security;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.facimus.procesos.common.api.Problemas;
import com.facimus.procesos.common.security.ApiPrincipal;
import com.facimus.procesos.gestion.service.EmpresaService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 35: una tienda dada de baja pasa sus 30 dias de gracia en solo lectura. Su gente entra y lo mira todo, y un
 * administrador puede cancelar la baja; cualquier otro cambio responde 409. Va detras de la autorizacion, y solo
 * pregunta a la base por una peticion que cambia algo y que el rol de quien la hace permite.
 */
public class TiendaEnBajaFilter extends OncePerRequestFilter {

    static final String TITULO = "Tienda dada de baja";

    private static final Set<String> DE_LECTURA = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Entrar, renovar, salir o cambiar la clave siguen sirviendo: la tienda se sigue consultando. */
    private static final String AUTENTICACION = "/api/v1/auth/";

    private static final String BAJA = "/api/v1/empresas/actual/baja";

    private final EmpresaService empresaService;
    private final JsonMapper jsonMapper;

    public TiendaEnBajaFilter(EmpresaService empresaService, JsonMapper jsonMapper) {
        this.empresaService = empresaService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<LocalDateTime> borrado = cambiaAlgo(request)
                ? principal().flatMap(quien -> empresaService.borradoProgramado(quien.empresaId()))
                : Optional.empty();
        if (borrado.isPresent()) {
            responder(request, response, borrado.get());
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Todo lo que no es una lectura, salvo la autenticacion y cancelar la baja. */
    private static boolean cambiaAlgo(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        boolean cancelaLaBaja = HttpMethod.DELETE.matches(request.getMethod()) && BAJA.equals(ruta);
        return !DE_LECTURA.contains(request.getMethod()) && !ruta.startsWith(AUTENTICACION) && !cancelaLaBaja;
    }

    private static Optional<ApiPrincipal> principal() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getPrincipal() instanceof ApiPrincipal principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    private void responder(HttpServletRequest request, HttpServletResponse response, LocalDateTime borrado)
            throws IOException {
        ProblemDetail problema = Problemas.de(HttpStatus.CONFLICT, TITULO, "La tienda está dada de baja: hasta el "
                + borrado.toLocalDate() + " solo se puede consultar, y un administrador todavía puede cancelar la baja.");
        problema.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
