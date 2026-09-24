package com.facimus.procesos.gestion.service;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.dto.response.RolProcesoVistaResponse;
import com.facimus.procesos.gestion.mapper.RolProcesoMapper;
import com.facimus.procesos.gestion.model.Empresa;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.service.UsoDeRoles;
import com.facimus.procesos.gestion.service.impl.RolProcesoServiceImpl;

import org.mapstruct.factory.Mappers;
import org.mockito.Spy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolProcesoServiceTest {

    @Mock
    private RolProcesoRepository rolProcesoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private UsoDeRoles usoDeRoles;

    @Spy
    private RolProcesoMapper rolProcesoMapper = Mappers.getMapper(RolProcesoMapper.class);

    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private RolProcesoServiceImpl rolProcesoService;

    private Empresa empresa;
    private RolProceso rol;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(1L);

        rol = new RolProceso();
        rol.setId(5L);
        rol.setEmpresa(empresa);
        rol.setNombre("Analista");
        rol.setActivo(true);
    }

    @Test
    @DisplayName("HU-17: crear rol con nombre unico")
    void crear_exitoso() {
        when(rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCaseAndActivoTrue(1L, "Auditor")).thenReturn(false);
        when(empresaRepository.findById(1L)).thenReturn(Optional.of(empresa));
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));

        RolProcesoVistaResponse result = rolProcesoService.crear(1L, 10L, "Auditor", "Revisa procesos");

        assertEquals("Auditor", result.nombre());
        assertFalse(result.enUso());
    }

    @Test
    @DisplayName("HU-19: eliminar rol en uso lanza excepcion (regla 13)")
    void eliminar_rol_en_uso() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(usoDeRoles.procesosQueLoUsan(1L, 5L)).thenReturn(List.of("Compras"));

        assertThrows(ReglaNegocioException.class,
                () -> rolProcesoService.eliminar(1L, 10L, 5L));

        verify(rolProcesoRepository, never()).delete(any());
    }

    @Test
    @DisplayName("HU-19: eliminar rol sin uso desactiva correctamente")
    void eliminar_rol_sin_uso() {
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(5L, 1L)).thenReturn(Optional.of(rol));
        when(usoDeRoles.procesosQueLoUsan(1L, 5L)).thenReturn(Collections.emptyList());
        when(rolProcesoRepository.save(any(RolProceso.class))).thenAnswer(inv -> inv.getArgument(0));

        rolProcesoService.eliminar(1L, 10L, 5L);

        assertFalse(rol.isActivo());
        verify(rolProcesoRepository).save(rol);
    }
}
