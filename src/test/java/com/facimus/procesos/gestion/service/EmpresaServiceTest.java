package com.facimus.procesos.gestion.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

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

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.EmpresaResponse;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;
import com.facimus.procesos.gestion.mapper.EmpresaMapper;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.service.impl.BorradoDeTiendas;
import com.facimus.procesos.gestion.service.impl.EmpresaServiceImpl;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioService usuarioService;

    @Spy
    private EmpresaMapper empresaMapper = Mappers.getMapper(EmpresaMapper.class);

    @Mock
    private HistorialCambioService historialCambioService;

    @Mock
    private ConfiguracionTiendaService configuracionTiendaService;

    @Mock
    private CuentaService cuentaService;

    @Mock
    private BorradoDeTiendas borradoDeTiendas;

    @Mock
    private ExportacionService exportacionService;

    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private EmpresaServiceImpl empresaService;

    @Test
    @DisplayName("HU-01: registrar empresa crea empresa + usuario admin")
    void registrar_crea_empresa_y_admin() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(false);
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> {
            Empresa e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        when(usuarioService.crearColaborador(eq(1L), isNull(), anyString(), anyString(), anyString(), any()))
                .thenReturn(administrador());

        EmpresaResponse resultado = empresaService.registrar("Acme", "900123456", "info@acme.com",
                "Admin", "admin@acme.com", "harbor lights at dusk");

        assertNotNull(resultado);
        assertEquals("Acme", resultado.nombre());
        assertEquals("900123456", resultado.nit());
        verify(usuarioService).crearColaborador(1L, null, "Admin", "admin@acme.com", "harbor lights at dusk",
                RolAcceso.ADMINISTRADOR);
        verify(historialCambioService).registrarDeTienda(eq(1L), eq(7L), eq(RecursoDeHistorial.EMPRESA), eq(1L),
                contains("Acme"));
        verify(cuentaService).pedirVerificacion(1L, 7L);
    }

    private static UsuarioResponse administrador() {
        return new UsuarioResponse(7L, "Admin", "admin@acme.com", RolAcceso.ADMINISTRADOR, true, 1L, 0L, null, null,
                null, null, false, null);
    }

    @Test
    @DisplayName("La empresa propia se entrega con sus datos")
    void obtener_empresaPropia_laDevuelve() {
        Empresa acme = Empresa.builder().nombre("Acme").nit("900123456").correoContacto("info@acme.com").build();
        acme.setId(1L);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(acme));

        assertEquals("900123456", empresaService.obtener(1L, 1L).nit());
    }

    @Test
    @DisplayName("Otra empresa no existe para quien pregunta: ni siquiera se busca")
    void obtener_otraEmpresa_lanzaNoEncontradaSinConsultar() {
        assertThrows(RecursoNoEncontradoException.class, () -> empresaService.obtener(1L, 2L));
        verifyNoInteractions(empresaRepository);
    }

    @Test
    @DisplayName("HU-01: registrar con NIT duplicado lanza excepcion")
    void registrar_nit_duplicado_lanza_excepcion() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> empresaService.registrar("Acme", "900123456", "info@acme.com",
                        "Admin", "admin@acme.com", "harbor lights at dusk"));

        assertTrue(ex.getMessage().contains("NIT"));
        verify(empresaRepository, never()).save(any());
    }

    @Test
    @DisplayName("HU-01: si el correo del administrador ya esta registrado, la empresa no se crea")
    void registrar_correoAdminRegistrado_lanzaExcepcionSinCrearLaEmpresa() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(false);
        doThrow(new ReglaNegocioException("Ya existe un usuario registrado con el correo admin@acme.com."))
                .when(usuarioService).validarCorreoDisponible("admin@acme.com");

        assertThrows(ReglaNegocioException.class,
                () -> empresaService.registrar("Acme", "900123456", "info@acme.com",
                        "Admin", "admin@acme.com", "harbor lights at dusk"));

        verify(empresaRepository, never()).save(any());
        verify(usuarioService, never()).crearColaborador(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Dar de baja con el nombre exacto la deja en baja, lo anota y dice cuando se borra")
    void pedirBaja_conElNombre_laDejaEnBaja() {
        Empresa acme = acme();
        when(empresaRepository.bloquear(1L)).thenReturn(Optional.of(acme));
        when(empresaRepository.saveAndFlush(acme)).thenReturn(acme);

        EmpresaResponse respuesta = empresaService.pedirBaja(1L, 7L, "  Acme ", false);

        assertNotNull(acme.getBajaSolicitadaEn());
        assertEquals(acme.getBajaSolicitadaEn(), respuesta.bajaSolicitadaEn());
        assertEquals(acme.getBajaSolicitadaEn().plusDays(30), respuesta.borradoProgramadoPara());
        verify(historialCambioService).registrarDeTienda(1L, 7L, RecursoDeHistorial.EMPRESA, 1L,
                "Tienda dada de baja: hasta el " + acme.getBajaSolicitadaEn().plusDays(30).toLocalDate()
                        + " solo se puede consultar, y ese día se borran sus datos.");
        verify(auditoria).registrar(1L, 7L, "TIENDA_DADA_DE_BAJA", "EMPRESA", 1L, Map.of());
        verifyNoInteractions(exportacionService);
    }

    @Test
    @DisplayName("PR 66: dar de baja con exportar pide tambien la exportacion completa, de quien la da de baja")
    void pedirBaja_conExportacion() {
        Empresa acme = acme();
        when(empresaRepository.bloquear(1L)).thenReturn(Optional.of(acme));
        when(empresaRepository.saveAndFlush(acme)).thenReturn(acme);

        empresaService.pedirBaja(1L, 7L, "Acme", true);

        assertNotNull(acme.getBajaSolicitadaEn());
        verify(exportacionService).pedir(1L, 7L);
    }

    @Test
    @DisplayName("Sin el nombre exacto no se da de baja, y una tienda ya en baja no se da de baja otra vez")
    void pedirBaja_rechazos() {
        Empresa acme = acme();
        when(empresaRepository.bloquear(1L)).thenReturn(Optional.of(acme));

        assertThrows(SolicitudInvalidaException.class, () -> empresaService.pedirBaja(1L, 7L, "acme", false));
        assertNull(acme.getBajaSolicitadaEn());

        LocalDateTime pedida = LocalDateTime.of(2026, 11, 1, 10, 0);
        acme.setBajaSolicitadaEn(pedida);
        ReglaNegocioException yaEnBaja = assertThrows(ReglaNegocioException.class,
                () -> empresaService.pedirBaja(1L, 7L, "Acme", false));
        assertEquals("La tienda ya está dada de baja: sus datos se borran el 2026-12-01.", yaEnBaja.getMessage());
        assertEquals(pedida, acme.getBajaSolicitadaEn());
        assertThrows(RecursoNoEncontradoException.class, () -> empresaService.pedirBaja(2L, 7L, "Acme", false));
        verify(empresaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("Cancelar la baja la quita y lo anota; sin baja pedida no hay nada que cancelar")
    void cancelarBaja() {
        Empresa acme = acme();
        when(empresaRepository.bloquear(1L)).thenReturn(Optional.of(acme));

        assertThrows(ReglaNegocioException.class, () -> empresaService.cancelarBaja(1L, 7L));

        acme.setBajaSolicitadaEn(LocalDateTime.now());
        when(empresaRepository.saveAndFlush(acme)).thenReturn(acme);
        EmpresaResponse respuesta = empresaService.cancelarBaja(1L, 7L);

        assertNull(acme.getBajaSolicitadaEn());
        assertNull(respuesta.bajaSolicitadaEn());
        assertEquals("Acme", respuesta.nombre());
        verify(historialCambioService).registrarDeTienda(1L, 7L, RecursoDeHistorial.EMPRESA, 1L,
                "Baja de la tienda cancelada: vuelve a funcionar como antes.");
        verify(auditoria).registrar(1L, 7L, "BAJA_CANCELADA", "EMPRESA", 1L, Map.of());
        assertThrows(RecursoNoEncontradoException.class, () -> empresaService.cancelarBaja(2L, 7L));
    }

    @Test
    @DisplayName("El borrado se programa treinta dias despues de pedir la baja; sin baja no hay fecha")
    void borradoProgramado() {
        LocalDateTime pedida = LocalDateTime.of(2026, 11, 1, 10, 0);
        when(empresaRepository.bajaSolicitadaEn(1L)).thenReturn(Optional.of(pedida));

        assertEquals(Optional.of(LocalDateTime.of(2026, 12, 1, 10, 0)), empresaService.borradoProgramado(1L));
        assertTrue(empresaService.borradoProgramado(2L).isEmpty());
    }

    @Test
    @DisplayName("Se borran las tiendas que cumplieron la gracia, cada una por su lado, y se cuentan")
    void borrarLasDadasDeBaja() {
        ArgumentCaptor<LocalDateTime> limite = ArgumentCaptor.forClass(LocalDateTime.class);
        when(empresaRepository.conLaBajaAntesDe(limite.capture())).thenReturn(List.of(3L, 4L));

        assertEquals(2, empresaService.borrarLasDadasDeBaja());

        ArgumentCaptor<LocalDateTime> ahora = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(borradoDeTiendas).borrar(eq(3L), ahora.capture());
        verify(borradoDeTiendas).borrar(4L, ahora.getValue());
        assertEquals(ahora.getValue().minusDays(30), limite.getValue());
    }

    private static Empresa acme() {
        Empresa acme = Empresa.builder().nombre("Acme").nit("900123456").correoContacto("info@acme.com").build();
        acme.setId(1L);
        return acme;
    }
}
