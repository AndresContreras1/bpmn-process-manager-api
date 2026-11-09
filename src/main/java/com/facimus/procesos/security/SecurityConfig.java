package com.facimus.procesos.security;

import java.time.Clock;
import java.time.Duration;

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
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
                .csrf(AbstractHttpConfigurer::disable)
                .headers(SecurityConfig::cabeceras)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout", "/api/v1/auth/password")
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HU-03: DaoAuthenticationProvider compara la clave con BCrypt. Si el correo no existe igual gasta el tiempo de una
     * comparacion, y responde el mismo BadCredentialsException: ni la respuesta ni su demora delatan que correos
     * estan registrados.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider(userDetailsService);
        proveedor.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(proveedor);
    }
}
