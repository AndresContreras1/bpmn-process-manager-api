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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.facimus.procesos.common.EnlaceNoValidoException;
import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.trabajos.ColaDeTrabajos;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.EnlaceDeUnUso;
import com.facimus.procesos.gestion.model.PropositoDeEnlace;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.EnlaceDeUnUsoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.CuentaServiceImpl;
import com.facimus.procesos.gestion.service.impl.PoliticaDeClaves;

import tools.jackson.databind.json.JsonMapper;

/**
 * Los enlaces de un uso que manda el correo: cual sirve, para que, que hace al usarse y en que idioma sale cada correo.
 * La entrega del correo la prueba CorreosDeCuentaTest; el recorrido por HTTP, CuentaIntegracionTest.
 */
@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 11, 12, 0);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EnlaceDeUnUsoRepository enlaceRepository;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private SesionService sesionService;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ColaDeTrabajos cola;
    @Mock
    private PoliticaDeClaves politicaDeClaves;
    @Mock
    private Auditoria auditoria;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private CuentaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CuentaServiceImpl(usuarioRepository, enlaceRepository, configuracionRepository, usuarioService,
                sesionService, historialCambioService, passwordEncoder, cola, politicaDeClaves, jsonMapper,
                Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), auditoria);
    }

    @Test
    @DisplayName("Pedir la verificacion manda el correo en el idioma que la persona eligio")
    void pedirVerificacion() {
        Usuario usuario = usuario();
        usuario.setIdioma("en-US");
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        service.pedirVerificacion(EMPRESA, USUARIO);

        assertThat(pedidoEncolado()).containsEntry("proposito", "VERIFICAR_CORREO")
                .containsEntry("empresaId", 7)
                .containsEntry("usuarioId", 5)
                .containsEntry("email", "ana@acme.com")
                .containsEntry("idioma", "en-US");
        verify(configuracionRepository, never()).findByEmpresaId(anyLong());
    }

    @Test
    @DisplayName("Sin idioma propio, el correo sale en el de la organizacion, o en el de por defecto si no hay")
    void idiomaDeLaOrganizacion() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(ConfiguracionTienda.builder().idioma("fr-FR").build()), Optional.empty());

        service.pedirVerificacion(EMPRESA, USUARIO);
        service.pedirVerificacion(EMPRESA, USUARIO);

        ArgumentCaptor<String> datos = ArgumentCaptor.forClass(String.class);
        verify(cola, times(2)).encolar(eq(EMPRESA), eq("correo-de-cuenta"), datos.capture());
        assertThat(leer(datos.getAllValues().get(0))).containsEntry("idioma", "fr-FR");
        assertThat(leer(datos.getAllValues().get(1))).containsEntry("idioma", Idiomas.POR_DEFECTO);
    }

    @Test
    @DisplayName("Un correo ya verificado no se vuelve a verificar, y quien no esta en la tienda no pide nada")
    void pedirVerificacionSinCorreo() {
        Usuario usuario = usuario();
        usuario.setCorreoVerificadoEn(AHORA.minusDays(1));
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        service.pedirVerificacion(EMPRESA, USUARIO);

        verify(cola, never()).encolar(anyLong(), anyString(), anyString());
        assertThatThrownBy(() -> service.pedirVerificacion(EMPRESA, 6L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Verificar el correo gasta el enlace, anula los demas y marca la hora una sola vez")
    void verificarCorreo() {
        Usuario usuario = usuario();
        EnlaceDeUnUso enlace = enlace(PropositoDeEnlace.VERIFICAR_CORREO, usuario);
        when(enlaceRepository.findByTokenHash(Huella.de("tok"))).thenReturn(Optional.of(enlace));
        when(enlaceRepository.marcarUsado(3L, AHORA)).thenReturn(1);

        service.verificarCorreo("tok");
        assertThat(usuario.getCorreoVerificadoEn()).isEqualTo(AHORA);
        verify(enlaceRepository).anularVigentes(USUARIO, PropositoDeEnlace.VERIFICAR_CORREO, AHORA);

        usuario.setCorreoVerificadoEn(AHORA.minusDays(3));
        service.verificarCorreo("tok");
        assertThat(usuario.getCorreoVerificadoEn()).isEqualTo(AHORA.minusDays(3));
    }

    @Test
    @DisplayName("Un enlace de otro proposito, vencido, usado o gastado por otra peticion no sirve")
    void enlacesQueNoSirven() {
        assertThatThrownBy(() -> service.verificarCorreo("otro")).isInstanceOf(EnlaceNoValidoException.class);

        EnlaceDeUnUso enlace = enlace(PropositoDeEnlace.RECUPERAR_CLAVE, usuario());
        when(enlaceRepository.findByTokenHash(Huella.de("tok"))).thenReturn(Optional.of(enlace));
        assertThatThrownBy(() -> service.verificarCorreo("tok")).isInstanceOf(EnlaceNoValidoException.class);

        enlace.setProposito(PropositoDeEnlace.VERIFICAR_CORREO);
        enlace.setVenceEn(AHORA);
        assertThatThrownBy(() -> service.verificarCorreo("tok")).isInstanceOf(EnlaceNoValidoException.class);

        enlace.setVenceEn(AHORA.plusHours(1));
        enlace.setUsadoEn(AHORA.minusMinutes(1));
        assertThatThrownBy(() -> service.verificarCorreo("tok")).isInstanceOf(EnlaceNoValidoException.class);
        verify(enlaceRepository, never()).marcarUsado(anyLong(), any());

        enlace.setUsadoEn(null);
        assertThatThrownBy(() -> service.verificarCorreo("tok")).isInstanceOf(EnlaceNoValidoException.class);
        verify(enlaceRepository, never()).anularVigentes(anyLong(), any(), any());
        assertThat(enlace.getUsuario().getCorreoVerificadoEn()).isNull();
    }

    @Test
    @DisplayName("La recuperacion se pide con el correo como se escriba, y solo para una cuenta activa")
    void pedirRecuperacion() {
        Usuario usuario = usuario();
        usuario.setIdioma("es-CO");
        when(usuarioRepository.findByEmail("ana@acme.com")).thenReturn(Optional.of(usuario));

        service.pedirRecuperacion("  Ana@ACME.com ");
        assertThat(pedidoEncolado()).containsEntry("proposito", "RECUPERAR_CLAVE").containsEntry("usuarioId", 5)
                .containsEntry("empresaId", 7).containsEntry("email", "ana@acme.com");

        usuario.setActivo(false);
        service.pedirRecuperacion("ana@acme.com");
        verify(cola).encolar(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Recuperar la clave la cambia, verifica el correo, lo anota y cierra todas las sesiones")
    void recuperarClave() {
        Usuario usuario = usuario();
        usuario.setDebeCambiarClave(true);
        when(enlaceRepository.findByTokenHash(Huella.de("tok")))
                .thenReturn(Optional.of(enlace(PropositoDeEnlace.RECUPERAR_CLAVE, usuario)));
        when(enlaceRepository.marcarUsado(3L, AHORA)).thenReturn(1);
        when(passwordEncoder.encode("Nueva-clave-2026")).thenReturn("hash-nuevo");

        service.recuperarClave("tok", "Nueva-clave-2026");

        verify(politicaDeClaves).comprobar("Nueva-clave-2026", "Ana", "ana@acme.com", "Acme");
        assertThat(usuario.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(usuario.isDebeCambiarClave()).isFalse();
        assertThat(usuario.getCorreoVerificadoEn()).isEqualTo(AHORA);
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.USUARIO, USUARIO,
                "Contraseña restablecida con el enlace del correo.");
        verify(sesionService).cerrarTodas(EMPRESA, USUARIO);
    }

    @Test
    @DisplayName("Recuperar la clave con el correo ya verificado no cambia esa fecha")
    void recuperarClaveVerificado() {
        Usuario usuario = usuario();
        usuario.setCorreoVerificadoEn(AHORA.minusDays(2));
        when(enlaceRepository.findByTokenHash(Huella.de("tok")))
                .thenReturn(Optional.of(enlace(PropositoDeEnlace.RECUPERAR_CLAVE, usuario)));
        when(enlaceRepository.marcarUsado(3L, AHORA)).thenReturn(1);

        service.recuperarClave("tok", "Nueva-clave-2026");

        assertThat(usuario.getCorreoVerificadoEn()).isEqualTo(AHORA.minusDays(2));
    }

    @Test
    @DisplayName("Una cuenta desactivada no recupera la clave aunque el enlace sirva")
    void recuperarClaveDesactivada() {
        Usuario usuario = usuario();
        usuario.setActivo(false);
        when(enlaceRepository.findByTokenHash(Huella.de("tok")))
                .thenReturn(Optional.of(enlace(PropositoDeEnlace.RECUPERAR_CLAVE, usuario)));
        when(enlaceRepository.marcarUsado(3L, AHORA)).thenReturn(1);

        assertThatThrownBy(() -> service.recuperarClave("tok", "Nueva-clave-2026"))
                .isInstanceOf(EnlaceNoValidoException.class);
        assertThat(usuario.getPasswordHash()).isEqualTo("x");
        verify(sesionService, never()).cerrarTodas(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Invitar exige el correo verificado del autor, comprueba el del invitado y lo manda en el idioma de la tienda")
    void invitar() {
        Usuario autor = usuario();
        autor.setCorreoVerificadoEn(AHORA.minusDays(1));
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(autor));
        when(configuracionRepository.findByEmpresaId(EMPRESA))
                .thenReturn(Optional.of(ConfiguracionTienda.builder().idioma("en-AU").build()));

        service.invitar(EMPRESA, USUARIO, " Luis@Acme.com ", RolAcceso.EDITOR);

        verify(usuarioService).validarCorreoDisponible("luis@acme.com");
        assertThat(pedidoEncolado()).containsEntry("proposito", "INVITACION").containsEntry("usuarioId", null)
                .containsEntry("email", "luis@acme.com").containsEntry("rolAcceso", "EDITOR")
                .containsEntry("autorId", 5).containsEntry("idioma", "en-AU");
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Invitación por correo a luis@acme.com con rol EDITOR.");
        verify(auditoria).registrar(EMPRESA, USUARIO, "INVITACION_ENVIADA", "EMPRESA", EMPRESA,
                Map.of("rol", "EDITOR"));
    }

    @Test
    @DisplayName("Sin el correo verificado no se invita, y un autor que no esta en la tienda tampoco")
    void invitarSinVerificar() {
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));

        assertThatThrownBy(() -> service.invitar(EMPRESA, USUARIO, "luis@acme.com", RolAcceso.EDITOR))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> service.invitar(EMPRESA, 6L, "luis@acme.com", RolAcceso.EDITOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(usuarioService, never()).validarCorreoDisponible(anyString());
        verify(cola, never()).encolar(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Aceptar la invitacion crea a la persona con el correo y el rol del enlace, y el correo queda verificado")
    void aceptarInvitacion() {
        EnlaceDeUnUso enlace = enlace(PropositoDeEnlace.INVITACION, null);
        enlace.setEmail("luis@acme.com");
        enlace.setRolAcceso(RolAcceso.SOLO_LECTURA);
        when(enlaceRepository.findByTokenHash(Huella.de("tok"))).thenReturn(Optional.of(enlace));
        when(enlaceRepository.marcarUsado(3L, AHORA)).thenReturn(1);
        UsuarioResponse creado = new UsuarioResponse(9L, "Luis", "luis@acme.com", RolAcceso.SOLO_LECTURA, true,
                EMPRESA, 0L, null, null, null, null, false, null);
        when(usuarioService.crearColaborador(EMPRESA, null, "Luis", "luis@acme.com", "Clave-2026",
                RolAcceso.SOLO_LECTURA)).thenReturn(creado);
        Usuario luis = usuario();
        when(usuarioRepository.findByIdAndEmpresaId(9L, EMPRESA)).thenReturn(Optional.of(luis));

        assertThat(service.aceptarInvitacion("tok", "Luis", "Clave-2026")).isSameAs(creado);

        assertThat(luis.getCorreoVerificadoEn()).isEqualTo(AHORA);
        verify(enlaceRepository, never()).anularVigentes(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Los enlaces se olvidan una semana despues de vencer")
    void olvidarVencidos() {
        when(enlaceRepository.borrarVencidosAntesDe(AHORA.minusDays(7))).thenReturn(3);

        assertThat(service.olvidarEnlacesVencidos()).isEqualTo(3);
    }

    private Map<String, Object> pedidoEncolado() {
        ArgumentCaptor<String> datos = ArgumentCaptor.forClass(String.class);
        verify(cola).encolar(eq(EMPRESA), eq("correo-de-cuenta"), datos.capture());
        return leer(datos.getValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> leer(String json) {
        return jsonMapper.readValue(json, Map.class);
    }

    private static EnlaceDeUnUso enlace(PropositoDeEnlace proposito, Usuario usuario) {
        return EnlaceDeUnUso.builder()
                .id(3L)
                .empresa(empresa())
                .usuario(usuario)
                .proposito(proposito)
                .tokenHash(Huella.de("tok"))
                .creadoEn(AHORA.minusHours(1))
                .venceEn(AHORA.plusHours(1))
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
        empresa.setNombre("Acme");
        return empresa;
    }
}
