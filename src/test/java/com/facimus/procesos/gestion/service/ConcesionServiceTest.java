package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.ConcesionResponse;
import com.facimus.procesos.gestion.model.Concesion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.SujetoConcesion;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.ConcesionRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.ConcesionServiceImpl;

/**
 * Las concesiones sobre un proceso o una carpeta: a quien se le dan, que no se repiten y quien puede retirarlas. Como
 * pesan al decidir los permisos lo prueba PermisosGranularesIntegracionTest.
 */
@ExtendWith(MockitoExtension.class)
class ConcesionServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Actor ADMIN = new Actor(2L, EMPRESA, RolAcceso.ADMINISTRADOR);

    @Mock
    private ConcesionRepository concesionRepository;
    @Mock
    private CarpetaRepository carpetaRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolProcesoRepository rolProcesoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private Permisos permisos;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private ConcesionServiceImpl service;

    @Test
    @DisplayName("Las concesiones de un proceso o de una carpeta salen de su propia lista")
    void listar() {
        conProceso(10L);
        when(concesionRepository.findAllByEmpresaIdAndProcesoIdOrderByIdAsc(EMPRESA, 10L))
                .thenReturn(List.of(concesion(1L, 10L, null, SujetoConcesion.ORGANIZACION, null, null, Accion.VER)));
        when(carpetaRepository.existsByIdAndEmpresaId(20L, EMPRESA)).thenReturn(true);
        when(concesionRepository.findAllByEmpresaIdAndCarpetaIdOrderByIdAsc(EMPRESA, 20L))
                .thenReturn(List.of(concesion(2L, null, 20L, SujetoConcesion.PERSONA, 5L, null, Accion.MODELAR)));

        assertThat(service.listar(EMPRESA, Recurso.proceso(10L))).containsExactly(
                new ConcesionResponse(1L, null, 10L, SujetoConcesion.ORGANIZACION, null, null, Accion.VER));
        assertThat(service.listar(EMPRESA, Recurso.carpeta(20L))).containsExactly(
                new ConcesionResponse(2L, 20L, null, SujetoConcesion.PERSONA, 5L, null, Accion.MODELAR));
    }

    @Test
    @DisplayName("Un proceso inactivo o una carpeta de otra tienda no tienen concesiones")
    void recursoQueNoEsDeLaTienda() {
        assertThatThrownBy(() -> service.listar(EMPRESA, Recurso.proceso(10L)))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proceso no encontrado.");
        assertThatThrownBy(() -> service.listar(EMPRESA, Recurso.carpeta(20L)))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Carpeta no encontrada.");
    }

    @Test
    @DisplayName("Conceder a una persona sobre un proceso lo guarda y lo deja en la auditoria")
    void concederAPersona() {
        conProceso(10L);
        when(usuarioRepository.existsByIdAndEmpresaId(5L, EMPRESA)).thenReturn(true);
        conEmpresa();
        ArgumentCaptor<Concesion> guardada = ArgumentCaptor.forClass(Concesion.class);
        when(concesionRepository.save(guardada.capture())).thenAnswer(invocacion -> {
            Concesion concesion = invocacion.getArgument(0);
            concesion.setId(30L);
            return concesion;
        });

        ConcesionResponse respuesta = service.conceder(ADMIN, Recurso.proceso(10L), Accion.MODELAR,
                SujetoConcesion.PERSONA, 5L, null);

        assertThat(respuesta).isEqualTo(new ConcesionResponse(30L, null, 10L, SujetoConcesion.PERSONA, 5L, null,
                Accion.MODELAR));
        assertThat(guardada.getValue().getEmpresa().getId()).isEqualTo(EMPRESA);
        verify(auditoria).registrar(EMPRESA, 2L, "CONCESION_CREADA", "PROCESO", 10L,
                Map.of("accion", "MODELAR", "sujeto", "PERSONA"));
    }

    @Test
    @DisplayName("Conceder a un rol sobre una carpeta la guarda en la carpeta y no en un proceso")
    void concederARolEnCarpeta() {
        when(carpetaRepository.existsByIdAndEmpresaId(20L, EMPRESA)).thenReturn(true);
        when(rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(3L, EMPRESA))
                .thenReturn(Optional.of(new RolProceso()));
        conEmpresa();
        when(concesionRepository.save(any(Concesion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ConcesionResponse respuesta = service.conceder(ADMIN, Recurso.carpeta(20L), Accion.VER, SujetoConcesion.ROL,
                null, 3L);

        assertThat(respuesta.carpetaId()).isEqualTo(20L);
        assertThat(respuesta.procesoId()).isNull();
        assertThat(respuesta.rolProcesoId()).isEqualTo(3L);
        verify(auditoria).registrar(EMPRESA, 2L, "CONCESION_CREADA", "CARPETA", 20L,
                Map.of("accion", "VER", "sujeto", "ROL"));
    }

    @Test
    @DisplayName("La misma concesion dos veces no se guarda; la misma accion para otra persona si")
    void repetida() {
        conProceso(10L);
        when(usuarioRepository.existsByIdAndEmpresaId(any(), any())).thenReturn(true);
        when(concesionRepository.findAllByEmpresaIdAndProcesoIdOrderByIdAsc(EMPRESA, 10L))
                .thenReturn(List.of(concesion(1L, 10L, null, SujetoConcesion.PERSONA, 5L, null, Accion.MODELAR)));

        assertThatThrownBy(() -> service.conceder(ADMIN, Recurso.proceso(10L), Accion.MODELAR,
                SujetoConcesion.PERSONA, 5L, null)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esa concesión ya existe.");
        verify(concesionRepository, never()).save(any());

        conEmpresa();
        when(concesionRepository.save(any(Concesion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        service.conceder(ADMIN, Recurso.proceso(10L), Accion.MODELAR, SujetoConcesion.PERSONA, 6L, null);
        service.conceder(ADMIN, Recurso.proceso(10L), Accion.VER, SujetoConcesion.PERSONA, 5L, null);
        verify(concesionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("El sujeto tiene que coincidir con la persona o el rol que llegan")
    void sujetoIncompleto() {
        conProceso(10L);
        Recurso proceso = Recurso.proceso(10L);

        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.PERSONA, null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.PERSONA, 5L, 3L))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.ROL, null, null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.ROL, 5L, 3L))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.ORGANIZACION, 5L, null))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.ORGANIZACION, null, 3L))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(concesionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una persona o un rol que no son de la tienda no reciben concesiones")
    void sujetoDeOtraTienda() {
        conProceso(10L);
        Recurso proceso = Recurso.proceso(10L);

        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.PERSONA, 5L, null))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Usuario no encontrado.");
        assertThatThrownBy(() -> service.conceder(ADMIN, proceso, Accion.VER, SujetoConcesion.ROL, null, 3L))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Rol de proceso no encontrado.");
    }

    @Test
    @DisplayName("Retirar exige administrar el recurso de la concesion, la borra y lo deja en la auditoria")
    void retirar() {
        Concesion deProceso = concesion(1L, 10L, null, SujetoConcesion.ORGANIZACION, null, null, Accion.VER);
        Concesion deCarpeta = concesion(2L, null, 20L, SujetoConcesion.ROL, null, 3L, Accion.MODELAR);
        when(concesionRepository.findByIdAndEmpresaId(1L, EMPRESA)).thenReturn(Optional.of(deProceso));
        when(concesionRepository.findByIdAndEmpresaId(2L, EMPRESA)).thenReturn(Optional.of(deCarpeta));

        service.retirar(ADMIN, 1L);
        service.retirar(ADMIN, 2L);

        verify(permisos).exigir(ADMIN, Accion.ADMINISTRAR, Recurso.proceso(10L));
        verify(permisos).exigir(ADMIN, Accion.ADMINISTRAR, Recurso.carpeta(20L));
        verify(concesionRepository).delete(deProceso);
        verify(concesionRepository).delete(deCarpeta);
        verify(auditoria).registrar(EMPRESA, 2L, "CONCESION_RETIRADA", "PROCESO", 10L,
                Map.of("accion", "VER", "sujeto", "ORGANIZACION"));
        verify(auditoria).registrar(EMPRESA, 2L, "CONCESION_RETIRADA", "CARPETA", 20L,
                Map.of("accion", "MODELAR", "sujeto", "ROL"));
    }

    @Test
    @DisplayName("Sin permiso de administrar no se retira, y una concesion de otra tienda no se encuentra")
    void retirarRechazos() {
        Concesion concesion = concesion(1L, 10L, null, SujetoConcesion.ORGANIZACION, null, null, Accion.VER);
        when(concesionRepository.findByIdAndEmpresaId(1L, EMPRESA)).thenReturn(Optional.of(concesion));
        Actor editor = new Actor(4L, EMPRESA, RolAcceso.EDITOR);
        doThrow(new SinPermisoException("No.")).when(permisos)
                .exigir(editor, Accion.ADMINISTRAR, Recurso.proceso(10L));

        assertThatThrownBy(() -> service.retirar(editor, 1L)).isInstanceOf(SinPermisoException.class);
        assertThatThrownBy(() -> service.retirar(ADMIN, 9L)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(concesionRepository, never()).delete(any());
    }

    private void conProceso(Long id) {
        when(procesoRepository.findByIdAndEmpresaIdAndActivoTrue(id, EMPRESA)).thenReturn(Optional.of(new Proceso()));
    }

    private void conEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(empresa));
    }

    private static Concesion concesion(Long id, Long procesoId, Long carpetaId, SujetoConcesion sujeto, Long usuarioId,
            Long rolProcesoId, Accion accion) {
        return Concesion.builder().id(id).procesoId(procesoId).carpetaId(carpetaId).sujeto(sujeto)
                .usuarioId(usuarioId).rolProcesoId(rolProcesoId).accion(accion).build();
    }
}
