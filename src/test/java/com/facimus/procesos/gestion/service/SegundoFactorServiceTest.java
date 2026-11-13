package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.security.Base32;
import com.facimus.procesos.common.security.Factor;
import com.facimus.procesos.common.security.Totp;
import com.facimus.procesos.gestion.dto.response.InscripcionTotpResponse;
import com.facimus.procesos.gestion.dto.response.SegundoFactorResponse;
import com.facimus.procesos.gestion.model.CodigoDeRecuperacion;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.PoliticaSegundoFactor;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.SegundoFactor;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.CodigoDeRecuperacionRepository;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.PasskeyRepository;
import com.facimus.procesos.gestion.repository.SegundoFactorRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.CifradoDeSecretos;
import com.facimus.procesos.gestion.service.impl.SegundoFactorServiceImpl;

/**
 * Las reglas del segundo factor sin la base ni el filtro de seguridad: cuando se pide, como se inscribe y se confirma la
 * aplicacion, que codigo sirve una sola vez y que se borra al quitarlo. El cifrado es el de verdad, para que el secreto
 * guardado sea el que se le muestra a la persona.
 */
@ExtendWith(MockitoExtension.class)
class SegundoFactorServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;
    private static final Instant AHORA = Instant.parse("2026-11-11T12:00:10Z");
    private static final byte[] SECRETO = "12345678901234567890".getBytes();

    @Mock
    private SegundoFactorRepository segundoFactorRepository;
    @Mock
    private CodigoDeRecuperacionRepository codigoRepository;
    @Mock
    private PasskeyRepository passkeyRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private SesionService sesionService;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private Auditoria auditoria;

    @Captor
    private ArgumentCaptor<List<CodigoDeRecuperacion>> codigosGuardados;

    private final CifradoDeSecretos cifrado =
            new CifradoDeSecretos("test-only-encryption-key-with-at-least-32-characters");

    private SegundoFactorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SegundoFactorServiceImpl(segundoFactorRepository, codigoRepository, passkeyRepository,
                usuarioRepository, cifrado, historialCambioService, sesionService, configuracionRepository,
                auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("El estado cuenta los codigos solo si hay algun factor: sin aplicacion ni passkey son cero")
    void estado() {
        when(segundoFactorRepository.existsByUsuarioIdAndEmpresaIdAndConfirmadoEnIsNotNull(USUARIO, EMPRESA))
                .thenReturn(true);
        when(codigoRepository.countByUsuarioIdAndEmpresaIdAndUsadoEnIsNull(USUARIO, EMPRESA)).thenReturn(8L);
        assertThat(service.estado(EMPRESA, USUARIO)).isEqualTo(new SegundoFactorResponse(true, 0, 8));

        when(segundoFactorRepository.existsByUsuarioIdAndEmpresaIdAndConfirmadoEnIsNotNull(USUARIO, EMPRESA))
                .thenReturn(false);
        when(passkeyRepository.countByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(2L);
        assertThat(service.estado(EMPRESA, USUARIO)).isEqualTo(new SegundoFactorResponse(false, 2, 8));
    }

    @Test
    @DisplayName("Sin aplicacion ni passkeys no hay codigos que contar")
    void estadoSinFactor() {
        assertThat(service.estado(EMPRESA, USUARIO)).isEqualTo(new SegundoFactorResponse(false, 0, 0));
        verify(codigoRepository, never()).countByUsuarioIdAndEmpresaIdAndUsadoEnIsNull(anyLong(), anyLong());
    }

    @Test
    @DisplayName("El login pide el segundo factor con la aplicacion confirmada o con alguna passkey, no sin ellas")
    void loPide() {
        when(segundoFactorRepository.existsByUsuarioIdAndEmpresaIdAndConfirmadoEnIsNotNull(USUARIO, EMPRESA))
                .thenReturn(true, false, false);
        when(passkeyRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true, false);

        assertThat(service.loPide(EMPRESA, USUARIO)).isTrue();
        assertThat(service.loPide(EMPRESA, USUARIO)).isTrue();
        assertThat(service.loPide(EMPRESA, USUARIO)).isFalse();
    }

    @Test
    @DisplayName("Falta inscribir cuando la politica de la tienda lo exige a ese rol y la persona no tiene factor")
    void faltaInscribir() {
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(configuracion(PoliticaSegundoFactor.ADMINISTRADORES)));
        assertThat(service.faltaInscribir(EMPRESA, USUARIO, RolAcceso.EDITOR)).isFalse();
        assertThat(service.faltaInscribir(EMPRESA, USUARIO, RolAcceso.ADMINISTRADOR)).isTrue();

        when(passkeyRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true);
        assertThat(service.faltaInscribir(EMPRESA, USUARIO, RolAcceso.ADMINISTRADOR)).isFalse();

        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.empty());
        assertThat(service.faltaInscribir(EMPRESA, USUARIO, RolAcceso.ADMINISTRADOR)).isFalse();
    }

    @Test
    @DisplayName("Inscribir guarda el secreto cifrado y devuelve el mismo en Base32 con su URI otpauth")
    void inscribir() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        ArgumentCaptor<SegundoFactor> guardado = ArgumentCaptor.forClass(SegundoFactor.class);

        InscripcionTotpResponse respuesta = service.inscribir(EMPRESA, USUARIO);

        verify(segundoFactorRepository).save(guardado.capture());
        SegundoFactor factor = guardado.getValue();
        assertThat(factor.getUsuario().getId()).isEqualTo(USUARIO);
        assertThat(factor.getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(factor.getCreadoEn()).isNotNull();
        assertThat(factor.estaConfirmado()).isFalse();
        assertThat(Base32.codificar(cifrado.descifrar(factor.getSecretoCifrado(), USUARIO)))
                .isEqualTo(respuesta.secreto());
        assertThat(respuesta.secreto()).hasSize(32);
        assertThat(respuesta.uri()).isEqualTo("otpauth://totp/BPMN%20Process%20Manager:ana%2Bmfa%40acme.com?secret="
                + respuesta.secreto() + "&issuer=BPMN%20Process%20Manager&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    @DisplayName("Volver a inscribir sin confirmar reusa el factor pendiente y lo deja en el paso cero")
    void reinscribir() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        SegundoFactor pendiente = factor(null, 99);
        String secretoViejo = pendiente.getSecretoCifrado();
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(pendiente));

        service.inscribir(EMPRESA, USUARIO);

        verify(segundoFactorRepository).save(pendiente);
        assertThat(pendiente.getUltimoPaso()).isZero();
        assertThat(pendiente.getSecretoCifrado()).isNotEqualTo(secretoViejo);
    }

    @Test
    @DisplayName("Con la aplicacion ya confirmada no se inscribe otra, y una persona anonimizada no se encuentra")
    void inscribirRechazos() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(LocalDateTime.now(), 0)));
        assertThatThrownBy(() -> service.inscribir(EMPRESA, USUARIO)).isInstanceOf(ReglaNegocioException.class);

        Usuario anonimizado = usuario();
        anonimizado.setAnonimizadoEn(LocalDateTime.now());
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(anonimizado));
        assertThatThrownBy(() -> service.inscribir(EMPRESA, USUARIO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(segundoFactorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Confirmar con el codigo de ahora activa el factor, guarda el paso y entrega diez codigos")
    void confirmar() {
        SegundoFactor factor = factor(null, 0);
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(factor));

        List<String> codigos = service.confirmar(EMPRESA, USUARIO, codigoDe(Totp.paso(AHORA))).codigos();

        assertThat(factor.estaConfirmado()).isTrue();
        assertThat(factor.getUltimoPaso()).isEqualTo(Totp.paso(AHORA));
        assertThat(codigos).hasSize(10).doesNotHaveDuplicates()
                .allSatisfy(codigo -> assertThat(codigo).matches("[a-z2-7]{5}-[a-z2-7]{5}"));
        verify(codigoRepository).saveAll(codigosGuardados.capture());
        assertThat(codigosGuardados.getValue()).extracting(CodigoDeRecuperacion::getCodigoHash)
                .containsExactlyElementsOf(codigos.stream().map(c -> cifrado.huella(c.replace("-", ""))).toList());
        assertThat(codigosGuardados.getValue()).allSatisfy(guardado -> {
            assertThat(guardado.getUsuario().getId()).isEqualTo(USUARIO);
            assertThat(guardado.getEmpresa().getId()).isEqualTo(EMPRESA);
            assertThat(guardado.getCreadoEn()).isNotNull();
        });
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.USUARIO, USUARIO,
                "Segundo factor de \"Ana\" activado.");
        verify(auditoria).registrar(EMPRESA, USUARIO, "SEGUNDO_FACTOR_ACTIVADO", "USUARIO", USUARIO, Map.of());
    }

    @Test
    @DisplayName("Quien ya tenia codigos por una passkey los conserva: confirmar la aplicacion no le da otros")
    void confirmarConCodigos() {
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(null, 0)));
        when(codigoRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true);

        assertThat(service.confirmar(EMPRESA, USUARIO, codigoDe(Totp.paso(AHORA))).codigos()).isEmpty();
        verify(codigoRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("No se confirma sin factor pendiente, ni uno ya confirmado, ni con un codigo que no es el de ahora")
    void confirmarRechazos() {
        String codigoDeAhora = codigoDe(Totp.paso(AHORA));
        String codigoDeOtroPaso = codigoDe(Totp.paso(AHORA) + 5);

        assertThatThrownBy(() -> service.confirmar(EMPRESA, USUARIO, "123456"))
                .isInstanceOf(ReglaNegocioException.class);
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(LocalDateTime.now(), 0)));
        assertThatThrownBy(() -> service.confirmar(EMPRESA, USUARIO, codigoDeAhora))
                .isInstanceOf(ReglaNegocioException.class);

        SegundoFactor pendiente = factor(null, 0);
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(pendiente));
        assertThatThrownBy(() -> service.confirmar(EMPRESA, USUARIO, codigoDeOtroPaso))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(pendiente.estaConfirmado()).isFalse();
        verify(auditoria, never()).registrar(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Los codigos del primer factor salen una vez: si ya hay, no se crean otros")
    void codigosDelPrimerFactor() {
        when(codigoRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true, false);
        assertThat(service.codigosDelPrimerFactor(EMPRESA, USUARIO)).isEmpty();
        verify(usuarioRepository, never()).findByIdAndEmpresaId(anyLong(), anyLong());

        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        assertThat(service.codigosDelPrimerFactor(EMPRESA, USUARIO)).hasSize(10);
    }

    @Test
    @DisplayName("Los codigos se olvidan solo cuando no queda ningun factor")
    void olvidarCodigos() {
        when(passkeyRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true, false);

        service.olvidarCodigosSinFactor(EMPRESA, USUARIO);
        verify(codigoRepository, never()).borrarLosDe(anyLong(), anyLong());
        service.olvidarCodigosSinFactor(EMPRESA, USUARIO);
        verify(codigoRepository).borrarLosDe(EMPRESA, USUARIO);
    }

    @Test
    @DisplayName("Un codigo de la aplicacion entra una vez: si otra peticion ya acepto ese paso, no entra")
    void verificarTotp() {
        SegundoFactor factor = factor(LocalDateTime.now(), 0);
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(factor));
        long paso = Totp.paso(AHORA);
        when(segundoFactorRepository.aceptarPaso(3L, paso)).thenReturn(1, 0);

        assertThat(service.verificar(EMPRESA, USUARIO, " " + codigoDe(paso) + " ")).contains(Factor.TOTP);
        assertThat(service.verificar(EMPRESA, USUARIO, codigoDe(paso))).isEmpty();
    }

    @Test
    @DisplayName("Un codigo de seis cifras que no es el de ahora no llega a aceptar ningun paso")
    void verificarTotpEquivocado() {
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(LocalDateTime.now(), 0)));

        assertThat(service.verificar(EMPRESA, USUARIO, codigoDe(Totp.paso(AHORA) + 7))).isEmpty();
        verify(segundoFactorRepository, never()).aceptarPaso(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Un codigo de recuperacion se reconoce con o sin guion y en mayusculas, y se gasta una vez")
    void verificarRecuperacion() {
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(LocalDateTime.now(), 0)));
        CodigoDeRecuperacion recuperacion = CodigoDeRecuperacion.builder().id(11L).build();
        when(codigoRepository.findByUsuarioIdAndEmpresaIdAndCodigoHashAndUsadoEnIsNull(USUARIO, EMPRESA,
                cifrado.huella("abcdefghij"))).thenReturn(Optional.of(recuperacion));
        when(codigoRepository.marcarUsado(eq(11L), any())).thenReturn(1, 0);

        assertThat(service.verificar(EMPRESA, USUARIO, " ABCDE-FGHIJ ")).contains(Factor.CODIGO_DE_RECUPERACION);
        assertThat(service.verificar(EMPRESA, USUARIO, "abcde fghij")).isEmpty();
    }

    @Test
    @DisplayName("Sin aplicacion confirmada o sin codigo no se verifica nada")
    void verificarSinFactor() {
        assertThat(service.verificar(EMPRESA, USUARIO, "123456")).isEmpty();
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(null, 0)));
        assertThat(service.verificar(EMPRESA, USUARIO, codigoDe(Totp.paso(AHORA)))).isEmpty();
        when(segundoFactorRepository.findByUsuarioIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(factor(LocalDateTime.now(), 0)));
        assertThat(service.verificar(EMPRESA, USUARIO, null)).isEmpty();
        verify(segundoFactorRepository, never()).aceptarPaso(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Renovar los codigos borra los viejos y entrega diez nuevos; sin factor no se puede")
    void renovar() {
        assertThatThrownBy(() -> service.renovarCodigos(EMPRESA, USUARIO)).isInstanceOf(ReglaNegocioException.class);
        verify(codigoRepository, never()).borrarLosDe(anyLong(), anyLong());

        when(passkeyRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true);
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        assertThat(service.renovarCodigos(EMPRESA, USUARIO).codigos()).hasSize(10);
        verify(codigoRepository).borrarLosDe(EMPRESA, USUARIO);
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.USUARIO, USUARIO,
                "Códigos de recuperación de \"Ana\" renovados.");
        verify(auditoria).registrar(EMPRESA, USUARIO, "CODIGOS_DE_RECUPERACION_RENOVADOS", "USUARIO", USUARIO,
                Map.of());
    }

    @Test
    @DisplayName("Quitar la propia aplicacion deja los codigos si queda una passkey y lo anota")
    void quitarPropioConPasskey() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(segundoFactorRepository.borrarElDe(EMPRESA, USUARIO)).thenReturn(1);
        when(passkeyRepository.existsByUsuarioIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(true);

        service.quitarPropio(EMPRESA, USUARIO);

        verify(codigoRepository, never()).borrarLosDe(anyLong(), anyLong());
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.USUARIO, USUARIO,
                "Segundo factor de \"Ana\" quitado.");
        verify(auditoria).registrar(EMPRESA, USUARIO, "SEGUNDO_FACTOR_QUITADO", "USUARIO", USUARIO, Map.of());
    }

    @Test
    @DisplayName("Quitar la propia aplicacion sin otro factor borra los codigos; si no habia nada, no anota nada")
    void quitarPropio() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(segundoFactorRepository.borrarElDe(EMPRESA, USUARIO)).thenReturn(1, 0);

        service.quitarPropio(EMPRESA, USUARIO);
        verify(codigoRepository).borrarLosDe(EMPRESA, USUARIO);
        service.quitarPropio(EMPRESA, USUARIO);
        verify(auditoria).registrar(any(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Un administrador quita el segundo factor de otra persona y le cierra las sesiones, no el propio")
    void quitarDeOtro() {
        assertThatThrownBy(() -> service.quitarDeOtro(EMPRESA, USUARIO, USUARIO))
                .isInstanceOf(ReglaNegocioException.class);

        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(passkeyRepository.borrarLasDe(EMPRESA, USUARIO)).thenReturn(1);
        service.quitarDeOtro(EMPRESA, 2L, USUARIO);

        verify(codigoRepository).borrarLosDe(EMPRESA, USUARIO);
        verify(historialCambioService).registrarDeTienda(EMPRESA, 2L, RecursoDeHistorial.USUARIO, USUARIO,
                "Segundo factor de \"Ana\" quitado por un administrador.");
        verify(auditoria).registrar(EMPRESA, 2L, "SEGUNDO_FACTOR_QUITADO", "USUARIO", USUARIO, Map.of());
        verify(sesionService).cerrarTodas(EMPRESA, USUARIO);
    }

    @Test
    @DisplayName("Si la otra persona solo tenia la aplicacion tambien se quita; si no tenia nada, nada se anota")
    void quitarDeOtroSegunLoQueHabia() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(segundoFactorRepository.borrarElDe(EMPRESA, USUARIO)).thenReturn(1, 0);

        service.quitarDeOtro(EMPRESA, 2L, USUARIO);
        service.quitarDeOtro(EMPRESA, 2L, USUARIO);

        verify(sesionService).cerrarTodas(EMPRESA, USUARIO);
    }

    @Test
    @DisplayName("Borrar a la persona se lleva su segundo factor entero y su user handle de WebAuthn")
    void borrarDe() {
        Usuario usuario = usuario();
        usuario.setWebauthnId(new byte[] {1, 2});
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        service.borrarDe(EMPRESA, USUARIO);

        verify(codigoRepository).borrarLosDe(EMPRESA, USUARIO);
        verify(passkeyRepository).borrarLasDe(EMPRESA, USUARIO);
        verify(segundoFactorRepository).borrarElDe(EMPRESA, USUARIO);
        assertThat(usuario.getWebauthnId()).isNull();
    }

    private String codigoDe(long paso) {
        return Totp.codigo(SECRETO, paso, Totp.DIGITOS);
    }

    private SegundoFactor factor(LocalDateTime confirmado, long ultimoPaso) {
        Usuario usuario = usuario();
        return SegundoFactor.builder()
                .id(3L)
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .secretoCifrado(cifrado.cifrar(SECRETO, USUARIO))
                .creadoEn(LocalDateTime.now())
                .confirmadoEn(confirmado)
                .ultimoPaso(ultimoPaso)
                .build();
    }

    private static ConfiguracionTienda configuracion(PoliticaSegundoFactor politica) {
        return ConfiguracionTienda.builder().politicaSegundoFactor(politica).build();
    }

    private static Usuario usuario() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return Usuario.builder()
                .id(USUARIO)
                .empresa(empresa)
                .nombre("Ana")
                .email("ana+mfa@acme.com")
                .passwordHash("x")
                .build();
    }
}
