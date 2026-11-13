package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.PreferenciasResponse;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.PreferenciasServiceImpl;

/**
 * F12: el idioma y la zona de cada persona, que heredan los de su organizacion mientras no elija otros.
 */
@ExtendWith(MockitoExtension.class)
class PreferenciasServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;

    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HistorialCambioService historialCambioService;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private PreferenciasServiceImpl service;

    @Test
    @DisplayName("Sin preferencias propias, la persona ve las de su organizacion")
    void heredaLasDeLaOrganizacion() {
        conConfiguracion();
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));

        assertThat(service.obtener(EMPRESA, USUARIO)).isEqualTo(new PreferenciasResponse("es-CO", "America/Bogota",
                null, null, "es-CO", "America/Bogota"));
    }

    @Test
    @DisplayName("Las propias ganan a las de la organizacion, y las dos se devuelven")
    void lasPropiasGanan() {
        conConfiguracion();
        Usuario usuario = usuario();
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        PreferenciasResponse respuesta = service.cambiarLasPropias(EMPRESA, USUARIO, "fr-FR", " Europe/Paris ");

        assertThat(respuesta).isEqualTo(new PreferenciasResponse("fr-FR", "Europe/Paris", "fr-FR", "Europe/Paris",
                "es-CO", "America/Bogota"));
        assertThat(usuario.getIdioma()).isEqualTo("fr-FR");
        assertThat(usuario.getZonaHoraria()).isEqualTo("Europe/Paris");
    }

    @Test
    @DisplayName("Dejar en blanco una preferencia propia vuelve a la de la organizacion")
    void enBlancoVuelveALaDeLaOrganizacion() {
        conConfiguracion();
        Usuario usuario = usuario();
        usuario.setIdioma("en-US");
        usuario.setZonaHoraria("America/New_York");
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        PreferenciasResponse respuesta = service.cambiarLasPropias(EMPRESA, USUARIO, " ", null);

        assertThat(usuario.getIdioma()).isNull();
        assertThat(usuario.getZonaHoraria()).isNull();
        assertThat(respuesta.idioma()).isEqualTo("es-CO");
        assertThat(respuesta.zonaHoraria()).isEqualTo("America/Bogota");
    }

    @Test
    @DisplayName("Un idioma que la web no tiene, o una zona que no existe, no se guardan")
    void propiasInvalidas() {
        Usuario usuario = usuario();
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.cambiarLasPropias(EMPRESA, USUARIO, "de-DE", null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.cambiarLasPropias(EMPRESA, USUARIO, null, "+05:00"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(usuario.getIdioma()).isNull();
    }

    @Test
    @DisplayName("Las de la organizacion se cambian enteras, se anotan, y las ve quien no tiene propias")
    void cambiarLasDeLaOrganizacion() {
        ConfiguracionTienda configuracion = conConfiguracion();
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario()));

        PreferenciasResponse respuesta = service.cambiarLasDeLaOrganizacion(EMPRESA, USUARIO, "en-AU",
                "Australia/Sydney");

        assertThat(configuracion.getIdioma()).isEqualTo("en-AU");
        assertThat(configuracion.getZonaHoraria()).isEqualTo("Australia/Sydney");
        assertThat(respuesta).isEqualTo(new PreferenciasResponse("en-AU", "Australia/Sydney", null, null, "en-AU",
                "Australia/Sydney"));
        verify(historialCambioService).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.EMPRESA, EMPRESA,
                "Idioma en-AU y zona horaria Australia/Sydney de la organización.");
        verify(auditoria).registrar(EMPRESA, USUARIO, "PREFERENCIAS_CAMBIADAS", "EMPRESA", EMPRESA,
                Map.of("idioma", "en-AU", "zonaHoraria", "Australia/Sydney"));
    }

    @Test
    @DisplayName("La organizacion no se queda sin idioma ni sin zona")
    void organizacionSinIdiomaOZona() {
        assertThatThrownBy(() -> service.cambiarLasDeLaOrganizacion(EMPRESA, USUARIO, " ", "America/Bogota"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.cambiarLasDeLaOrganizacion(EMPRESA, USUARIO, "es-CO", null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.cambiarLasDeLaOrganizacion(EMPRESA, USUARIO, null, ""))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(historialCambioService, never()).registrarDeTienda(any(), any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("Sin configuracion o sin la persona en la tienda no hay preferencias")
    void sinConfiguracionOSinUsuario() {
        assertThatThrownBy(() -> service.obtener(EMPRESA, USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);
        conConfiguracion();
        assertThatThrownBy(() -> service.obtener(EMPRESA, USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    private ConfiguracionTienda conConfiguracion() {
        ConfiguracionTienda configuracion = ConfiguracionTienda.builder().empresa(empresa()).build();
        when(configuracionRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion));
        return configuracion;
    }

    private static Usuario usuario() {
        return Usuario.builder().id(USUARIO).empresa(empresa()).nombre("Ana").email("ana@acme.com")
                .passwordHash("x").build();
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return empresa;
    }
}
