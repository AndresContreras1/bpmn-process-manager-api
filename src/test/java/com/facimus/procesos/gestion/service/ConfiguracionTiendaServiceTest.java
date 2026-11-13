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
import org.springframework.test.util.ReflectionTestUtils;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.mapper.ConfiguracionTiendaMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.model.PoliticaSegundoFactor;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.ConfiguracionTiendaServiceImpl;

/**
 * La fila de configuracion de cada tienda: que cambia cada edicion, que queda en el historial y quien puede tocar la
 * estructura de los diagramas segun la politica.
 */
@ExtendWith(MockitoExtension.class)
class ConfiguracionTiendaServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Long USUARIO = 5L;

    @Mock
    private ConfiguracionTiendaRepository configuracionTiendaRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private HistorialCambioService historialCambioService;
    @Spy
    private ConfiguracionTiendaMapper configuracionTiendaMapper = Mappers.getMapper(ConfiguracionTiendaMapper.class);
    @Mock
    private Auditoria auditoria;
    @Mock
    private Permisos permisos;

    @InjectMocks
    private ConfiguracionTiendaServiceImpl service;

    @Test
    @DisplayName("Una tienda nueva nace con su idioma, su zona y la estructura abierta a los editores")
    void crearPara() {
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(empresa()));
        ArgumentCaptor<ConfiguracionTienda> guardada = ArgumentCaptor.forClass(ConfiguracionTienda.class);

        service.crearPara(EMPRESA, "en-US", "America/New_York");

        verify(configuracionTiendaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getEmpresa().getId()).isEqualTo(EMPRESA);
        assertThat(guardada.getValue().getPoliticaEstructura()).isEqualTo(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
        assertThat(guardada.getValue().getIdioma()).isEqualTo("en-US");
        assertThat(guardada.getValue().getZonaHoraria()).isEqualTo("America/New_York");
        assertThatThrownBy(() -> service.crearPara(8L, "es-CO", "America/Bogota"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Obtener devuelve la configuracion de la tienda, y sin fila no hay configuracion")
    void obtener() {
        assertThatThrownBy(() -> service.obtener(EMPRESA)).isInstanceOf(RecursoNoEncontradoException.class);
        when(configuracionTiendaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion()));

        ConfiguracionTiendaResponse respuesta = service.obtener(EMPRESA);

        assertThat(respuesta.politicaEstructura()).isEqualTo(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
        assertThat(respuesta.inactividadSesionMinutos()).isEqualTo(60);
        assertThat(respuesta.duracionSesionHoras()).isEqualTo(24);
    }

    @Test
    @DisplayName("Editar con todo lo nuevo lo guarda y deja una entrada del historial por cada cambio")
    void editarTodo() {
        ConfiguracionTienda configuracion = configuracion();
        guardaLaQueLlega(configuracion);
        ParametrosSimulacion parametros = ParametrosSimulacion.builder()
                .reglaRechazoPagos("payment.status == APPROVED and order.total > 100")
                .build();

        ConfiguracionTiendaResponse respuesta = service.editar(EMPRESA, USUARIO, PoliticaEstructura.SOLO_ADMINISTRADOR,
                ModoSimulacion.AUTOMATICO, parametros, new LimitesDeSesion(15, 1), PoliticaSegundoFactor.TODOS, true,
                null);

        assertThat(configuracion.getModoSimulacion()).isEqualTo(ModoSimulacion.AUTOMATICO);
        assertThat(configuracion.getSimulacion()).isSameAs(parametros);
        assertThat(respuesta.politicaEstructura()).isEqualTo(PoliticaEstructura.SOLO_ADMINISTRADOR);
        assertThat(respuesta.inactividadSesionMinutos()).isEqualTo(15);
        assertThat(respuesta.duracionSesionHoras()).isEqualTo(1);
        assertThat(respuesta.politicaSegundoFactor()).isEqualTo(PoliticaSegundoFactor.TODOS);
        assertThat(respuesta.soloEntradaExterna()).isTrue();
        verifyHistorial("La estructura de los diagramas queda reservada a los administradores.");
        verifyHistorial("Las sesiones se cierran tras 15 minutos sin renovarse y una hora después del login.");
        verifyHistorial("Todos tienen que usar un segundo factor.");
        verifyHistorial("Se entra solo con Google o Microsoft; los administradores conservan su contraseña.");
        verify(auditoria).registrar(EMPRESA, USUARIO, "CONFIGURACION_CAMBIADA", "EMPRESA", EMPRESA, Map.of(
                "politicaEstructura", "SOLO_ADMINISTRADOR",
                "politicaSegundoFactor", "TODOS",
                "soloEntradaExterna", true,
                "inactividadSesionMinutos", 15,
                "duracionSesionHoras", 1));
    }

    @Test
    @DisplayName("Lo que no llega, o llega igual, no cambia ni se anota: solo queda la entrada de la estructura")
    void editarSinCambios() {
        ConfiguracionTienda configuracion = configuracion();
        guardaLaQueLlega(configuracion);
        ParametrosSimulacion antes = configuracion.getSimulacion();

        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null,
                new LimitesDeSesion(60, 24), PoliticaSegundoFactor.OPCIONAL, false, null);
        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null,
                LimitesDeSesion.SIN_CAMBIOS, null, null, null);

        assertThat(configuracion.getModoSimulacion()).isEqualTo(ModoSimulacion.MANUAL);
        assertThat(configuracion.getSimulacion()).isSameAs(antes);
        assertThat(configuracion.getInactividadSesionMinutos()).isEqualTo(60);
        assertThat(configuracion.getDuracionSesionHoras()).isEqualTo(24);
        verify(historialCambioService, times(2)).registrarDeTienda(eq(EMPRESA), eq(USUARIO),
                eq(RecursoDeHistorial.EMPRESA), eq(EMPRESA), anyString());
        verifyHistorial(2, "Los editores vuelven a poder cambiar la estructura de los diagramas.");
    }

    @Test
    @DisplayName("Cambiar solo la duracion de las sesiones tambien se anota, con las horas en plural")
    void editarDuracion() {
        guardaLaQueLlega(configuracion());

        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null,
                new LimitesDeSesion(null, 2), null, null, null);

        verifyHistorial("Las sesiones se cierran tras 60 minutos sin renovarse y 2 horas después del login.");
    }

    @Test
    @DisplayName("Cambiar solo la inactividad se anota con la duracion que ya tenia")
    void editarInactividad() {
        guardaLaQueLlega(configuracion());

        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null,
                new LimitesDeSesion(45, null), null, null, null);

        verifyHistorial("Las sesiones se cierran tras 45 minutos sin renovarse y 24 horas después del login.");
    }

    @Test
    @DisplayName("Cada politica de segundo factor se anota con su frase, y volver a la contrasena tambien")
    void editarPoliticas() {
        ConfiguracionTienda configuracion = configuracion();
        configuracion.setPoliticaSegundoFactor(PoliticaSegundoFactor.TODOS);
        configuracion.setSoloEntradaExterna(true);
        guardaLaQueLlega(configuracion);

        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null,
                LimitesDeSesion.SIN_CAMBIOS, PoliticaSegundoFactor.ADMINISTRADORES, false, null);
        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, null,
                LimitesDeSesion.SIN_CAMBIOS, PoliticaSegundoFactor.OPCIONAL, null, null);

        verifyHistorial("Los administradores tienen que usar un segundo factor.");
        verifyHistorial("Se vuelve a poder entrar con contraseña.");
        verifyHistorial("El segundo factor queda a elección de cada persona.");
        assertThat(configuracion.isSoloEntradaExterna()).isFalse();
        assertThat(configuracion.getPoliticaSegundoFactor()).isEqualTo(PoliticaSegundoFactor.OPCIONAL);
    }

    @Test
    @DisplayName("Una regla de rechazo mal escrita no se guarda; una vacia si")
    void reglaDeRechazo() {
        ConfiguracionTienda configuracion = configuracion();
        when(configuracionTiendaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion));
        ParametrosSimulacion malEscrita = ParametrosSimulacion.builder().reglaRechazoPagos("payment.status ==").build();

        assertThatThrownBy(() -> service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null,
                malEscrita, LimitesDeSesion.SIN_CAMBIOS, null, null, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageStartingWith("La regla de rechazo de pagos no está bien escrita: ");
        verify(configuracionTiendaRepository, never()).saveAndFlush(any());

        when(configuracionTiendaRepository.saveAndFlush(configuracion)).thenReturn(configuracion);
        ParametrosSimulacion vacia = ParametrosSimulacion.builder().reglaRechazoPagos("  ").build();
        service.editar(EMPRESA, USUARIO, PoliticaEstructura.ADMINISTRADOR_Y_EDITOR, null, vacia,
                LimitesDeSesion.SIN_CAMBIOS, null, null, null);
        assertThat(configuracion.getSimulacion()).isSameAs(vacia);
    }

    @Test
    @DisplayName("Editar con una version vieja choca y no guarda nada")
    void editarVersionVieja() {
        ConfiguracionTienda configuracion = configuracion();
        ReflectionTestUtils.setField(configuracion, "version", 3L);
        when(configuracionTiendaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion));

        assertThatThrownBy(() -> service.editar(EMPRESA, USUARIO, PoliticaEstructura.SOLO_ADMINISTRADOR, null, null,
                LimitesDeSesion.SIN_CAMBIOS, null, null, 2L)).isInstanceOf(ConflictoDeVersionException.class);
        assertThat(configuracion.getPoliticaEstructura()).isEqualTo(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
        verify(configuracionTiendaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("El reloj de la simulacion se lee de la tienda y avanza los ticks pedidos")
    void reloj() {
        assertThatThrownBy(() -> service.reloj(EMPRESA)).isInstanceOf(RecursoNoEncontradoException.class);
        when(configuracionTiendaRepository.relojDe(EMPRESA)).thenReturn(Optional.of(4));
        assertThat(service.reloj(EMPRESA)).isEqualTo(4);

        ConfiguracionTienda configuracion = configuracion();
        configuracion.setReloj(3);
        guardaLaQueLlega(configuracion);
        assertThat(service.avanzarReloj(EMPRESA, 2)).isEqualTo(5);
        assertThat(configuracion.getReloj()).isEqualTo(5);

        when(configuracionTiendaRepository.empresasEnAutomatico()).thenReturn(List.of(7L, 9L));
        assertThat(service.tiendasEnAutomatico()).containsExactly(7L, 9L);
    }

    @Test
    @DisplayName("Con la estructura reservada, solo un administrador la edita; abierta, tambien un editor")
    void puedeEditarEstructura() {
        ConfiguracionTienda configuracion = configuracion();
        configuracion.setPoliticaEstructura(PoliticaEstructura.SOLO_ADMINISTRADOR);
        when(configuracionTiendaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion));
        Usuario usuario = usuario(RolAcceso.EDITOR);
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA)).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.exigirPuedeEditarEstructura(EMPRESA, USUARIO))
                .isInstanceOf(SinPermisoException.class)
                .hasMessage("La tienda reserva la estructura de los diagramas a los administradores.");
        usuario.setRolAcceso(RolAcceso.ADMINISTRADOR);
        service.exigirPuedeEditarEstructura(EMPRESA, USUARIO);

        configuracion.setPoliticaEstructura(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR);
        usuario.setRolAcceso(RolAcceso.EDITOR);
        service.exigirPuedeEditarEstructura(EMPRESA, USUARIO);
        usuario.setRolAcceso(RolAcceso.SOLO_LECTURA);
        assertThatThrownBy(() -> service.exigirPuedeEditarEstructura(EMPRESA, USUARIO))
                .isInstanceOf(SinPermisoException.class);
    }

    @Test
    @DisplayName("Quien no esta en la tienda no edita estructura")
    void sinUsuario() {
        assertThatThrownBy(() -> service.exigirPuedeEditarEstructura(EMPRESA, USUARIO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(configuracionTiendaRepository, never()).findByEmpresaId(anyLong());
    }

    @Test
    @DisplayName("Un permiso de modelar sobre el proceso basta; sin el, decide la politica de la tienda")
    void permisoSobreElProceso() {
        when(permisos.concedido(EMPRESA, USUARIO, Accion.MODELAR, Recurso.proceso(30L))).thenReturn(true, false);
        service.exigirPuedeEditarEstructura(EMPRESA, USUARIO, 30L);
        verify(usuarioRepository, never()).findByIdAndEmpresaId(anyLong(), anyLong());

        ConfiguracionTienda configuracion = configuracion();
        configuracion.setPoliticaEstructura(PoliticaEstructura.SOLO_ADMINISTRADOR);
        when(configuracionTiendaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion));
        when(usuarioRepository.findByIdAndEmpresaId(USUARIO, EMPRESA))
                .thenReturn(Optional.of(usuario(RolAcceso.EDITOR)));
        assertThatThrownBy(() -> service.exigirPuedeEditarEstructura(EMPRESA, USUARIO, 30L))
                .isInstanceOf(SinPermisoException.class);
    }

    private void guardaLaQueLlega(ConfiguracionTienda configuracion) {
        when(configuracionTiendaRepository.findByEmpresaId(EMPRESA)).thenReturn(Optional.of(configuracion));
        when(configuracionTiendaRepository.saveAndFlush(configuracion)).thenReturn(configuracion);
    }

    private void verifyHistorial(String descripcion) {
        verifyHistorial(1, descripcion);
    }

    private void verifyHistorial(int veces, String descripcion) {
        verify(historialCambioService, times(veces)).registrarDeTienda(EMPRESA, USUARIO, RecursoDeHistorial.EMPRESA,
                EMPRESA, descripcion);
    }

    private static ConfiguracionTienda configuracion() {
        return ConfiguracionTienda.builder().id(1L).empresa(empresa()).build();
    }

    private static Usuario usuario(RolAcceso rol) {
        return Usuario.builder().id(USUARIO).empresa(empresa()).nombre("Ana").email("ana@acme.com")
                .passwordHash("x").rolAcceso(rol).build();
    }

    private static Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        return empresa;
    }
}
