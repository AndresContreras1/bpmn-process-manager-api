package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.UsuarioServiceImpl;

import org.mapstruct.factory.Mappers;
import org.mockito.Spy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    /** El administrador que hace los cambios, distinto del usuario 10 que los recibe. */
    private static final Long AUTOR = 2L;

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SesionService sesionService;

    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);

    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

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

        UsuarioResponse result = usuarioService.crearColaborador(1L, 9L, "Nuevo", "nuevo@acme.com", "pass",
                RolAcceso.SOLO_LECTURA);

        assertEquals("Nuevo", result.nombre());
        assertEquals(RolAcceso.SOLO_LECTURA, result.rolAcceso());
        assertTrue(result.activo());
        assertEquals(1L, result.empresaId());
    }

    @Test
    @DisplayName("HU-02: un correo registrado en cualquier empresa no se puede reutilizar")
    void crearColaborador_correoRegistradoEnOtraEmpresa_lanzaExcepcionSinGuardar() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("juan@acme.com")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.crearColaborador(1L, 9L, "Juan", "juan@acme.com", "pass",
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

        UsuarioResponse result = usuarioService.crearColaborador(1L, 9L, "Nuevo", "  Nuevo@ACME.com ", "pass",
                RolAcceso.EDITOR);

        assertEquals("nuevo@acme.com", result.email());
    }

    @Test
    @DisplayName("HU-03: el login encuentra al usuario aunque el correo llegue con mayusculas")
    void buscarCredenciales_correoConMayusculas_encuentraAlUsuario() {
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));

        assertEquals(usuario.getId(), usuarioService.buscarCredenciales(" Juan@Acme.com").orElseThrow().usuario().id());
    }

    @Test
    @DisplayName("HU-03: las credenciales de un usuario activo traen su perfil y el hash de la clave, sin imprimirlo")
    void buscarCredenciales_usuarioActivo_devuelvePerfilYHash() {
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));

        CredencialesUsuario credenciales = usuarioService.buscarCredenciales("juan@acme.com").orElseThrow();

        assertEquals("juan@acme.com", credenciales.usuario().email());
        assertEquals("hashed", credenciales.claveHash());
        assertFalse(credenciales.toString().contains("hashed"));
    }

    @Test
    @DisplayName("HU-03: un usuario desactivado no tiene credenciales para el login")
    void buscarCredenciales_usuarioInactivo_devuelveVacio() {
        usuario.setActivo(false);
        when(usuarioRepository.findByEmail("juan@acme.com")).thenReturn(Optional.of(usuario));

        assertTrue(usuarioService.buscarCredenciales("juan@acme.com").isEmpty());
    }

    @Test
    @DisplayName("HU-03: un correo sin usuario no tiene credenciales")
    void buscarCredenciales_correoDesconocido_devuelveVacio() {
        when(usuarioRepository.findByEmail("nadie@acme.com")).thenReturn(Optional.empty());

        assertTrue(usuarioService.buscarCredenciales("nadie@acme.com").isEmpty());
    }

    @Test
    @DisplayName("HU-02: desactivar usuario pone activo=false")
    void desactivar_exitoso() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.desactivar(1L, AUTOR, 10L);

        assertFalse(usuario.isActivo());
        verify(usuarioRepository).save(usuario);
        verify(sesionService).cerrarTodas(1L, 10L);
    }

    @Test
    @DisplayName("Cambiar el rol cierra las sesiones del usuario: sus tokens llevan el rol de antes")
    void actualizar_cambiaElRol_cierraSusSesiones() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.SOLO_LECTURA, null, usuario.getVersion());

        verify(sesionService).cerrarTodas(1L, 10L);
    }

    @Test
    @DisplayName("Desactivar con PATCH tambien cierra las sesiones del usuario")
    void actualizar_desactiva_cierraSusSesiones() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, null, null, false, usuario.getVersion());

        verify(sesionService).cerrarTodas(1L, 10L);
    }

    @Test
    @DisplayName("Con una version vieja no cambia al usuario ni cierra sus sesiones")
    void actualizar_versionVieja_lanzaConflictoSinGuardar() {
        ReflectionTestUtils.setField(usuario, "version", 3L);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));

        assertThrows(ConflictoDeVersionException.class,
                () -> usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.SOLO_LECTURA, null, 2L));

        assertEquals(RolAcceso.EDITOR, usuario.getRolAcceso());
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(sesionService, never()).cerrarTodas(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Repetir el mismo rol con el usuario activo no cierra sus sesiones")
    void actualizar_sinCambioDeAcceso_noCierraSesiones() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.EDITOR, true, usuario.getVersion());

        verify(sesionService, never()).cerrarTodas(anyLong(), anyLong());
    }

    @Test
    void actualizar_rol_y_estado() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        UsuarioResponse actualizado = usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.ADMINISTRADOR, false,
                usuario.getVersion());

        assertEquals(RolAcceso.ADMINISTRADOR, actualizado.rolAcceso());
        assertFalse(actualizado.activo());
    }

    @Test
    @DisplayName("Nadie desactiva al ultimo administrador activo; se cuenta con la tienda bloqueada")
    void desactivar_ultimoAdministrador_lanzaReglaNegocio() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThrows(ReglaNegocioException.class, () -> usuarioService.desactivar(1L, AUTOR, 10L));

        InOrder orden = inOrder(empresaRepository, usuarioRepository);
        orden.verify(empresaRepository).bloquear(1L);
        orden.verify(usuarioRepository).countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR);
        assertTrue(usuario.isActivo());
        verify(usuarioRepository, never()).save(any());
        verify(sesionService, never()).cerrarTodas(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Desactivar con PATCH al ultimo administrador activo tampoco procede")
    void actualizar_desactivaAlUltimoAdministrador_lanzaReglaNegocio() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(1L);

        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.actualizar(1L, AUTOR, 10L, null, null, false, usuario.getVersion()));

        assertTrue(usuario.isActivo());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un administrador inactivo cambia de rol sin contar administradores: no es de los activos")
    void actualizar_administradorInactivo_noCuentaAdministradores() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        usuario.setActivo(false);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.EDITOR, null, usuario.getVersion());

        assertEquals(RolAcceso.EDITOR, usuario.getRolAcceso());
        verify(empresaRepository, never()).bloquear(anyLong());
        verify(usuarioRepository, never()).countByEmpresaIdAndRolAccesoAndActivoTrue(anyLong(), any());
    }

    @Test
    @DisplayName("Obtener usuario inexistente lanza RecursoNoEncontrado")
    void obtener_inexistente() {
        when(usuarioRepository.findByIdAndEmpresaId(99L, 1L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> usuarioService.obtener(1L, 99L));
    }
}
