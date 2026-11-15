package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.CredencialesUsuario;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.UsuarioMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.EnlaceDeUnUsoRepository;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.PoliticaDeClaves;
import com.facimus.procesos.gestion.service.impl.UsuarioServiceImpl;

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

    @Mock
    private PoliticaDeClaves politicaDeClaves;

    @Mock
    private MembresiaRolRepository membresiaRolRepository;

    @Mock
    private EnlaceDeUnUsoRepository enlaceDeUnUsoRepository;

    @Mock
    private SegundoFactorService segundoFactorService;

    @Mock
    private IdentidadExternaService identidadExternaService;

    @Mock
    private Auditoria auditoria;

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
    @DisplayName("La clave elegida pasa la politica con el nombre, el correo ya normalizado y la tienda")
    void crearColaborador_conClave_pasaLaPolitica() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("nuevo@acme.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.crearColaborador(1L, 9L, "Nuevo", " Nuevo@ACME.com", "pass", RolAcceso.EDITOR);

        verify(politicaDeClaves).comprobar("pass", "Nuevo", "nuevo@acme.com", "Acme");
    }

    @Test
    @DisplayName("Una clave que la politica rechaza no crea al usuario")
    void crearColaborador_claveRechazada_noGuarda() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("nuevo@acme.com")).thenReturn(false);
        doThrow(new SolicitudInvalidaException("Muy corta.")).when(politicaDeClaves)
                .comprobar("pass", "Nuevo", "nuevo@acme.com", "Acme");

        assertThrows(SolicitudInvalidaException.class, () -> usuarioService.crearColaborador(1L, 9L, "Nuevo",
                "nuevo@acme.com", "pass", RolAcceso.EDITOR));

        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("D17: la temporal no pasa por la politica; son 96 bits al azar en 16 caracteres")
    void crearColaborador_sinClave_generaUnaTemporalDe16Caracteres() {
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.existsByEmail("nuevo@acme.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse result = usuarioService.crearColaborador(1L, 9L, "Nuevo", "nuevo@acme.com", null,
                RolAcceso.EDITOR);

        assertEquals(16, result.claveTemporal().length());
        assertTrue(result.debeCambiarClave());
        verifyNoInteractions(politicaDeClaves);
    }

    @Test
    @DisplayName("Cambiar la clave propia pasa la nueva por la politica, con el nombre, el correo y la tienda")
    void cambiarClavePropia_pasaLaNuevaPorLaPolitica() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("la de ahora", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("una frase nueva y larga", "hashed")).thenReturn(false);
        when(passwordEncoder.encode("una frase nueva y larga")).thenReturn("otro-hash");
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.cambiarClavePropia(1L, 10L, "la de ahora", "una frase nueva y larga");

        verify(politicaDeClaves).comprobar("una frase nueva y larga", "Juan", "juan@acme.com", "Acme");
        assertEquals("otro-hash", usuario.getPasswordHash());
    }

    @Test
    @DisplayName("Una clave nueva que la politica rechaza deja la anterior")
    void cambiarClavePropia_rechazada_dejaLaAnterior() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("la de ahora", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("juan y su perro fiel", "hashed")).thenReturn(false);
        doThrow(new SolicitudInvalidaException("Lleva el nombre.")).when(politicaDeClaves)
                .comprobar("juan y su perro fiel", "Juan", "juan@acme.com", "Acme");

        assertThrows(SolicitudInvalidaException.class,
                () -> usuarioService.cambiarClavePropia(1L, 10L, "la de ahora", "juan y su perro fiel"));

        assertEquals("hashed", usuario.getPasswordHash());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Rehacer el hash va directo a la base, acotado por la tienda, sin historial ni cierre de sesiones")
    void renovarHash_soloCambiaElHash() {
        usuarioService.renovarHash(1L, 10L, "{bcrypt}otro-hash");

        verify(usuarioRepository).renovarHash(1L, 10L, "{bcrypt}otro-hash");
        verifyNoInteractions(historialCambioService, sesionService);
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

    @Test
    @DisplayName("Crear en una tienda que no existe no guarda a nadie")
    void crearColaborador_sinEmpresa_lanzaRecursoNoEncontrado() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> usuarioService.crearColaborador(9L, AUTOR, "Nuevo", "nuevo@acme.com", "pass", RolAcceso.EDITOR));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Renombrar anota solo el nombre nuevo, sin cerrar sesiones; el mismo nombre no cuenta como cambio")
    void actualizar_renombra_anotaElCambio() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, "Juan Perez", null, null, usuario.getVersion());

        assertEquals("Juan Perez", usuario.getNombre());
        verify(historialCambioService).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Usuario \"Juan Perez\" renombrado.");
        verify(auditoria).registrar(1L, AUTOR, "USUARIO_CAMBIADO", "USUARIO", 10L,
                Map.of("rol", "EDITOR", "activo", true));
        verify(sesionService, never()).cerrarTodas(anyLong(), anyLong());
        verify(empresaRepository, never()).bloquear(anyLong());

        usuarioService.actualizar(1L, AUTOR, 10L, "Juan Perez", null, true, usuario.getVersion());
        verify(historialCambioService).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Usuario \"Juan Perez\" reactivado.");
    }

    @Test
    @DisplayName("Renombrar, cambiar el rol y desactivar a la vez se anota en una sola entrada, en ese orden")
    void actualizar_variosCambios_unaEntrada() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, "Juana", RolAcceso.SOLO_LECTURA, false, usuario.getVersion());

        verify(historialCambioService).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Usuario \"Juana\" renombrado, con rol SOLO_LECTURA, desactivado.");
        verify(auditoria).registrar(1L, AUTOR, "USUARIO_CAMBIADO", "USUARIO", 10L,
                Map.of("rol", "SOLO_LECTURA", "activo", false));
    }

    @Test
    @DisplayName("Un cambio de rol que no desactiva se anota sin decir reactivado")
    void actualizar_soloRol_noDiceReactivado() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.ADMINISTRADOR, true, usuario.getVersion());

        verify(historialCambioService).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Usuario \"Juan\" con rol ADMINISTRADOR.");
    }

    @Test
    @DisplayName("Nadie se desactiva a si mismo, ni con PATCH ni con DELETE")
    void desactivarse_aSiMismo_lanzaReglaNegocio() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        Long version = usuario.getVersion();

        ReglaNegocioException conPatch = assertThrows(ReglaNegocioException.class,
                () -> usuarioService.actualizar(1L, 10L, 10L, null, null, false, version));
        assertEquals("No puede desactivar su propia cuenta.", conPatch.getMessage());
        assertThrows(ReglaNegocioException.class, () -> usuarioService.desactivar(1L, 10L, 10L));

        assertTrue(usuario.isActivo());
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Quitarle el rol al ultimo administrador activo no procede; darselo a otro no cuenta administradores")
    void actualizar_quitaElRolAlUltimoAdministrador_lanzaReglaNegocio() {
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.countByEmpresaIdAndRolAccesoAndActivoTrue(1L, RolAcceso.ADMINISTRADOR)).thenReturn(1L);
        Long version = usuario.getVersion();

        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.actualizar(1L, AUTOR, 10L, null, RolAcceso.EDITOR, null, version));
        assertEquals(RolAcceso.ADMINISTRADOR, usuario.getRolAcceso());

        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);
        usuarioService.actualizar(1L, AUTOR, 10L, "Juan", RolAcceso.ADMINISTRADOR, null, usuario.getVersion());
        verify(empresaRepository).bloquear(1L);
    }

    @Test
    @DisplayName("Desactivar anota el nombre y lo deja en la auditoria")
    void desactivar_anota() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));

        usuarioService.desactivar(1L, AUTOR, 10L);

        verify(historialCambioService).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Usuario \"Juan\" desactivado.");
        verify(auditoria).registrar(1L, AUTOR, "USUARIO_DESACTIVADO", "USUARIO", 10L, Map.of());
    }

    @Test
    @DisplayName("Restablecer la clave da una temporal distinta cada vez, obliga a cambiarla y cierra las sesiones")
    void restablecerClave_daUnaTemporal() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        UsuarioResponse primera = usuarioService.restablecerClave(1L, AUTOR, 10L);
        UsuarioResponse segunda = usuarioService.restablecerClave(1L, AUTOR, 10L);

        assertEquals(16, primera.claveTemporal().length());
        assertNotEquals(primera.claveTemporal(), segunda.claveTemporal());
        assertEquals("hash:" + segunda.claveTemporal(), usuario.getPasswordHash());
        assertTrue(usuario.isDebeCambiarClave());
        assertEquals(10L, primera.id());
        verify(usuarioRepository, times(2)).saveAndFlush(usuario);
        verify(historialCambioService, times(2)).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Contraseña de \"Juan\" restablecida.");
        verify(auditoria, times(2)).registrar(1L, AUTOR, "CLAVE_RESTABLECIDA", "USUARIO", 10L, Map.of());
        verify(sesionService, times(2)).cerrarTodas(1L, 10L);
    }

    @Test
    @DisplayName("A una persona anonimizada no se le restablece la clave ni se la edita")
    void anonimizada_noSeEdita() {
        usuario.setAnonimizadoEn(LocalDateTime.now());
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        Long version = usuario.getVersion();

        assertThrows(ReglaNegocioException.class, () -> usuarioService.restablecerClave(1L, AUTOR, 10L));
        assertThrows(ReglaNegocioException.class,
                () -> usuarioService.actualizar(1L, AUTOR, 10L, "Otro", null, null, version));
        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Anonimizar borra el nombre, el correo y todo lo que da acceso, y seudonimiza el historial")
    void anonimizar_borraLosDatosPersonales() {
        usuario.setCorreoVerificadoEn(LocalDateTime.now());
        usuario.setDebeCambiarClave(true);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        ArgumentCaptor<String> clave = ArgumentCaptor.forClass(String.class);
        when(passwordEncoder.encode(clave.capture())).thenReturn("hash-de-nadie");

        usuarioService.anonimizar(1L, AUTOR, 10L);

        assertEquals("Persona anonimizada 10", usuario.getNombre());
        assertEquals("anonimizada-10@anonimo.invalid", usuario.getEmail());
        assertEquals("hash-de-nadie", usuario.getPasswordHash());
        assertEquals(32, clave.getValue().length());
        assertFalse(usuario.isActivo());
        assertFalse(usuario.isDebeCambiarClave());
        assertNull(usuario.getCorreoVerificadoEn());
        assertNotNull(usuario.getAnonimizadoEn());
        verify(usuarioRepository).saveAndFlush(usuario);
        verify(membresiaRolRepository).borrarLasDe(1L, 10L);
        verify(enlaceDeUnUsoRepository).borrarLosDe(1L, 10L, "juan@acme.com");
        verify(segundoFactorService).borrarDe(1L, 10L);
        verify(identidadExternaService).borrarDe(1L, 10L);
        verify(sesionService).cerrarTodas(1L, 10L);
        verify(historialCambioService).seudonimizarUsuario(1L, 10L, "Persona anonimizada 10", "juan@acme.com",
                "anonimizada-10@anonimo.invalid");
        verify(historialCambioService).registrarDeTienda(1L, AUTOR, RecursoDeHistorial.USUARIO, 10L,
                "Usuario anonimizado: su nombre y su correo ya no constan en ninguna parte.");
        verify(auditoria).registrar(1L, AUTOR, "USUARIO_ANONIMIZADO", "USUARIO", 10L, Map.of());
    }

    @Test
    @DisplayName("Anonimizar dos veces no hace nada la segunda, y nadie se anonimiza a si mismo")
    void anonimizar_repetidoOPropio() {
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));

        assertThrows(ReglaNegocioException.class, () -> usuarioService.anonimizar(1L, 10L, 10L));
        assertEquals("Juan", usuario.getNombre());

        usuario.setAnonimizadoEn(LocalDateTime.now());
        usuarioService.anonimizar(1L, AUTOR, 10L);
        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(historialCambioService, never()).seudonimizarUsuario(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Cambiar la clave propia quita la obligacion de cambiarla y lo anota; la actual tiene que coincidir")
    void cambiarClavePropia_reglas() {
        usuario.setDebeCambiarClave(true);
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mala", "hashed")).thenReturn(false);

        assertThrows(SolicitudInvalidaException.class,
                () -> usuarioService.cambiarClavePropia(1L, 10L, "mala", "Otra-clave-2026"));

        when(passwordEncoder.matches("actual", "hashed")).thenReturn(true);
        assertThrows(SolicitudInvalidaException.class,
                () -> usuarioService.cambiarClavePropia(1L, 10L, "actual", "actual"));

        when(passwordEncoder.matches("Otra-clave-2026", "hashed")).thenReturn(false);
        when(passwordEncoder.encode("Otra-clave-2026")).thenReturn("hash-nuevo");
        UsuarioResponse respuesta = usuarioService.cambiarClavePropia(1L, 10L, "actual", "Otra-clave-2026");

        assertEquals(10L, respuesta.id());
        assertFalse(respuesta.debeCambiarClave());
        assertEquals("hash-nuevo", usuario.getPasswordHash());
        verify(historialCambioService).registrarDeTienda(1L, 10L, RecursoDeHistorial.USUARIO, 10L,
                "Usuario \"Juan\" cambió su contraseña.");
    }

    @Test
    @DisplayName("Buscar pagina los usuarios de la tienda y obtener devuelve uno")
    void buscarYObtener() {
        PageRequest pagina = PageRequest.of(0, 10);
        when(usuarioRepository.findAll(ArgumentMatchers.<Specification<Usuario>>any(), eq(pagina)))
                .thenReturn(new PageImpl<>(List.of(usuario), pagina, 1));
        when(usuarioRepository.findByIdAndEmpresaId(10L, 1L)).thenReturn(Optional.of(usuario));

        assertEquals(List.of(10L), usuarioService.buscar(1L, "Ju", false, pagina).content().stream()
                .map(UsuarioResponse::id).toList());
        assertEquals("juan@acme.com", usuarioService.obtener(1L, 10L).email());
    }
}
