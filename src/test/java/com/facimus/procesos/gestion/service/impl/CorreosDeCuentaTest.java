package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
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

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.CorreoSaliente;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.EnlaceDeUnUso;
import com.facimus.procesos.gestion.model.PropositoDeEnlace;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.EnlaceDeUnUsoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * El trabajo que manda los correos de la cuenta: cuando ya no hace falta mandarlo, que enlace se guarda y cuanto vive,
 * y que datos lleva la plantilla. La entrega de punta a punta la prueba CorreosDeCuentaIntegracionTest.
 */
@ExtendWith(MockitoExtension.class)
class CorreosDeCuentaTest {

    private static final Long EMPRESA = 7L;
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 11, 12, 0);

    @Mock
    private EnlaceDeUnUsoRepository enlaceRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private Redactor redactor;
    @Mock
    private Correo correo;

    @Captor
    private ArgumentCaptor<Map<String, Object>> datos;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CorreoSaliente redactado = new CorreoSaliente("ana@acme.com", "Asunto", "Texto", "<p>Html</p>");

    private CorreosDeCuenta correos;

    @BeforeEach
    void setUp() {
        correos = new CorreosDeCuenta(jsonMapper, enlaceRepository, usuarioRepository, empresaRepository, redactor,
                correo, Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), "https://app.example.com/");
    }

    @Test
    @DisplayName("Es el manejador de los trabajos de correo de la cuenta")
    void tipo() {
        assertThat(correos.tipo()).isEqualTo("correo-de-cuenta");
    }

    @Test
    @DisplayName("Verificar el correo guarda un enlace de 48 horas y manda la plantilla con el enlace a la web")
    void verificarCorreo() {
        conUsuario(usuario());
        conEmpresa();
        when(redactor.redactar(eq("verificar-correo"), eq(Locale.forLanguageTag("en-US")), eq("ana@acme.com"),
                datos.capture())).thenReturn(redactado);
        ArgumentCaptor<EnlaceDeUnUso> enlace = ArgumentCaptor.forClass(EnlaceDeUnUso.class);

        correos.ejecutar(trabajo(PropositoDeEnlace.VERIFICAR_CORREO, 5L, "ana@acme.com", null, null, "en-US"));

        verify(enlaceRepository).save(enlace.capture());
        verify(correo).enviar(redactado);
        Map<String, Object> enviados = datos.getValue();
        String url = (String) enviados.get("enlace");
        assertThat(url).startsWith("https://app.example.com/en-US/verificar-correo#token=");
        String token = url.substring(url.indexOf("#token=") + "#token=".length());
        assertThat(token).hasSize(43);
        EnlaceDeUnUso guardado = enlace.getValue();
        assertThat(guardado.getTokenHash()).isEqualTo(Huella.de(token));
        assertThat(guardado.getEmail()).isEqualTo("ana@acme.com");
        assertThat(guardado.getProposito()).isEqualTo(PropositoDeEnlace.VERIFICAR_CORREO);
        assertThat(guardado.getUsuario().getId()).isEqualTo(5L);
        assertThat(guardado.getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(guardado.getCreadoEn()).isEqualTo(AHORA);
        assertThat(guardado.getVenceEn()).isEqualTo(AHORA.plusHours(48));
        assertThat(enviados).containsEntry("horas", 48L).containsEntry("minutos", 2880L).containsEntry("dias", 2L)
                .containsEntry("organizacion", "Acme").containsEntry("nombre", "Ana")
                .doesNotContainKey("quienInvita");
    }

    @Test
    @DisplayName("Cada correo lleva un token nuevo")
    void tokenNuevo() {
        conUsuario(usuario());
        conEmpresa();
        when(redactor.redactar(anyString(), any(), anyString(), datos.capture())).thenReturn(redactado);

        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "es-CO"));
        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "es-CO"));

        Map<String, Object> primero = datos.getAllValues().get(0);
        Map<String, Object> segundo = datos.getAllValues().get(1);
        assertThat(primero).doesNotContainEntry("enlace", segundo.get("enlace"));
        assertThat((String) primero.get("enlace"))
                .startsWith("https://app.example.com/es-CO/restablecer-clave#token=");
        verify(correo, times(2)).enviar(redactado);
    }

    @Test
    @DisplayName("La recuperacion sale aunque el correo ya este verificado, y vive quince minutos")
    void recuperarConCorreoVerificado() {
        Usuario usuario = usuario();
        usuario.setCorreoVerificadoEn(AHORA.minusDays(1));
        conUsuario(usuario);
        conEmpresa();
        when(redactor.redactar(eq("recuperar-clave"), any(), eq("ana@acme.com"), datos.capture()))
                .thenReturn(redactado);
        ArgumentCaptor<EnlaceDeUnUso> enlace = ArgumentCaptor.forClass(EnlaceDeUnUso.class);

        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "fr-FR"));

        verify(enlaceRepository).save(enlace.capture());
        assertThat(enlace.getValue().getVenceEn()).isEqualTo(AHORA.plusMinutes(15));
        assertThat(datos.getValue()).containsEntry("minutos", 15L).containsEntry("horas", 0L);
        verify(correo).enviar(redactado);
    }

    @Test
    @DisplayName("Una invitacion sale sin usuario, con el rol, quien invita y el idioma de por defecto si no trae")
    void invitacion() {
        conEmpresa();
        Usuario luis = usuario();
        luis.setNombre("Luis");
        when(usuarioRepository.findByIdAndEmpresaId(2L, EMPRESA)).thenReturn(Optional.of(luis));
        when(redactor.redactar(eq("invitacion"), eq(Locale.forLanguageTag("es-CO")), eq("eva@acme.com"),
                datos.capture())).thenReturn(redactado);
        ArgumentCaptor<EnlaceDeUnUso> enlace = ArgumentCaptor.forClass(EnlaceDeUnUso.class);

        correos.ejecutar(trabajo(PropositoDeEnlace.INVITACION, null, "eva@acme.com", RolAcceso.EDITOR, 2L, null));

        verify(enlaceRepository).save(enlace.capture());
        assertThat(enlace.getValue().getUsuario()).isNull();
        assertThat(enlace.getValue().getRolAcceso()).isEqualTo(RolAcceso.EDITOR);
        assertThat(enlace.getValue().getVenceEn()).isEqualTo(AHORA.plusDays(7));
        assertThat(datos.getValue()).containsEntry("quienInvita", "Luis").containsEntry("dias", 7L)
                .doesNotContainKey("nombre");
        verify(correo).enviar(redactado);
    }

    @Test
    @DisplayName("Una invitacion cuyo autor ya no esta sale igual, sin decir quien invita")
    void invitacionSinAutor() {
        conEmpresa();
        when(redactor.redactar(anyString(), any(), anyString(), datos.capture())).thenReturn(redactado);

        correos.ejecutar(trabajo(PropositoDeEnlace.INVITACION, null, "eva@acme.com", RolAcceso.EDITOR, 2L, "en-AU"));

        assertThat(datos.getValue()).doesNotContainKey("quienInvita");
        verify(correo).enviar(redactado);
    }

    @Test
    @DisplayName("Si la persona ya no existe, se desactivo o cambio de correo, el correo ya no sale")
    void yaNoHaceFalta() {
        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "es-CO"));

        Usuario usuario = usuario();
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(usuario));
        usuario.setActivo(false);
        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "es-CO"));

        usuario.setActivo(true);
        usuario.setEmail("ana.nueva@acme.com");
        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "es-CO"));

        usuario.setEmail("ana@acme.com");
        usuario.setCorreoVerificadoEn(AHORA.minusHours(1));
        correos.ejecutar(trabajo(PropositoDeEnlace.VERIFICAR_CORREO, 5L, "ana@acme.com", null, null, "es-CO"));

        verify(enlaceRepository, never()).save(any());
        verify(correo, never()).enviar(any());
    }

    @Test
    @DisplayName("Sin una barra al final de la URL publica el enlace sale igual")
    void urlSinBarra() {
        correos = new CorreosDeCuenta(jsonMapper, enlaceRepository, usuarioRepository, empresaRepository, redactor,
                correo, Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), "https://app.example.com");
        conUsuario(usuario());
        conEmpresa();
        when(redactor.redactar(anyString(), any(), anyString(), datos.capture())).thenReturn(redactado);

        correos.ejecutar(trabajo(PropositoDeEnlace.RECUPERAR_CLAVE, 5L, "ana@acme.com", null, null, "es-CO"));

        assertThat((String) datos.getValue().get("enlace")).startsWith("https://app.example.com/es-CO/");
    }

    private Trabajo trabajo(PropositoDeEnlace proposito, Long usuarioId, String email, RolAcceso rol, Long autorId,
            String idioma) {
        String json = jsonMapper.writeValueAsString(new PedidoDeCorreo(proposito, EMPRESA, usuarioId, email, rol,
                autorId, idioma));
        return new Trabajo(1L, EMPRESA, PedidoDeCorreo.TIPO, json, 1, 5);
    }

    private void conUsuario(Usuario usuario) {
        when(usuarioRepository.findByIdAndEmpresaId(5L, EMPRESA)).thenReturn(Optional.of(usuario));
    }

    private void conEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        empresa.setNombre("Acme");
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(empresa));
    }

    private static Usuario usuario() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return Usuario.builder().id(5L).empresa(empresa).nombre("Ana").email("ana@acme.com").passwordHash("x")
                .build();
    }
}
