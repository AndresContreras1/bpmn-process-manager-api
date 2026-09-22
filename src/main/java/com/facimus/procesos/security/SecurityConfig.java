package com.facimus.procesos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.service.UsuarioService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMINISTRADOR = RolAcceso.ADMINISTRADOR.name();
    private static final String EDITOR = RolAcceso.EDITOR.name();

    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    public SecurityConfig(JwtAuthEntryPoint jwtAuthEntryPoint, JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService, UsuarioService usuarioService)
            throws Exception {
        reglasComunes(http)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                // Sin @Bean a proposito: como bean, Spring Boot tambien lo registraria como filtro del servlet.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Reglas compartidas con la configuracion de seguridad de los tests de controllers. */
    static HttpSecurity reglasComunes(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // La consola H2 de dev se arma con frames de su mismo origen; ningun otro sitio puede enmarcar la API.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/empresas").permitAll()
                        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/error").permitAll()
                        // Matriz de permisos de las HU: cualquier rol consulta, administrador y editor modifican,
                        // y el administrador se reserva usuarios (HU-02), roles (HU-17 a HU-19), compartir procesos
                        // (HU-23) y los borrados de procesos (HU-06), actividades (HU-10), arcos (HU-13) y gateways
                        // (HU-16).
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/usuarios/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers("/api/v1/roles/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, "/api/v1/procesos/*/compartidos").hasAuthority(ADMINISTRADOR)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/procesos/**", "/api/v1/actividades/**",
                                "/api/v1/arcos/**", "/api/v1/gateways/**", "/api/v1/pools/**",
                                "/api/v1/lanes/**", "/api/v1/mensajes/**").hasAuthority(ADMINISTRADOR)
                        .requestMatchers("/api/v1/**").hasAnyAuthority(ADMINISTRADOR, EDITOR)
                        .anyRequest().authenticated());
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
