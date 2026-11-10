package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.model.Passkey;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.PasskeyRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.PasskeyServiceImpl;

/**
 * PR 49b: lo que se guarda de cada passkey y a quien pertenece. La verificacion criptografica la prueba
 * PasskeysIntegracionTest, de punta a punta; aqui, las reglas de la cuenta.
 */
@ExtendWith(MockitoExtension.class)
class PasskeyServiceTest {

    private static final byte[] HANDLE = {1, 2, 3};
    private static final byte[] CREDENCIAL = {9, 8, 7};

    @Mock
    private PasskeyRepository passkeyRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SegundoFactorService segundoFactorService;

    @Mock
    private HistorialCambioService historialCambioService;

    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private PasskeyServiceImpl service;

    @Test
    @DisplayName("Una passkey sin nombre se llama Passkey; uno largo se corta a 64 y pierde los caracteres de control")
    void etiquetas() {
        Usuario usuario = usuario(true, null);
        when(usuarioRepository.findByWebauthnId(HANDLE)).thenReturn(Optional.of(usuario));
        ArgumentCaptor<Passkey> guardada = ArgumentCaptor.forClass(Passkey.class);

        service.guardarNueva(nueva("  "));
        service.guardarNueva(nueva("a".repeat(70)));
        service.guardarNueva(nueva("Mi\tportatil\n"));

        verify(passkeyRepository, org.mockito.Mockito.times(3)).save(guardada.capture());
        assertThat(guardada.getAllValues()).extracting(Passkey::getEtiqueta)
                .containsExactly("Passkey", "a".repeat(64), "Miportatil");
        assertThat(guardada.getAllValues().getFirst().getEmpresa().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Una persona no pasa de diez passkeys, y una desactivada no guarda ninguna")
    void maximoYDesactivada() {
        Usuario usuario = usuario(true, null);
        when(usuarioRepository.findByWebauthnId(HANDLE)).thenReturn(Optional.of(usuario));
        when(passkeyRepository.countByUsuarioIdAndEmpresaId(5L, 7L)).thenReturn(10L);
        assertThatThrownBy(() -> service.guardarNueva(nueva(null))).isInstanceOf(ReglaNegocioException.class);

        usuario.setActivo(false);
        assertThatThrownBy(() -> service.guardarNueva(nueva(null))).isInstanceOf(RecursoNoEncontradoException.class);
        verify(passkeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("El user handle se asigna una vez: el mismo otra vez no cambia nada, uno distinto se rechaza")
    void handle() {
        Usuario usuario = usuario(true, null);
        when(usuarioRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(usuario));

        service.asignarHandle(" Ana@Acme.com ", HANDLE);
        assertThat(usuario.getWebauthnId()).containsExactly(HANDLE);
        service.asignarHandle("ana@acme.com", HANDLE.clone());
        assertThatThrownBy(() -> service.asignarHandle("ana@acme.com", new byte[] {4}))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Ni una persona anonimizada ni una desactivada se encuentran por correo o por user handle")
    void personas() {
        when(usuarioRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(usuario(true, null)));
        assertThat(service.personaPorCorreo("ana@acme.com")).get().extracting(PersonaWebAuthn::usuarioId)
                .isEqualTo(5L);
        when(usuarioRepository.findByWebauthnId(HANDLE)).thenReturn(Optional.of(usuario(true, LocalDateTime.now())));
        assertThat(service.personaPorHandle(HANDLE)).isEmpty();
        when(usuarioRepository.findByEmail("eva@acme.com")).thenReturn(Optional.of(usuario(false, null)));
        assertThat(service.personaPorCorreo("eva@acme.com")).isEmpty();
    }

    @Test
    @DisplayName("Un uso sin passkey guardada, o con un contador que no sube, no se guarda")
    void usos() {
        assertThat(service.registrarUso(nueva(null))).isFalse();
        Passkey passkey = passkey();
        when(passkeyRepository.findByCredencialId(CREDENCIAL)).thenReturn(Optional.of(passkey));
        when(passkeyRepository.registrarUso(eq(3L), anyLong(), anyBoolean(), anyBoolean(), any())).thenReturn(0);
        assertThat(service.registrarUso(nueva(null))).isFalse();
    }

    @Test
    @DisplayName("Quitar una passkey revisa si quedan factores, y la de otra persona no se encuentra")
    void quitar() {
        Passkey passkey = passkey();
        when(passkeyRepository.findByIdAndUsuarioIdAndEmpresaId(3L, 5L, 7L)).thenReturn(Optional.of(passkey));

        service.quitar(7L, 5L, 3L);

        verify(passkeyRepository).delete(passkey);
        verify(segundoFactorService).olvidarCodigosSinFactor(7L, 5L);
        verify(auditoria).registrar(eq(7L), eq(5L), eq("PASSKEY_QUITADA"), eq("USUARIO"), eq(5L), any());
        assertThatThrownBy(() -> service.quitar(7L, 6L, 3L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Una passkey registrada que no es de esa persona y esa tienda no se da por suya")
    void registradaDeOtra() {
        when(passkeyRepository.findByCredencialId(CREDENCIAL)).thenReturn(Optional.of(passkey()));

        assertThatThrownBy(() -> service.registrada(8L, 5L, CREDENCIAL))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.registrada(7L, 6L, CREDENCIAL))
                .isInstanceOf(RecursoNoEncontradoException.class);
        when(segundoFactorService.codigosDelPrimerFactor(7L, 5L)).thenReturn(List.of("abcde-fghij"));
        assertThat(service.registrada(7L, 5L, CREDENCIAL).codigosDeRecuperacion()).containsExactly("abcde-fghij");
    }

    @Test
    @DisplayName("Lo que se lee de una passkey guardada vuelve tal cual, con su user handle y sus fechas")
    void lecturas() {
        Passkey passkey = passkey();
        when(passkeyRepository.findByCredencialId(CREDENCIAL)).thenReturn(Optional.of(passkey));
        PasskeyGuardada leida = service.buscar(CREDENCIAL).orElseThrow();
        assertThat(leida.handle()).containsExactly(HANDLE);
        assertThat(leida.creadaEn()).isNotNull();
        assertThat(leida.ultimoUsoEn()).isNull();

        when(usuarioRepository.findByWebauthnId(HANDLE)).thenReturn(Optional.of(passkey.getUsuario()));
        when(passkeyRepository.findAllByUsuarioIdAndEmpresaIdOrderByCreadaEnAscIdAsc(5L, 7L))
                .thenReturn(List.of(passkey));
        assertThat(service.delHandle(HANDLE)).hasSize(1);
        assertThat(service.delHandle(new byte[] {0})).isEmpty();

        service.borrar(CREDENCIAL);
        verify(passkeyRepository).delete(passkey);
    }

    private static PasskeyGuardada nueva(String etiqueta) {
        return new PasskeyGuardada(CREDENCIAL, HANDLE, new byte[] {1}, 0, true, null, true, true, new byte[] {2},
                new byte[] {3}, etiqueta, Instant.now(), null);
    }

    private static Passkey passkey() {
        Usuario duena = usuario(true, null);
        duena.setWebauthnId(HANDLE.clone());
        return Passkey.builder()
                .id(3L)
                .empresa(empresa())
                .usuario(duena)
                .credencialId(CREDENCIAL)
                .clavePublica(new byte[] {1})
                .transportes("internal")
                .objetoDeAtestacion(new byte[] {2})
                .datosDelCliente(new byte[] {3})
                .etiqueta("Portatil")
                .creadaEn(LocalDateTime.now())
                .build();
    }

    private static Usuario usuario(boolean activo, LocalDateTime anonimizado) {
        Usuario usuario = Usuario.builder()
                .id(5L)
                .empresa(empresa())
                .nombre("Ana")
                .email("ana@acme.com")
                .passwordHash("x")
                .activo(activo)
                .build();
        usuario.setAnonimizadoEn(anonimizado);
        usuario.setWebauthnId(activo && anonimizado == null ? null : HANDLE.clone());
        return usuario;
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(7L);
        return empresa;
    }
}
