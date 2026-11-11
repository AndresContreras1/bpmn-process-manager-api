package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.facimus.procesos.common.EnlaceNoValidoException;
import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.IdentidadExternaResponse;
import com.facimus.procesos.gestion.dto.response.ResultadoEntradaExterna;
import com.facimus.procesos.gestion.dto.response.ResultadoEntradaExterna.Tipo;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.DominioVerificado;
import com.facimus.procesos.gestion.model.IdentidadExterna;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.model.VinculoPendiente;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.DominioVerificadoRepository;
import com.facimus.procesos.gestion.repository.IdentidadExternaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.repository.VinculoPendienteRepository;
import com.facimus.procesos.gestion.service.impl.IdentidadExternaServiceImpl;

/**
 * Entrar con Google o Microsoft sin el proveedor de verdad: quien entra directo, a quien se le pide vincular, quien se
 * une por el dominio de su tienda y quien se queda fuera. El flujo OIDC completo lo prueba OidcIntegracionTest.
 */
@ExtendWith(MockitoExtension.class)
class IdentidadExternaServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;

    @Mock
    private IdentidadExternaRepository identidadRepository;
    @Mock
    private VinculoPendienteRepository vinculoRepository;
    @Mock
    private DominioVerificadoRepository dominioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private IdentidadExternaServiceImpl service;

    @Test
    @DisplayName("Una cuenta ya vinculada entra si esta activa, sin anonimizar y su tienda no se borro")
    void vinculadaEntra() {
        Usuario usuario = usuario();
        when(identidadRepository.findByProveedorAndSujeto("google", "sub-1"))
                .thenReturn(Optional.of(identidad(usuario, "google")));

        ResultadoEntradaExterna resultado = service.entrar("google", "sub-1", null, false, null);

        assertThat(resultado.tipo()).isEqualTo(Tipo.ENTRA);
        assertThat(resultado.usuario().id()).isEqualTo(USUARIO);
        assertThat(resultado.usuario().email()).isEqualTo("ana@acme.com");
    }

    @Test
    @DisplayName("Una cuenta vinculada desactivada, anonimizada o de una tienda borrada no entra")
    void vinculadaRechazada() {
        Usuario usuario = usuario();
        when(identidadRepository.findByProveedorAndSujeto("google", "sub-1"))
                .thenReturn(Optional.of(identidad(usuario, "google")));

        usuario.setActivo(false);
        assertThat(service.entrar("google", "sub-1", null, true, null).motivo()).isEqualTo("cuenta-inactiva");
        usuario.setActivo(true);
        usuario.setAnonimizadoEn(LocalDateTime.now());
        assertThat(service.entrar("google", "sub-1", null, true, null).motivo()).isEqualTo("cuenta-inactiva");
        usuario.setAnonimizadoEn(null);
        usuario.getEmpresa().setBorradaEn(LocalDateTime.now());
        ResultadoEntradaExterna resultado = service.entrar("google", "sub-1", null, true, null);
        assertThat(resultado.tipo()).isEqualTo(Tipo.RECHAZADA);
        assertThat(resultado.motivo()).isEqualTo("cuenta-inactiva");
    }

    @Test
    @DisplayName("Un correo que el proveedor no verifico, o que no llega, no abre ninguna cuenta")
    void correoSinVerificar() {
        assertThat(service.entrar("google", "sub-1", "ana@acme.com", false, null).motivo())
                .isEqualTo("correo-no-verificado");
        assertThat(service.entrar("google", "sub-1", "  ", true, null).motivo()).isEqualTo("correo-no-verificado");
        assertThat(service.entrar("google", "sub-1", null, true, null).tipo()).isEqualTo(Tipo.RECHAZADA);
        verify(usuarioRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("Con el correo de una cuenta que ya existe se pide vincular: el token vale diez minutos")
    void pideVincular() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(usuario));
        ArgumentCaptor<VinculoPendiente> guardado = ArgumentCaptor.forClass(VinculoPendiente.class);

        ResultadoEntradaExterna resultado = service.entrar("microsoft", "sub-2", " Ana@Acme.com ", true, "Ana");

        assertThat(resultado.tipo()).isEqualTo(Tipo.VINCULAR);
        assertThat(resultado.token()).hasSize(43);
        verify(vinculoRepository).borrarVencidos(any());
        verify(vinculoRepository).save(guardado.capture());
        VinculoPendiente vinculo = guardado.getValue();
        assertThat(vinculo.getTokenHash()).isEqualTo(Huella.de(resultado.token()));
        assertThat(vinculo.getUsuario()).isSameAs(usuario);
        assertThat(vinculo.getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(vinculo.getProveedor()).isEqualTo("microsoft");
        assertThat(vinculo.getSujeto()).isEqualTo("sub-2");
        assertThat(vinculo.getEmail()).isEqualTo("ana@acme.com");
        assertThat(Duration.between(vinculo.getCreadoEn(), vinculo.getVenceEn())).isEqualTo(Duration.ofMinutes(10));
        verify(identidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Con el correo de una cuenta desactivada no se pide vincular")
    void correoDeCuentaInactiva() {
        Usuario usuario = usuario();
        usuario.setActivo(false);
        when(usuarioRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(usuario));

        assertThat(service.entrar("google", "sub-1", "ana@acme.com", true, null).motivo())
                .isEqualTo("cuenta-inactiva");
        verify(vinculoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Alguien nuevo de un dominio verificado se une con el rol por defecto y el correo ya verificado")
    void uneporDominio() {
        when(dominioRepository.findByDominioAndVerificadoEnIsNotNull("acme.com")).thenReturn(Optional.of(dominio()));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario nuevo = invocacion.getArgument(0);
            nuevo.setId(9L);
            return nuevo;
        });
        ArgumentCaptor<IdentidadExterna> identidad = ArgumentCaptor.forClass(IdentidadExterna.class);

        ResultadoEntradaExterna resultado = service.entrar("google", "sub-3", "luis@acme.com", true, "  Luis  ");

        assertThat(resultado.tipo()).isEqualTo(Tipo.ENTRA);
        assertThat(resultado.usuario().nombre()).isEqualTo("Luis");
        assertThat(resultado.usuario().rolAcceso()).isEqualTo(RolAcceso.EDITOR);
        assertThat(resultado.usuario().empresaId()).isEqualTo(EMPRESA);
        verify(identidadRepository).save(identidad.capture());
        assertThat(identidad.getValue().getUsuario().getId()).isEqualTo(9L);
        assertThat(identidad.getValue().getProveedor()).isEqualTo("google");
        assertThat(identidad.getValue().getSujeto()).isEqualTo("sub-3");
        assertThat(identidad.getValue().getEmail()).isEqualTo("luis@acme.com");
        assertThat(identidad.getValue().getVinculadaEn()).isNotNull();
        assertThat(identidad.getValue().getEmpresa().getId()).isEqualTo(EMPRESA);
        verify(historialCambioService).registrarDeTienda(EMPRESA, 9L, RecursoDeHistorial.USUARIO, 9L,
                "\"Luis\" se unió con Google por el dominio acme.com, con rol EDITOR.");
        verify(auditoria).registrar(EMPRESA, 9L, "USUARIO_UNIDO_POR_DOMINIO", "USUARIO", 9L,
                Map.of("proveedor", "google", "rol", "EDITOR"));
    }

    @Test
    @DisplayName("Quien se une sin nombre toma el de su correo, uno largo se corta a 120, y la clave es aleatoria")
    void nombreAlUnirse() {
        when(dominioRepository.findByDominioAndVerificadoEnIsNotNull("acme.com")).thenReturn(Optional.of(dominio()));
        when(passwordEncoder.encode(anyString())).thenAnswer(invocacion -> "{x}" + invocacion.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);

        service.entrar("okta", "sub-4", "luis.gomez@acme.com", true, " ");
        service.entrar("okta", "sub-5", "eva@acme.com", true, "e".repeat(130));

        verify(usuarioRepository, times(2)).save(guardado.capture());
        Usuario sinNombre = guardado.getAllValues().get(0);
        assertThat(sinNombre.getNombre()).isEqualTo("luis.gomez");
        assertThat(sinNombre.getCorreoVerificadoEn()).isNotNull();
        assertThat(sinNombre.getPasswordHash()).startsWith("{x}").hasSizeGreaterThan(40);
        assertThat(guardado.getAllValues().get(1).getNombre()).isEqualTo("e".repeat(120));
        assertThat(guardado.getAllValues().get(1).getPasswordHash()).isNotEqualTo(sinNombre.getPasswordHash());
        verify(historialCambioService).registrarDeTienda(any(), any(), any(), any(),
                eq("\"luis.gomez\" se unió con okta por el dominio acme.com, con rol "
                        + "EDITOR."));
    }

    @Test
    @DisplayName("Un correo nuevo de un dominio que nadie verifico se queda sin cuenta")
    void sinCuenta() {
        ResultadoEntradaExterna resultado = service.entrar("google", "sub-6", "eva@otra.com", true, "Eva");

        assertThat(resultado.tipo()).isEqualTo(Tipo.RECHAZADA);
        assertThat(resultado.motivo()).isEqualTo("sin-cuenta");
        verify(dominioRepository).findByDominioAndVerificadoEnIsNotNull("otra.com");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("El usuario de un vinculo sale mientras el vinculo no vence y la cuenta puede entrar")
    void usuarioDelVinculo() {
        Usuario usuario = usuario();
        VinculoPendiente vinculo = vinculo(usuario, LocalDateTime.now().plusMinutes(5));
        when(vinculoRepository.findByTokenHash(Huella.de("tok"))).thenReturn(Optional.of(vinculo));

        assertThat(service.usuarioDelVinculo("tok")).get().extracting(r -> r.id()).isEqualTo(USUARIO);
        usuario.setActivo(false);
        assertThat(service.usuarioDelVinculo("tok")).isEmpty();
        usuario.setActivo(true);
        vinculo.setVenceEn(LocalDateTime.now().minusSeconds(1));
        assertThat(service.usuarioDelVinculo("tok")).isEmpty();
    }

    @Test
    @DisplayName("Confirmar un vinculo vigente lo gasta, guarda la identidad y lo anota")
    void vincular() {
        Usuario usuario = usuario();
        when(vinculoRepository.findByTokenHash(Huella.de("tok")))
                .thenReturn(Optional.of(vinculo(usuario, LocalDateTime.now().plusMinutes(5))));
        when(vinculoRepository.gastar(4L)).thenReturn(1);
        ArgumentCaptor<IdentidadExterna> identidad = ArgumentCaptor.forClass(IdentidadExterna.class);

        service.vincular("tok");

        verify(identidadRepository).save(identidad.capture());
        assertThat(identidad.getValue().getUsuario()).isSameAs(usuario);
        assertThat(identidad.getValue().getProveedor()).isEqualTo("microsoft");
        assertThat(identidad.getValue().getSujeto()).isEqualTo("sub-2");
        assertThat(identidad.getValue().getEmail()).isEqualTo("ana@acme.com");
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.USUARIO, USUARIO,
                "Cuenta de Microsoft vinculada a \"Ana\".");
        verify(auditoria).registrar(EMPRESA, USUARIO, "IDENTIDAD_VINCULADA", "USUARIO", USUARIO,
                Map.of("proveedor", "microsoft"));
    }

    @Test
    @DisplayName("Un vinculo vencido o ya gastado no sirve, y una cuenta de proveedor ya vinculada no se vincula otra vez")
    void vincularRechazos() {
        Usuario usuario = usuario();
        VinculoPendiente vinculo = vinculo(usuario, LocalDateTime.now().minusSeconds(1));
        when(vinculoRepository.findByTokenHash(Huella.de("tok"))).thenReturn(Optional.of(vinculo));
        assertThatThrownBy(() -> service.vincular("tok")).isInstanceOf(EnlaceNoValidoException.class);
        verify(vinculoRepository, never()).gastar(anyLong());

        vinculo.setVenceEn(LocalDateTime.now().plusMinutes(5));
        when(vinculoRepository.gastar(4L)).thenReturn(0, 1);
        assertThatThrownBy(() -> service.vincular("tok")).isInstanceOf(EnlaceNoValidoException.class);

        when(identidadRepository.findByProveedorAndSujeto("microsoft", "sub-2"))
                .thenReturn(Optional.of(identidad(usuario, "microsoft")));
        assertThatThrownBy(() -> service.vincular("tok")).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esa cuenta de Microsoft ya está vinculada.");
        verify(identidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Las cuentas vinculadas salen con su proveedor, su correo y su fecha")
    void identidades() {
        IdentidadExterna identidad = identidad(usuario(), "google");
        when(identidadRepository.findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(USUARIO, EMPRESA))
                .thenReturn(List.of(identidad));

        assertThat(service.identidades(EMPRESA, USUARIO)).containsExactly(new IdentidadExternaResponse(2L, "google",
                "ana@acme.com", identidad.getVinculadaEn()));
    }

    @Test
    @DisplayName("Desvincular quita la cuenta y lo anota; la de otra persona no se encuentra")
    void desvincular() {
        IdentidadExterna identidad = identidad(usuario(), "google");
        when(identidadRepository.findByIdAndUsuarioIdAndEmpresaId(2L, USUARIO, EMPRESA))
                .thenReturn(Optional.of(identidad));
        when(identidadRepository.findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(USUARIO, EMPRESA))
                .thenReturn(List.of(identidad));

        service.desvincular(EMPRESA, USUARIO, 2L);

        verify(identidadRepository).delete(identidad);
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.USUARIO, USUARIO,
                "Cuenta de Google desvinculada de \"Ana\".");
        verify(auditoria).registrar(EMPRESA, USUARIO, "IDENTIDAD_DESVINCULADA", "USUARIO", USUARIO,
                Map.of("proveedor", "google"));
        assertThatThrownBy(() -> service.desvincular(EMPRESA, 6L, 2L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("En una tienda que solo entra con proveedor, nadie salvo un administrador quita su ultima cuenta")
    void desvincularLaUltima() {
        IdentidadExterna identidad = identidad(usuario(), "google");
        when(identidadRepository.findByIdAndUsuarioIdAndEmpresaId(2L, USUARIO, EMPRESA))
                .thenReturn(Optional.of(identidad));
        when(identidadRepository.findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(USUARIO, EMPRESA))
                .thenReturn(List.of(identidad), List.of(identidad, identidad(usuario(), "microsoft")));
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(ConfiguracionTienda.builder().soloEntradaExterna(true).build()));

        assertThatThrownBy(() -> service.desvincular(EMPRESA, USUARIO, 2L)).isInstanceOf(ReglaNegocioException.class);
        verify(identidadRepository, never()).delete(any());

        service.desvincular(EMPRESA, USUARIO, 2L);
        verify(identidadRepository).delete(identidad);
    }

    @Test
    @DisplayName("Un administrador puede quitar su ultima cuenta aunque la tienda solo entre con proveedor")
    void administradorDesvincula() {
        Usuario admin = usuario();
        admin.setRolAcceso(RolAcceso.ADMINISTRADOR);
        IdentidadExterna identidad = identidad(admin, "google");
        when(identidadRepository.findByIdAndUsuarioIdAndEmpresaId(2L, USUARIO, EMPRESA))
                .thenReturn(Optional.of(identidad));
        when(identidadRepository.findAllByUsuarioIdAndEmpresaIdOrderByVinculadaEnAscIdAsc(USUARIO, EMPRESA))
                .thenReturn(List.of(identidad));

        service.desvincular(EMPRESA, USUARIO, 2L);

        verify(identidadRepository).delete(identidad);
        verify(configuracionRepository, never()).findByEmpresaId(anyLong());
    }

    @Test
    @DisplayName("La entrada externa se exige a quien no es administrador, solo si la tienda lo pide")
    void exigeEntradaExterna() {
        assertThat(service.exigeEntradaExterna(EMPRESA, RolAcceso.ADMINISTRADOR)).isFalse();
        assertThat(service.exigeEntradaExterna(EMPRESA, RolAcceso.EDITOR)).isFalse();
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(ConfiguracionTienda.builder().soloEntradaExterna(true).build()));
        assertThat(service.exigeEntradaExterna(EMPRESA, RolAcceso.EDITOR)).isTrue();
        assertThat(service.exigeEntradaExterna(EMPRESA, RolAcceso.ADMINISTRADOR)).isFalse();
    }

    @Test
    @DisplayName("Borrar a la persona se lleva sus vinculos pendientes y sus cuentas vinculadas")
    void borrarDe() {
        service.borrarDe(EMPRESA, USUARIO);

        verify(vinculoRepository).borrarLosDe(EMPRESA, USUARIO);
        verify(identidadRepository).borrarLasDe(EMPRESA, USUARIO);
    }

    private static VinculoPendiente vinculo(Usuario usuario, LocalDateTime venceEn) {
        return VinculoPendiente.builder()
                .id(4L)
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .proveedor("microsoft")
                .sujeto("sub-2")
                .email("ana@acme.com")
                .tokenHash(Huella.de("tok"))
                .creadoEn(LocalDateTime.now())
                .venceEn(venceEn)
                .build();
    }

    private static IdentidadExterna identidad(Usuario usuario, String proveedor) {
        return IdentidadExterna.builder()
                .id(2L)
                .empresa(usuario.getEmpresa())
                .usuario(usuario)
                .proveedor(proveedor)
                .sujeto("sub-1")
                .email("ana@acme.com")
                .vinculadaEn(LocalDateTime.now())
                .build();
    }

    private static DominioVerificado dominio() {
        return DominioVerificado.builder()
                .empresa(empresa())
                .dominio("acme.com")
                .rolPorDefecto(RolAcceso.EDITOR)
                .verificadoEn(LocalDateTime.now())
                .build();
    }

    private static Usuario usuario() {
        return Usuario.builder()
                .id(USUARIO)
                .empresa(empresa())
                .nombre("Ana")
                .email("ana@acme.com")
                .passwordHash("x")
                .rolAcceso(RolAcceso.EDITOR)
                .build();
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return empresa;
    }
}
