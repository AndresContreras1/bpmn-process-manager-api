package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SesionInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.gestion.dto.response.SesionAbiertaResponse;
import com.facimus.procesos.gestion.dto.response.SesionActual;
import com.facimus.procesos.gestion.dto.response.SesionIniciada;
import com.facimus.procesos.gestion.dto.response.SesionVerificada;
import com.facimus.procesos.gestion.event.SesionesCerradas;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.RefreshToken;
import com.facimus.procesos.gestion.model.Sesion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.RefreshTokenRepository;
import com.facimus.procesos.gestion.repository.SesionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.SesionServiceImpl;

/**
 * Las reglas de la sesion sin la base: cuanto vive cada refresh token frente a la inactividad y la duracion de la
 * tienda, cuando renovar cierra la sesion, y que se avisa al cerrar. Las cookies y el filtro los prueba
 * SesionesIntegracionTest.
 */
@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;

    @Mock
    private SesionRepository sesionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private SesionServiceImpl service;

    @Test
    @DisplayName("Iniciar abre la sesion con sus factores y emite un refresh token que vence con la inactividad")
    void iniciar() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(sesionRepository.save(any(Sesion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        ArgumentCaptor<Sesion> sesion = ArgumentCaptor.forClass(Sesion.class);
        ArgumentCaptor<RefreshToken> token = ArgumentCaptor.forClass(RefreshToken.class);

        SesionIniciada iniciada = service.iniciar(EMPRESA, USUARIO, EnumSet.of(Factor.TOTP, Factor.PASSWORD));

        verify(sesionRepository).save(sesion.capture());
        Sesion guardada = sesion.getValue();
        assertThat(guardada.getCodigo()).hasSize(36).isEqualTo(iniciada.sesion());
        assertThat(guardada.getFactores()).isEqualTo("PASSWORD,TOTP");
        assertThat(guardada.getVerificadaEn()).isEqualTo(guardada.getFechaInicio());
        assertThat(guardada.getUsuario().getId()).isEqualTo(USUARIO);
        assertThat(guardada.getEmpresa().getId()).isEqualTo(EMPRESA);
        verify(auditoria).registrar(EMPRESA, USUARIO, "SESION_INICIADA", "USUARIO", USUARIO,
                Map.of("factores", "PASSWORD,TOTP"));

        verify(refreshTokenRepository).save(token.capture());
        RefreshToken emitido = token.getValue();
        assertThat(emitido.getTokenHash()).isEqualTo(Huella.de(iniciada.refreshToken()));
        assertThat(emitido.getSesion()).isSameAs(guardada);
        assertThat(emitido.getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(emitido.getFechaEmision()).isEqualTo(guardada.getFechaInicio());
        assertThat(emitido.getFechaExpiracion()).isEqualTo(guardada.getFechaInicio().plusMinutes(30));
        assertThat(iniciada.refreshToken()).hasSize(43);
        assertThat(iniciada.vidaDelRefresco()).isEqualTo(Duration.ofMinutes(30));
        assertThat(iniciada.vidaRestante()).isEqualTo(Duration.ofHours(8));
        assertThat(iniciada.factores()).containsExactly(Factor.PASSWORD, Factor.TOTP);
        assertThat(iniciada.verificadaEn()).isEqualTo(instante(guardada.getVerificadaEn()));
        assertThat(iniciada.usuario().id()).isEqualTo(USUARIO);
    }

    @Test
    @DisplayName("Una tienda sin configuracion toma los topes: una hora sin uso y un dia desde el login")
    void iniciarSinConfiguracion() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(sesionRepository.save(any(Sesion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        SesionIniciada iniciada = service.iniciar(EMPRESA, USUARIO, EnumSet.of(Factor.PASSWORD));

        assertThat(iniciada.vidaDelRefresco()).isEqualTo(Duration.ofHours(1));
        assertThat(iniciada.vidaRestante()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("No se abre sesion para quien no esta en la tienda")
    void iniciarSinUsuario() {
        Set<Factor> factores = EnumSet.of(Factor.PASSWORD);
        assertThatThrownBy(() -> service.iniciar(EMPRESA, USUARIO, factores))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(sesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Renovar gasta el token y emite otro; cerca del fin, el nuevo vence en el fin y no despues")
    void renovar() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(8).plusMinutes(10));
        RefreshToken token = token(sesion, LocalDateTime.now().plusMinutes(5));
        when(refreshTokenRepository.findByTokenHash(Huella.de("viejo"))).thenReturn(Optional.of(token));
        when(refreshTokenRepository.marcarUsado(eq(9L), any())).thenReturn(1);
        ArgumentCaptor<RefreshToken> nuevo = ArgumentCaptor.forClass(RefreshToken.class);

        SesionIniciada renovada = service.renovar("viejo");

        verify(refreshTokenRepository).save(nuevo.capture());
        assertThat(nuevo.getValue().getFechaExpiracion()).isEqualTo(sesion.getFechaInicio().plusHours(8));
        assertThat(renovada.sesion()).isEqualTo("c1");
        assertThat(renovada.vidaDelRefresco()).isEqualTo(renovada.vidaRestante())
                .isLessThanOrEqualTo(Duration.ofMinutes(10)).isGreaterThan(Duration.ofMinutes(9));
        assertThat(sesion.estaAbierta()).isTrue();
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Lejos del fin, el token nuevo vence cuando se cumple la inactividad")
    void renovarLejosDelFin() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(1));
        when(refreshTokenRepository.findByTokenHash(Huella.de("viejo")))
                .thenReturn(Optional.of(token(sesion, LocalDateTime.now().plusMinutes(5))));
        when(refreshTokenRepository.marcarUsado(eq(9L), any())).thenReturn(1);

        SesionIniciada renovada = service.renovar("viejo");

        assertThat(renovada.vidaDelRefresco()).isEqualTo(Duration.ofMinutes(30));
        assertThat(renovada.vidaRestante()).isLessThanOrEqualTo(Duration.ofHours(7))
                .isGreaterThan(Duration.ofHours(7).minusMinutes(1));
    }

    @Test
    @DisplayName("Un token desconocido, vencido, de una sesion cerrada o de alguien desactivado no renueva")
    void renovarRechazos() {
        assertThatThrownBy(() -> service.renovar("otro")).isInstanceOf(SesionInvalidaException.class);

        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(1));
        RefreshToken token = token(sesion, LocalDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(Huella.de("viejo"))).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.renovar("viejo")).isInstanceOf(SesionInvalidaException.class);

        token.setFechaExpiracion(LocalDateTime.now().plusMinutes(5));
        sesion.getUsuario().setActivo(false);
        assertThatThrownBy(() -> service.renovar("viejo")).isInstanceOf(SesionInvalidaException.class);

        sesion.getUsuario().setActivo(true);
        sesion.setFechaCierre(LocalDateTime.now());
        assertThatThrownBy(() -> service.renovar("viejo")).isInstanceOf(SesionInvalidaException.class);
        verify(refreshTokenRepository, never()).marcarUsado(anyLong(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una sesion que llego al fin de la tienda se cierra al intentar renovarla")
    void renovarPasadoElFin() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(8).minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(Huella.de("viejo")))
                .thenReturn(Optional.of(token(sesion, LocalDateTime.now().plusMinutes(5))));

        assertThatThrownBy(() -> service.renovar("viejo")).isInstanceOf(SesionInvalidaException.class);

        assertThat(sesion.estaAbierta()).isFalse();
        verify(eventos).publishEvent((Object) new SesionesCerradas(List.of("c1")));
        verify(refreshTokenRepository, never()).marcarUsado(anyLong(), any());
    }

    @Test
    @DisplayName("Un refresh token que ya se habia usado circula copiado: se cierra la sesion")
    void renovarReutilizado() {
        Sesion sesion = sesion("c1", LocalDateTime.now().minusMinutes(5));
        when(refreshTokenRepository.findByTokenHash(Huella.de("viejo")))
                .thenReturn(Optional.of(token(sesion, LocalDateTime.now().plusMinutes(5))));

        assertThatThrownBy(() -> service.renovar("viejo")).isInstanceOf(SesionInvalidaException.class);

        assertThat(sesion.estaAbierta()).isFalse();
        verify(eventos).publishEvent((Object) new SesionesCerradas(List.of("c1")));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reverificar suma el factor a la sesion abierta y marca la hora; una cerrada no se reverifica")
    void reverificar() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(2));
        LocalDateTime antes = sesion.getVerificadaEn();
        when(sesionRepository.findByCodigoAndUsuarioIdAndEmpresaId("c1", USUARIO, EMPRESA))
                .thenReturn(Optional.of(sesion));

        SesionVerificada verificada = service.reverificar(EMPRESA, USUARIO, "c1", Factor.PASSKEY);

        assertThat(sesion.getFactores()).isEqualTo("PASSWORD,PASSKEY");
        assertThat(sesion.getVerificadaEn()).isAfter(antes);
        assertThat(verificada.factores()).containsExactly(Factor.PASSWORD, Factor.PASSKEY);
        assertThat(verificada.sesion()).isEqualTo("c1");
        assertThat(verificada.usuario().id()).isEqualTo(USUARIO);
        assertThat(verificada.verificadaEn()).isEqualTo(instante(sesion.getVerificadaEn()));
        assertThat(verificada.vidaRestante()).isLessThanOrEqualTo(Duration.ofHours(6))
                .isGreaterThan(Duration.ofHours(6).minusMinutes(1));

        sesion.setFechaCierre(LocalDateTime.now());
        assertThatThrownBy(() -> service.reverificar(EMPRESA, USUARIO, "c1", Factor.TOTP))
                .isInstanceOf(SesionInvalidaException.class);
        assertThat(sesion.getFactores()).isEqualTo("PASSWORD,PASSKEY");
    }

    @Test
    @DisplayName("La sesion actual dice cuanto le queda sin uso y hasta el fin, nunca menos de cero")
    void actual() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(2));
        when(sesionRepository.findByCodigoAndUsuarioIdAndEmpresaId("c1", USUARIO, EMPRESA))
                .thenReturn(Optional.of(sesion));
        when(refreshTokenRepository.findFirstBySesionIdAndEmpresaIdAndFechaUsoIsNullOrderByFechaEmisionDesc(1L,
                EMPRESA)).thenReturn(Optional.of(token(sesion, LocalDateTime.now().plusMinutes(20))),
                        Optional.empty(), Optional.of(token(sesion, LocalDateTime.now().minusMinutes(3))));

        SesionActual conToken = service.actual(EMPRESA, USUARIO, "c1");
        assertThat(conToken.usuario().id()).isEqualTo(USUARIO);
        assertThat(conToken.inactividad()).isLessThanOrEqualTo(Duration.ofMinutes(20))
                .isGreaterThan(Duration.ofMinutes(19));
        assertThat(conToken.restante()).isLessThanOrEqualTo(Duration.ofHours(6))
                .isGreaterThan(Duration.ofHours(6).minusMinutes(1));
        assertThat(service.actual(EMPRESA, USUARIO, "c1").inactividad()).isZero();
        assertThat(service.actual(EMPRESA, USUARIO, "c1").inactividad()).isZero();

        sesion.setFechaInicio(LocalDateTime.now().minusHours(9));
        when(refreshTokenRepository.findFirstBySesionIdAndEmpresaIdAndFechaUsoIsNullOrderByFechaEmisionDesc(1L,
                EMPRESA)).thenReturn(Optional.empty());
        assertThat(service.actual(EMPRESA, USUARIO, "c1").restante()).isZero();

        sesion.setFechaCierre(LocalDateTime.now());
        assertThatThrownBy(() -> service.actual(EMPRESA, USUARIO, "c1")).isInstanceOf(SesionInvalidaException.class);
    }

    @Test
    @DisplayName("Las abiertas son las que no llegaron al fin y tienen un token vigente, la mas reciente primero")
    void abiertas() {
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion(30, 8)));
        LocalDateTime ahora = LocalDateTime.now();
        Sesion vieja = sesion("vieja", ahora.minusHours(9));
        Sesion sinToken = sesion("sin-token", ahora.minusHours(1));
        Sesion vencida = sesion("vencida", ahora.minusHours(1));
        Sesion esta = sesion("esta", ahora.minusHours(3));
        esta.setFactores("TOTP,PASSWORD");
        Sesion otra = sesion("otra", ahora.minusHours(2));
        sinToken.setId(2L);
        vencida.setId(3L);
        esta.setId(4L);
        otra.setId(5L);
        when(sesionRepository.findAllByUsuarioIdAndEmpresaIdAndFechaCierreIsNull(USUARIO, EMPRESA))
                .thenReturn(List.of(vieja, sinToken, vencida, esta, otra));
        when(refreshTokenRepository.findFirstBySesionIdAndEmpresaIdAndFechaUsoIsNullOrderByFechaEmisionDesc(any(),
                eq(EMPRESA))).thenAnswer(invocacion -> switch (invocacion.<Long>getArgument(0).intValue()) {
                    case 3 -> Optional.of(token(vencida, ahora.minusMinutes(1)));
                    case 4 -> Optional.of(token(esta, ahora.plusMinutes(10)));
                    case 5 -> Optional.of(token(otra, ahora.plusMinutes(10)));
                    default -> Optional.empty();
                });
        RefreshToken ultimoDeEsta = token(esta, ahora.plusMinutes(10));
        ultimoDeEsta.setFechaEmision(ahora.minusMinutes(5));
        when(refreshTokenRepository.findFirstBySesionIdAndEmpresaIdOrderByFechaEmisionDesc(4L, EMPRESA))
                .thenReturn(Optional.of(ultimoDeEsta));
        when(refreshTokenRepository.findFirstBySesionIdAndEmpresaIdOrderByFechaEmisionDesc(5L, EMPRESA))
                .thenReturn(Optional.empty());

        List<SesionAbiertaResponse> abiertas = service.abiertas(EMPRESA, USUARIO, "esta");

        assertThat(abiertas).containsExactly(
                new SesionAbiertaResponse(4L, esta.getFechaInicio(), ahora.minusMinutes(5),
                        List.of("PASSWORD", "TOTP"), true),
                new SesionAbiertaResponse(5L, otra.getFechaInicio(), otra.getFechaInicio(), List.of("PASSWORD"),
                        false));
    }

    @Test
    @DisplayName("Cerrar una sesion por id avisa con su codigo; la de otra persona no se encuentra")
    void cerrarPorId() {
        Sesion sesion = sesion("c1", LocalDateTime.now().minusHours(1));
        when(sesionRepository.findByIdAndEmpresaId(1L, EMPRESA)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> service.cerrarPorId(EMPRESA, 6L, 1L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        service.cerrarPorId(EMPRESA, USUARIO, 1L);
        service.cerrarPorId(EMPRESA, USUARIO, 1L);

        assertThat(sesion.estaAbierta()).isFalse();
        verify(eventos).publishEvent((Object) new SesionesCerradas(List.of("c1")));
    }

    @Test
    @DisplayName("Cerrar las demas deja abierta la actual y dice cuantas cerro")
    void cerrarLasDemas() {
        Sesion esta = sesion("esta", LocalDateTime.now());
        Sesion otra = sesion("otra", LocalDateTime.now());
        Sesion tercera = sesion("tercera", LocalDateTime.now());
        when(sesionRepository.findAllByUsuarioIdAndEmpresaIdAndFechaCierreIsNull(USUARIO, EMPRESA))
                .thenReturn(List.of(esta, otra, tercera));

        assertThat(service.cerrarLasDemas(EMPRESA, USUARIO, "esta")).isEqualTo(2);

        assertThat(esta.estaAbierta()).isTrue();
        assertThat(otra.estaAbierta()).isFalse();
        assertThat(tercera.estaAbierta()).isFalse();
        verify(eventos).publishEvent((Object) new SesionesCerradas(List.of("otra", "tercera")));
    }

    @Test
    @DisplayName("Cerrar por codigo marca la sesion y avisa siempre, aunque la base ya no la tenga abierta")
    void cerrar() {
        Sesion sesion = sesion("c1", LocalDateTime.now());
        when(sesionRepository.findByCodigoAndUsuarioIdAndEmpresaId("c1", USUARIO, EMPRESA))
                .thenReturn(Optional.of(sesion));

        service.cerrar(EMPRESA, USUARIO, "c1");
        assertThat(sesion.estaAbierta()).isFalse();
        LocalDateTime cerrada = sesion.getFechaCierre();
        service.cerrar(EMPRESA, USUARIO, "c1");

        assertThat(sesion.getFechaCierre()).isEqualTo(cerrada);
        verify(eventos, times(2)).publishEvent((Object) new SesionesCerradas(List.of("c1")));
    }

    @Test
    @DisplayName("Cerrar con el refresh token cierra su sesion; un token desconocido no cierra nada")
    void cerrarConToken() {
        Sesion sesion = sesion("c1", LocalDateTime.now());
        when(refreshTokenRepository.findByTokenHash(Huella.de("tok")))
                .thenReturn(Optional.of(token(sesion, LocalDateTime.now().plusMinutes(5))));

        service.cerrarConToken("tok");
        service.cerrarConToken("otro");

        assertThat(sesion.estaAbierta()).isFalse();
        verify(eventos).publishEvent((Object) new SesionesCerradas(List.of("c1")));
        verify(refreshTokenRepository).findByTokenHash(Huella.de("otro"));
    }

    @Test
    @DisplayName("Cerrar todas cierra las abiertas; sin ninguna abierta no avisa nada")
    void cerrarTodas() {
        Sesion abierta = sesion("c1", LocalDateTime.now());
        Sesion cerrada = sesion("c2", LocalDateTime.now());
        cerrada.setFechaCierre(LocalDateTime.now().minusDays(1));
        when(sesionRepository.findAllByUsuarioIdAndEmpresaIdAndFechaCierreIsNull(USUARIO, EMPRESA))
                .thenReturn(List.of(abierta, cerrada), List.of());

        service.cerrarTodas(EMPRESA, USUARIO);
        service.cerrarTodas(EMPRESA, USUARIO);

        assertThat(abierta.estaAbierta()).isFalse();
        verify(eventos).publishEvent((Object) new SesionesCerradas(List.of("c1")));
    }

    @Test
    @DisplayName("Las cerradas recientes se buscan desde ahora menos la ventana")
    void cerradasRecientes() {
        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        when(sesionRepository.codigosCerradosDesde(desde.capture())).thenReturn(List.of("c1"));

        assertThat(service.cerradasEnLosUltimos(Duration.ofMinutes(15))).containsExactly("c1");

        LocalDateTime esperado = LocalDateTime.now().minusMinutes(15);
        assertThat(desde.getValue()).isBetween(esperado.minusSeconds(5), esperado);
    }

    private static Instant instante(LocalDateTime fecha) {
        return fecha.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static ConfiguracionTienda configuracion(int inactividadMinutos, int duracionHoras) {
        return ConfiguracionTienda.builder()
                .inactividadSesionMinutos(inactividadMinutos)
                .duracionSesionHoras(duracionHoras)
                .build();
    }

    private static RefreshToken token(Sesion sesion, LocalDateTime vence) {
        return RefreshToken.builder()
                .id(9L)
                .empresa(sesion.getEmpresa())
                .sesion(sesion)
                .tokenHash("h")
                .fechaEmision(LocalDateTime.now().minusMinutes(1))
                .fechaExpiracion(vence)
                .build();
    }

    private static Sesion sesion(String codigo, LocalDateTime inicio) {
        Usuario usuario = usuario();
        return Sesion.builder()
                .id(1L)
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .codigo(codigo)
                .fechaInicio(inicio)
                .factores("PASSWORD")
                .verificadaEn(inicio)
                .build();
    }

    private static Usuario usuario() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return Usuario.builder()
                .id(USUARIO)
                .empresa(empresa)
                .nombre("Ana")
                .email("ana@acme.com")
                .passwordHash("x")
                .build();
    }
}
