package com.facimus.procesos.gestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.facimus.procesos.config.LimpiezaConfig;
import com.facimus.procesos.gestion.model.ClaveIdempotencia;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RefreshToken;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Sesion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ClaveIdempotenciaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Limpieza;
import com.facimus.procesos.gestion.service.LimpiezaService;

/**
 * D20: la purga contra la base, que es donde se ve si el borrado se lleva lo que tiene que llevarse y deja lo demas.
 * <p>
 * Cada caso pone una fila a cada lado del limite. La prueba barre una vez antes de empezar, para que lo que cuente
 * la segunda pasada sea exactamente lo que ella creo y no lo que dejaron otras clases del mismo contexto.
 */
@SpringBootTest
@ActiveProfiles("test")
class LimpiezaIntegracionTest {

    private static final int RETENCION_DIAS = 7;

    @Autowired
    private LimpiezaService limpiezaService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SesionRepository sesionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ClaveIdempotenciaRepository claveIdempotenciaRepository;

    @Autowired
    private ApplicationContext contexto;

    private Empresa tienda;
    private Usuario usuario;

    @BeforeEach
    void dejarLaBaseBarrida() {
        limpiezaService.limpiar();
        tienda = empresaRepository.save(Empresa.builder()
                .nombre("Tienda de la limpieza")
                .nit(UUID.randomUUID().toString().substring(0, 18))
                .correoContacto("contacto@limpieza.com")
                .fechaRegistro(LocalDate.now())
                .build());
        usuario = usuarioRepository.save(Usuario.builder()
                .empresa(tienda)
                .nombre("Usuaria de la limpieza")
                .email(UUID.randomUUID() + "@limpieza.com")
                .passwordHash("hash-de-la-clave")
                .rolAcceso(RolAcceso.EDITOR)
                .build());
    }

    @Test
    @DisplayName("Un refresh token vencido se va y uno que todavia sirve se queda")
    void refreshTokens_soloSeVanLosVencidos() {
        Sesion sesion = sesion(hace(1));
        Long vencido = refreshToken(sesion, hace(RETENCION_DIAS + 1)).getId();
        Long dentroDelLimite = refreshToken(sesion, hace(RETENCION_DIAS - 1)).getId();
        Long vivo = refreshToken(sesion, LocalDateTime.now().plusDays(7)).getId();

        Limpieza limpieza = limpiezaService.limpiar();

        assertThat(limpieza).isEqualTo(new Limpieza(1, 0, 0));
        assertThat(existeToken(vencido)).isFalse();
        assertThat(existeToken(dentroDelLimite)).isTrue();
        assertThat(existeToken(vivo)).isTrue();
    }

    @Test
    @DisplayName("Una sesion vieja sin ningun token se va; con un token vivo se queda")
    void sesiones_seVanLasQueYaNoPuedenEmitirNada() {
        Long muerta = sesion(hace(RETENCION_DIAS + 1), hace(RETENCION_DIAS + 1)).getId();
        Sesion conToken = sesion(hace(RETENCION_DIAS + 1), hace(RETENCION_DIAS + 1));
        refreshToken(conToken, LocalDateTime.now().plusDays(7));
        Long recienCerrada = sesion(hace(RETENCION_DIAS + 1), LocalDateTime.now().minusHours(1)).getId();
        Long abiertaYVieja = sesion(hace(RETENCION_DIAS + 1)).getId();

        Limpieza limpieza = limpiezaService.limpiar();

        assertThat(limpieza).isEqualTo(new Limpieza(0, 2, 0));
        assertThat(existeSesion(muerta)).isFalse();
        // Sin fecha de cierre cuenta la de inicio: una sesion que nadie cerro tampoco se guarda para siempre.
        assertThat(existeSesion(abiertaYVieja)).isFalse();
        assertThat(existeSesion(conToken.getId())).isTrue();
        assertThat(existeSesion(recienCerrada)).isTrue();
    }

    @Test
    @DisplayName("La sesion que se queda sin tokens en esta misma pasada se va con ellos")
    void sesiones_laQueSeQuedaSinTokensSeVaEnLaMismaPasada() {
        Sesion sesion = sesion(hace(RETENCION_DIAS + 1), hace(RETENCION_DIAS + 1));
        refreshToken(sesion, hace(RETENCION_DIAS + 1));

        Limpieza limpieza = limpiezaService.limpiar();

        assertThat(limpieza).isEqualTo(new Limpieza(1, 1, 0));
        assertThat(existeSesion(sesion.getId())).isFalse();
    }

    @Test
    @DisplayName("Una clave de idempotencia de mas de un dia se va y la de ayer por poco se queda")
    void clavesDeIdempotencia_seVanLasDeMasDeUnDia() {
        Long vieja = clave(LocalDateTime.now().minusHours(25)).getId();
        Long reciente = clave(LocalDateTime.now().minusHours(23)).getId();

        Limpieza limpieza = limpiezaService.limpiar();

        assertThat(limpieza).isEqualTo(new Limpieza(0, 0, 1));
        assertThat(claveIdempotenciaRepository.findById(vieja)).isEmpty();
        assertThat(claveIdempotenciaRepository.findById(reciente)).isPresent();
    }

    @Test
    @DisplayName("El perfil test no programa la purga: la corre quien prueba, no un reloj por detras")
    void perfilTest_sinTrabajoProgramado() {
        assertThat(contexto.getBeanNamesForType(LimpiezaConfig.class)).isEmpty();
    }

    private static LocalDateTime hace(int dias) {
        return LocalDateTime.now().minusDays(dias);
    }

    private boolean existeSesion(Long id) {
        return sesionRepository.findById(id).isPresent();
    }

    private boolean existeToken(Long id) {
        return refreshTokenRepository.findById(id).isPresent();
    }

    private Sesion sesion(LocalDateTime inicio) {
        return sesion(inicio, null);
    }

    private Sesion sesion(LocalDateTime inicio, LocalDateTime cierre) {
        return sesionRepository.save(Sesion.builder()
                .empresa(tienda)
                .usuario(usuario)
                .codigo(UUID.randomUUID().toString())
                .fechaInicio(inicio)
                .fechaCierre(cierre)
                .build());
    }

    private RefreshToken refreshToken(Sesion sesion, LocalDateTime expiracion) {
        return refreshTokenRepository.save(RefreshToken.builder()
                .empresa(tienda)
                .sesion(sesion)
                .tokenHash(UUID.randomUUID().toString())
                .fechaEmision(expiracion.minusDays(7))
                .fechaExpiracion(expiracion)
                .build());
    }

    private ClaveIdempotencia clave(LocalDateTime creacion) {
        return claveIdempotenciaRepository.save(ClaveIdempotencia.builder()
                .empresa(tienda)
                .usuarioId(usuario.getId())
                .clave(UUID.randomUUID().toString())
                .huella(UUID.randomUUID().toString().replace("-", "").repeat(2))
                .estado(201)
                .fechaCreacion(creacion)
                .build());
    }
}
