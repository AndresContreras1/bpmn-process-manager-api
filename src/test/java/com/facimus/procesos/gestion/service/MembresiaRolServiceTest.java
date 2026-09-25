package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.RolDeUsuarioResponse;
import com.facimus.procesos.gestion.model.MembresiaRol;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.MembresiaRolServiceImpl;

/**
 * D13: reemplazar los roles de proceso de una persona. Es un filtro para su bandeja, asi que lo unico que hay que
 * cuidar es que el usuario y los roles sean de la tienda, y que lo que llega sea exactamente con lo que queda.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembresiaRolServiceTest {

    private static final Long TIENDA_ID = 1L;
    private static final Long ADMIN = 2L;
    private static final Long ANA = 5L;
    private static final Long VENTAS = 10L;
    private static final Long BODEGA = 11L;

    @Mock
    private MembresiaRolRepository membresiaRolRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolProcesoRepository rolProcesoRepository;

    @Mock
    private HistorialCambioService historialCambioService;

    @InjectMocks
    private MembresiaRolServiceImpl membresiaRolService;

    private Empresa tienda;

    @BeforeEach
    void laTiendaYSuGente() {
        tienda = Empresa.builder().id(TIENDA_ID).nombre("Demo Store").build();
        given(usuarioRepository.findByIdAndEmpresaId(ANA, TIENDA_ID)).willReturn(Optional.of(usuario()));
        given(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(VENTAS, TIENDA_ID))
                .willReturn(Optional.of(rol(VENTAS, "Sales")));
        given(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(BODEGA, TIENDA_ID))
                .willReturn(Optional.of(rol(BODEGA, "Warehouse")));
        given(membresiaRolRepository.save(any(MembresiaRol.class))).willAnswer(l -> l.getArgument(0));
    }

    @Test
    @DisplayName("Reemplazar borra lo que habia y guarda lo que llega")
    void reemplazar_dejaSoloLosQueLlegan() {
        List<RolDeUsuarioResponse> roles = membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA,
                List.of(VENTAS, BODEGA));

        verify(membresiaRolRepository).borrarLasDe(TIENDA_ID, ANA);
        ArgumentCaptor<MembresiaRol> guardadas = ArgumentCaptor.forClass(MembresiaRol.class);
        verify(membresiaRolRepository, org.mockito.Mockito.times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(m -> m.getRolProceso().getId())
                .containsExactly(VENTAS, BODEGA);
        assertThat(roles).extracting(RolDeUsuarioResponse::nombre).containsExactly("Sales", "Warehouse");
    }

    @Test
    @DisplayName("Una lista vacia deja a la persona sin ningun rol")
    void reemplazar_conListaVacia_laDejaSinRoles() {
        assertThat(membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA, List.of())).isEmpty();

        verify(membresiaRolRepository).borrarLasDe(TIENDA_ID, ANA);
        verify(membresiaRolRepository, never()).save(any());
    }

    @Test
    @DisplayName("Pedir dos veces el mismo rol no lo guarda dos veces")
    void reemplazar_conRepetidos_losIgnora() {
        assertThat(membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA, List.of(VENTAS, VENTAS, BODEGA)))
                .hasSize(2);

        verify(membresiaRolRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    @DisplayName("Un rol de otra tienda no existe, y no se guarda nada")
    void reemplazar_conRolDeOtraTienda_noExiste() {
        given(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(99L, TIENDA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA, List.of(VENTAS, 99L)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Rol de proceso no encontrado.");
        verify(membresiaRolRepository, never()).borrarLasDe(any(), any());
        verify(membresiaRolRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un usuario de otra tienda no existe")
    void reemplazar_conUsuarioDeOtraTienda_noExiste() {
        given(usuarioRepository.findByIdAndEmpresaId(ANA, TIENDA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA, List.of(VENTAS)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Usuario no encontrado.");
    }

    @Test
    @DisplayName("El historial de la tienda dice con que roles quedo la persona")
    void reemplazar_quedaEnElHistorialDeLaTienda() {
        membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA, List.of(BODEGA));

        verify(historialCambioService).registrarDeTienda(eq(TIENDA_ID), eq(ADMIN), eq(RecursoDeHistorial.USUARIO),
                eq(ANA), eq("\"Ana\" queda en los roles de proceso [\"Warehouse\"]."));
    }

    @Test
    @DisplayName("Quedarse sin roles tambien se anota, y se lee distinto")
    void reemplazar_sinRoles_seAnotaDistinto() {
        membresiaRolService.reemplazar(TIENDA_ID, ADMIN, ANA, List.of());

        verify(historialCambioService).registrarDeTienda(eq(TIENDA_ID), eq(ADMIN), eq(RecursoDeHistorial.USUARIO),
                eq(ANA), eq("\"Ana\" se queda sin roles de proceso."));
    }

    @Test
    @DisplayName("Los roles de un usuario de otra tienda no se leen")
    void rolesDe_usuarioDeOtraTienda_noExiste() {
        given(usuarioRepository.findByIdAndEmpresaId(ANA, TIENDA_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> membresiaRolService.rolesDe(TIENDA_ID, ANA))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private Usuario usuario() {
        return Usuario.builder().id(ANA).empresa(tienda).nombre("Ana").email("ana@demo.com")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv").rolAcceso(RolAcceso.EDITOR).activo(true).build();
    }

    private RolProceso rol(Long id, String nombre) {
        return RolProceso.builder().id(id).empresa(tienda).nombre(nombre).descripcion("Hace su parte")
                .activo(true).build();
    }
}
