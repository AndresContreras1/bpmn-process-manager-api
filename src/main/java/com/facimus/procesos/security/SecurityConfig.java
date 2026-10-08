package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.service.IdempotenciaService;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMINISTRADOR = RolAcceso.ADMINISTRADOR.name();
    private static final String EDITOR = RolAcceso.EDITOR.name();

    /** La API responde JSON y nunca una pagina: si un navegador la mostrara, no carga nada ni deja enmarcarla. */
    static final String CSP_DE_LA_API =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    /** Swagger UI, que fuera de prod si es una pagina: sus scripts y estilos, del mismo origen, y sus iconos. */
    static final String CSP_DE_LA_DOCUMENTACION = "default-src 'self'; img-src 'self' data:; "
            + "style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    /** Nada de lo que la API ni la web usan: ni camara, ni microfono, ni ubicacion, ni pagos del navegador. */
    static final String PERMISOS = "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), "
            + "microphone=(), payment=(), usb=()";

    /** 365 dias en segundos: lo minimo que pide la lista de precarga de HSTS. */
    private static final long HSTS_SEGUNDOS = 31_536_000L;

    /** Lo que no cambia nada no pide el token CSRF. */
    private static final Set<String> SIN_EFECTOS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final String LOGIN = "/api/v1/auth/login";

    private static final RequestMatcher DOCUMENTACION = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"));

    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(JwtAuthEntryPoint jwtAuthEntryPoint, JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, RevokedSessions revokedSessions,
            IdempotenciaService idempotenciaService, JsonMapper jsonMapper) throws Exception {
        reglasComunes(http)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                // Sin @Bean a proposito: como bean, Spring Boot tambien lo registraria como filtro del servlet.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, revokedSessions),
                        UsernamePasswordAuthenticationFilter.class)
                // Despues de la autorizacion: solo guarda las respuestas de peticiones que se pueden ejecutar.
                // D17: con una clave temporal solo se puede cambiarla, asi que va justo detras de identificar quien es.
                .addFilterAfter(new CambioDeClaveFilter(jsonMapper), JwtAuthenticationFilter.class)
                .addFilterAfter(new IdempotencyFilter(idempotenciaService, jsonMapper), AuthorizationFilter.class);
        return http.build();
    }

    /** Reglas compartidas con la configuracion de seguridad de los tests de controllers. */
    static HttpSecurity reglasComunes(HttpSecurity http) throws Exception {
        return http
                .csrf(SecurityConfig::csrf)
                .headers(SecurityConfig::cabeceras)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // D29: salir tambien sin un acceso vigente, para que un navegador siempre pueda borrar sus
                        // cookies; y el token CSRF, que la web pide al arrancar.
                        .requestMatchers(HttpMethod.POST, LOGIN, "/api/v1/auth/refresh", "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        // Los enlaces del correo: quien los sigue todavia no tiene sesion, y el token es la
                        // credencial. Pedir una recuperacion responde igual exista o no el correo.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verificacion/confirmar",
                                "/api/v1/auth/recuperacion", "/api/v1/auth/recuperacion/confirmar",
                                "/api/v1/auth/invitacion/aceptar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/empresas").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error")
                        .permitAll()
                        // El estado, la version y lo que lee Prometheus son publicos: en prod, Actuator vive en un
                        // puerto que solo alcanza la red interna. Las metricas, solo para el administrador.
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority(ADMINISTRADOR)
                        // Matriz de permisos de las HU: cualquier rol consulta, administrador y editor modifican,
                        // y el administrador se reserva usuarios (HU-02), roles (HU-17 a HU-19), compartir procesos
                        // (HU-23) y los borrados de procesos (HU-06), actividades (HU-10), arcos (HU-13),
                        // gateways (HU-16) y eventos (HU-04).
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password", "/api/v1/auth/verificacion")
                        .authenticated()
                        .requestMatchers("/api/v1/usuarios/**").hasAuthority(ADMINISTRADOR)
                        // El gobierno de la tienda es del administrador: su historial y su configuracion.
                        .requestMatchers("/api/v1/empresas/actual/historial",
                                "/api/v1/empresas/actual/configuracion").hasAuthority(ADMINISTRADOR)
                        // D8: mover el reloj cambia lo que les pasa a todos los casos de la tienda a la vez,
                        // asi que la simulacion entera es del administrador, tambien para mirarla.
                        .requestMatchers("/api/v1/simulacion", "/api/v1/simulacion/**")
                        .hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers("/api/v1/roles/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/procesos/*/compartidos").hasAuthority(ADMINISTRADOR)
                        // Retirar una version cambia lo que la tienda da por bueno: es del administrador (D2).
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/procesos/*/versiones/*")
                        .hasAuthority(ADMINISTRADOR)
                        // Tocar a mano las variables de un caso o volver a lanzarlo es rescatar una ejecucion que
                        // se torcio: abrir casos y completar tareas es del dia a dia, esto no.
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/casos/*/variables").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/casos/*/reintentar").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/procesos/**", "/api/v1/actividades/**",
                                "/api/v1/arcos/**", "/api/v1/gateways/**", "/api/v1/eventos/**",
                                "/api/v1/pools/**", "/api/v1/lanes/**",
                                "/api/v1/mensajes/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers("/api/v1/**").hasAnyAuthority(ADMINISTRADOR, EDITOR)
                        .anyRequest().authenticated());
    }

    /**
     * Lo que cada respuesta le dice al navegador: que no adivine el tipo, que no la enmarque nadie, que no mande la
     * direccion de donde venia, que no le de acceso a nada del equipo y que no comparta su ventana ni sus recursos
     * con otro origen. HSTS sale solo en lo que llego por HTTPS, y detras del proxy eso lo dice
     * {@code X-Forwarded-Proto}; la precarga en los navegadores se pide aparte, cuando produccion lleva un mes en
     * HTTPS. La web la sirve NGINX con sus propias cabeceras: estas son las de la API.
     */
    static void cabeceras(HeadersConfigurer<HttpSecurity> cabeceras) {
        cabeceras
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(permisos -> permisos.policy(PERMISOS))
                .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                .crossOriginResourcePolicy(corp -> corp.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(HSTS_SEGUNDOS))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(new NegatedRequestMatcher(DOCUMENTACION),
                        new StaticHeadersWriter("Content-Security-Policy", CSP_DE_LA_API)))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(DOCUMENTACION,
                        new StaticHeadersWriter("Content-Security-Policy", CSP_DE_LA_DOCUMENTACION)));
    }

    /**
     * D29: el token CSRF de una SPA. La web lo lee de la cookie {@code XSRF-TOKEN}, la unica que su JavaScript puede
     * leer, y lo devuelve en {@code X-XSRF-TOKEN}; otro sitio no puede leer esa cookie, asi que no puede mandarlo.
     */
    static void csrf(CsrfConfigurer<HttpSecurity> csrf) {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorio.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
        csrf.spa()
                .csrfTokenRepository(repositorio)
                .requireCsrfProtectionMatcher(SecurityConfig::pideCsrf);
    }

    /**
     * Lo pide lo que cambia algo y trae las cookies de la sesion, que un navegador manda por su cuenta, y el login,
     * para que otro sitio no meta al navegador en una cuenta ajena. Lo que no trae cookies no tiene sesion que
     * aprovechar: el registro de una tienda y, cuando lleguen, las claves de API.
     */
    static boolean pideCsrf(HttpServletRequest peticion) {
        return !SIN_EFECTOS.contains(peticion.getMethod())
                && (CookiesDeSesion.traeSesion(peticion) || LOGIN.equals(peticion.getRequestURI()));
    }

    /** El reloj del sistema, como bean para que los tests unitarios puedan mover el tiempo. */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }

    /**
     * HU-03: los intentos fallidos del login se cuentan por correo e IP, en una ventana deslizante, en la base: todas
     * las instancias cuentan los mismos (D34).
     */
    @Bean
    public IntentosDeLogin limitadorDeLogin(JdbcTemplate jdbc, @Value("${login.max-failed-attempts}") int maximo,
            @Value("${login.failed-attempts-window}") Duration ventana, Clock reloj) {
        return new IntentosDeLogin(jdbc, maximo, ventana, reloj);
    }

    /**
     * Cada hash lleva delante el algoritmo con el que se hizo, {bcrypt}: el dia que haya otro mejor, o un costo mas
     * alto, los hashes viejos se siguen comprobando y el login los rehace (UserAccountService). Los de antes de este
     * prefijo son de BCrypt y se comprueban igual.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${claves.costo-bcrypt}") int costo) {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(costo);
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }

    /**
     * HU-03: DaoAuthenticationProvider compara la clave con BCrypt. Si el correo no existe igual gasta el tiempo de una
     * comparacion, y responde el mismo BadCredentialsException: ni la respuesta ni su demora delatan que correos
     * estan registrados. Si la clave es buena y su hash es de un algoritmo o un costo viejo, lo rehace.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserAccountService cuentas, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(cuentas);
        proveedor.setPasswordEncoder(passwordEncoder);
        proveedor.setUserDetailsPasswordService(cuentas);
        return new ProviderManager(proveedor);
    }
}
