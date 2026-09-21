package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private Empresa empresa;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setNombre("Acme");

        usuario = new Usuario();
        usuario.setId(10L);
        usuario.setEmpresa(empresa);
        usuario.setNombre("Juan");
        usuario.setEmail("juan@acme.com");
        usuario.setPasswordHash("hashed");
        usuario.setRolAcceso(RolAcceso.EDITOR);
        usuario.setActivo(true);
    }

    @Test
    @DisplayName("HU-02: crear colaborador con email unico")
    void crearColaborador_exitoso() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("nuevo@acme.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario result = usuarioService.crearColaborador(1L, "Nuevo", "nuevo@acme.com", "pass",
                RolAcceso.SOLO_LECTURA);

        assertEquals("Nuevo", result.getNombre());
        assertEquals(RolAcceso.SOLO_LECTURA, result.getRolAcceso());
        assertTrue(result.isActivo());
    }

    @Test
    @DisplayName("HU-02: un correo registrado en cualquier empresa no se puede reutilizar")
    void crearColaborador_correoRegistradoEnOtraEmpresa_lanzaExcepcionSinGuardar() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("juan@acme.com")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.crearColaborador(1L, "Juan", "juan@acme.com", "pass",
                        RolAcceso.EDITOR));

        assertTrue(ex.getMessage().contains("juan@acme.com"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-02: el correo se guarda sin espacios y en minusculas")
    void crearColaborador_correoConMayusculas_seGuardaNormalizado() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("nuevo@acme.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario result = usuarioService.crearColaborador(1L, "Nuevo", "  Nuevo@ACME.com ", "pass",
                RolAcceso.EDITOR);

        assertEquals("nuevo@acme.com", result.getEmail());
    }

    @Test
    @DisplayName("HU-03: el login encuentra al usuario aunque el correo llegue con mayusculas")
    void autenticar_correoConMayusculas_encuentraAlUsuario() {
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        assertEquals(usuario, usuarioService.autenticar(" Juan@Acme.com", "pass"));
    }

    @Test
    @DisplayName("HU-03: autenticar con credenciales validas retorna usuario")
    void autenticar_exitoso() {
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        Usuario result = usuarioService.autenticar("juan@acme.com", "pass");

        assertEquals(usuario.getId(), result.getId());
    }

    @Test
    @DisplayName("HU-03: autenticar con password incorrecta lanza excepcion")
    void autenticar_password_incorrecta() {
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.autenticar("juan@acme.com", "wrong"));

        assertTrue(ex.getMessage().toLowerCase().contains("correo") ||
                   ex.getMessage().toLowerCase().contains("contrasena"));
    }

    @Test
    @DisplayName("HU-03: autenticar usuario inactivo lanza excepcion")
    void autenticar_usuario_inactivo() {
        usuario.setActivo(false);
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));

        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.autenticar("juan@acme.com", "pass"));
    }

    @Test
    @DisplayName("HU-02: desactivar usuario pone activo=false")
    void desactivar_exitoso() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.desactivar(1L, 10L);

        assertFalse(usuario.isActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void actualizar_rol_y_estado() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenReturn(usuario);

        Usuario actualizado = usuarioService.actualizar(1L, 10L, RolAcceso.ADMINISTRADOR, false);

        assertEquals(RolAcceso.ADMINISTRADOR, actualizado.getRolAcceso());
        assertFalse(actualizado.isActivo());
    }

    @Test
    @DisplayName("Obtener usuario inexistente lanza RecursoNoEncontrado")
    void obtener_inexistente() {
        when(usuarioRepository.findByIdAndEmpresaId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> usuarioService.obtener(1L, 99L));
    }
}
