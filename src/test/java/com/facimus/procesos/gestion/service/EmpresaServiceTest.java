package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolAcceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private EmpresaService empresaService;

    @Test
    @DisplayName("HU-01: registrar empresa crea empresa + usuario admin")
    void registrar_crea_empresa_y_admin() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(false);
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(inv -> {
            Empresa e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        Empresa resultado = empresaService.registrar("Acme", "900123456", "info@acme.com",
                "Admin", "admin@acme.com", "secret123");

        assertNotNull(resultado);
        assertEquals("Acme", resultado.getNombre());
        assertEquals("900123456", resultado.getNit());
        verify(usuarioService).crearUsuario(resultado, "Admin", "admin@acme.com", "secret123",
                RolAcceso.ADMINISTRADOR);
    }

    @Test
    @DisplayName("HU-01: registrar con NIT duplicado lanza excepcion")
    void registrar_nit_duplicado_lanza_excepcion() {
        when(empresaRepository.existsByNit("900123456")).thenReturn(true);

        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> empresaService.registrar("Acme", "900123456", "info@acme.com",
                        "Admin", "admin@acme.com", "secret123"));

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
                        "Admin", "admin@acme.com", "secret123"));

        verify(empresaRepository, never()).save(any());
        verify(usuarioService, never()).crearUsuario(any(), any(), any(), any(), any());
    }
}
