package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.Carpeta;
import com.facimus.procesos.gestion.model.Concesion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.SujetoConcesion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.ConcesionRepository;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.impl.PermisosImpl;

/**
 * Los permisos granulares sin la base: lo de siempre por rol cuando la tienda no concedio nada, como una concesion
 * restringe una accion a quien la tiene, como se heredan de las carpetas, y que nadie aprueba su propio cambio.
 *
 * <p>La tienda de prueba: la carpeta 1 en la raiz con la 2 adentro, el proceso 10 en la 2 y el 11 suelto. La persona 4
 * tiene el rol de proceso 3; la 5 no tiene ninguno.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermisosTest {

    private static final Long EMPRESA = 7L;
    private static final Recurso PROCESO = Recurso.proceso(10L);
    private static final Recurso SUELTO = Recurso.proceso(11L);
    private static final Recurso RAIZ = Recurso.carpeta(1L);
    private static final Recurso CARPETA = Recurso.carpeta(2L);
    private static final Actor ADMIN = new Actor(2L, EMPRESA, RolAcceso.ADMINISTRADOR);
    private static final Actor CON_ROL = new Actor(4L, EMPRESA, RolAcceso.EDITOR);
    private static final Actor SIN_ROL = new Actor(5L, EMPRESA, RolAcceso.EDITOR);
    private static final Actor LECTOR = new Actor(6L, EMPRESA, RolAcceso.SOLO_LECTURA);

    @Mock
    private ConcesionRepository concesionRepository;
    @Mock
    private CarpetaRepository carpetaRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private MembresiaRolRepository membresiaRolRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PermisosImpl permisos;

    private Proceso proceso;

    @BeforeEach
    void laTienda() {
        proceso = Proceso.builder().id(10L).carpetaId(2L).build();
        Proceso suelto = Proceso.builder().id(11L).build();
        when(procesoRepository.findByIdAndEmpresaId(10L, EMPRESA)).thenReturn(Optional.of(proceso));
        when(procesoRepository.findByIdAndEmpresaId(11L, EMPRESA)).thenReturn(Optional.of(suelto));
        when(procesoRepository.existsByIdAndEmpresaId(10L, EMPRESA)).thenReturn(true);
        when(carpetaRepository.existsByIdAndEmpresaId(2L, EMPRESA)).thenReturn(true);
        when(procesoRepository.findAllByIdInAndEmpresaId(anyCollection(), eq(EMPRESA))).thenAnswer(invocacion ->
                List.of(proceso, suelto).stream()
                        .filter(uno -> invocacion.<Collection<Long>>getArgument(0).contains(uno.getId()))
                        .toList());
        when(carpetaRepository.findAllByEmpresaId(EMPRESA)).thenReturn(List.of(
                Carpeta.builder().id(1L).nombre("Ventas").build(),
                Carpeta.builder().id(2L).nombre("Pagos").padreId(1L).build()));
        when(membresiaRolRepository.rolesDe(EMPRESA, 4L)).thenReturn(List.of(3L));
        when(membresiaRolRepository.rolesDe(EMPRESA, 5L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("Sin concesiones en la tienda, un editor hace todo menos administrar y no se leen las tablas")
    void sinConcesionesEditor() {
        assertThat(permisos.acciones(SIN_ROL, PROCESO)).isEqualTo(EnumSet.complementOf(EnumSet.of(Accion.ADMINISTRAR)));
        verify(concesionRepository, never()).findAllByEmpresaId(anyLong());
    }

    @Test
    @DisplayName("Un administrador puede todo, sin mirar concesiones")
    void administrador() {
        assertThat(permisos.acciones(ADMIN, PROCESO)).isEqualTo(EnumSet.allOf(Accion.class));
        verify(concesionRepository, never()).existsByEmpresaId(anyLong());
    }

    @Test
    @DisplayName("Solo lectura ve, comenta y exporta lo de la tienda; lo ajeno lo responde el servicio con su 404")
    void soloLectura() {
        assertThat(permisos.acciones(LECTOR, PROCESO)).containsExactlyInAnyOrder(Accion.VER, Accion.COMENTAR,
                Accion.EXPORTAR);
        assertThat(permisos.acciones(LECTOR, CARPETA)).containsExactlyInAnyOrder(Accion.VER, Accion.COMENTAR,
                Accion.EXPORTAR);
        assertThat(permisos.puede(LECTOR, Accion.MODELAR, Recurso.proceso(99L))).isTrue();
        assertThat(permisos.puede(LECTOR, Accion.MODELAR, Recurso.carpeta(98L))).isTrue();
    }

    @Test
    @DisplayName("Nadie aprueba el proceso que el mismo cambio por ultima vez, ni siquiera un administrador")
    void separacionDeFunciones() {
        ReflectionTestUtils.setField(proceso, "modificadoPor", 2L);

        assertThat(permisos.puede(ADMIN, Accion.APROBAR, PROCESO)).isFalse();
        assertThat(permisos.puede(ADMIN, Accion.PUBLICAR, PROCESO)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.APROBAR, PROCESO)).isTrue();
        assertThat(permisos.puede(ADMIN, Accion.APROBAR, CARPETA)).isTrue();
        assertThat(permisos.puede(ADMIN, Accion.APROBAR, Recurso.proceso(99L))).isTrue();
    }

    @Test
    @DisplayName("Una concesion de modelar a una persona se lo quita a las demas, que siguen viendo y revisando")
    void concesionAPersona() {
        conConcesiones(concesion(10L, null, SujetoConcesion.PERSONA, 4L, null, Accion.MODELAR));

        assertThat(permisos.puede(CON_ROL, Accion.MODELAR, PROCESO)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.MODELAR, PROCESO)).isFalse();
        assertThat(permisos.puede(SIN_ROL, Accion.VER, PROCESO)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.REVISAR, PROCESO)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.MODELAR, SUELTO)).isTrue();
        assertThatThrownBy(() -> permisos.exigir(SIN_ROL, Accion.MODELAR, PROCESO))
                .isInstanceOf(SinPermisoException.class).hasMessage("No tienes permiso para hacer esto aquí.");
        permisos.exigir(CON_ROL, Accion.MODELAR, PROCESO);
    }

    @Test
    @DisplayName("Ver concedido a un rol en una carpeta alcanza a lo que esta debajo; a quien no lo tiene, le es un 404")
    void verHeredadoPorRol() {
        conConcesiones(concesion(null, 1L, SujetoConcesion.ROL, null, 3L, Accion.VER));

        assertThat(permisos.puede(CON_ROL, Accion.VER, PROCESO)).isTrue();
        assertThat(permisos.puede(CON_ROL, Accion.VER, CARPETA)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.VER, PROCESO)).isFalse();
        assertThat(permisos.puede(SIN_ROL, Accion.VER, SUELTO)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.MODELAR, PROCESO)).isTrue();
        assertThatThrownBy(() -> permisos.exigir(SIN_ROL, Accion.VER, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Proceso no encontrado.");
        assertThatThrownBy(() -> permisos.exigir(SIN_ROL, Accion.VER, RAIZ))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Carpeta no encontrada.");
    }

    @Test
    @DisplayName("Administrar concedido cubre cualquier accion, y cualquier concesion trae ver")
    void administrarCubreTodo() {
        conConcesiones(concesion(null, 2L, SujetoConcesion.PERSONA, 5L, null, Accion.ADMINISTRAR),
                concesion(10L, null, SujetoConcesion.PERSONA, 4L, null, Accion.PUBLICAR),
                concesion(10L, null, SujetoConcesion.ROL, null, 3L, Accion.COMENTAR));

        assertThat(permisos.puede(SIN_ROL, Accion.PUBLICAR, PROCESO)).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.ADMINISTRAR, PROCESO)).isTrue();
        assertThat(permisos.puede(CON_ROL, Accion.ADMINISTRAR, PROCESO)).isFalse();
        assertThat(permisos.puede(CON_ROL, Accion.PUBLICAR, PROCESO)).isTrue();
        conPersona(4L, RolAcceso.EDITOR);
        assertThat(permisos.concedido(EMPRESA, 4L, Accion.VER, PROCESO)).isTrue();
    }

    @Test
    @DisplayName("Una concesion a toda la organizacion la tiene cualquiera que no sea de solo lectura")
    void concesionALaOrganizacion() {
        conConcesiones(concesion(10L, null, SujetoConcesion.ORGANIZACION, null, null, Accion.APROBAR));

        assertThat(permisos.puede(SIN_ROL, Accion.APROBAR, PROCESO)).isTrue();
        assertThat(permisos.puede(LECTOR, Accion.APROBAR, PROCESO)).isFalse();
    }

    @Test
    @DisplayName("Con concesiones, un recurso que no es de la tienda no se decide aqui")
    void recursoAjenoConConcesiones() {
        conConcesiones(concesion(null, 1L, SujetoConcesion.PERSONA, 4L, null, Accion.MODELAR));

        assertThat(permisos.puede(SIN_ROL, Accion.MODELAR, Recurso.proceso(99L))).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.MODELAR, Recurso.carpeta(98L))).isTrue();
        assertThat(permisos.puede(SIN_ROL, Accion.MODELAR, CARPETA)).isFalse();
    }

    @Test
    @DisplayName("Concedido mira solo las concesiones expresas: sin ninguna, o sin una que aplique, es que no")
    void concedido() {
        conPersona(4L, RolAcceso.EDITOR);
        conPersona(5L, RolAcceso.EDITOR);
        assertThat(permisos.concedido(EMPRESA, 4L, Accion.MODELAR, PROCESO)).isFalse();

        conConcesiones(concesion(null, 1L, SujetoConcesion.PERSONA, 4L, null, Accion.MODELAR));
        assertThat(permisos.concedido(EMPRESA, 4L, Accion.MODELAR, PROCESO)).isTrue();
        assertThat(permisos.concedido(EMPRESA, 4L, Accion.PUBLICAR, PROCESO)).isFalse();
        assertThat(permisos.concedido(EMPRESA, 5L, Accion.MODELAR, PROCESO)).isFalse();
        assertThat(permisos.concedido(EMPRESA, 4L, Accion.MODELAR, Recurso.proceso(99L))).isFalse();
        assertThatThrownBy(() -> permisos.concedido(EMPRESA, 9L, Accion.MODELAR, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Exigir por ids busca el rol de la persona en la tienda")
    void exigirPorIds() {
        conPersona(6L, RolAcceso.SOLO_LECTURA);

        permisos.exigir(EMPRESA, 6L, Accion.VER, PROCESO);
        assertThatThrownBy(() -> permisos.exigir(EMPRESA, 6L, Accion.MODELAR, PROCESO))
                .isInstanceOf(SinPermisoException.class);
        assertThatThrownBy(() -> permisos.exigir(EMPRESA, 9L, Accion.VER, PROCESO))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessage("Usuario no encontrado.");
    }

    @Test
    @DisplayName("Un recurso tiene concesiones si alguna lo alcanza, propia o de una carpeta de arriba")
    void tieneConcesiones() {
        assertThat(permisos.tieneConcesiones(EMPRESA, PROCESO)).isFalse();

        conConcesiones(concesion(null, 1L, SujetoConcesion.ORGANIZACION, null, null, Accion.VER));
        assertThat(permisos.tieneConcesiones(EMPRESA, PROCESO)).isTrue();
        assertThat(permisos.tieneConcesiones(EMPRESA, CARPETA)).isTrue();
        assertThat(permisos.tieneConcesiones(EMPRESA, SUELTO)).isFalse();
        assertThat(permisos.tieneConcesiones(EMPRESA, Recurso.carpeta(98L))).isFalse();
        verify(membresiaRolRepository, never()).rolesDe(any(), any());
    }

    @Test
    @DisplayName("Sin concesiones de ver, quien edita ve todo; un administrador siempre")
    void visibilidadCompleta() {
        assertThat(permisos.visibilidad(SIN_ROL)).isEqualTo(Visibilidad.TODO);

        conConcesiones(concesion(10L, null, SujetoConcesion.PERSONA, 4L, null, Accion.MODELAR));
        assertThat(permisos.visibilidad(SIN_ROL)).isEqualTo(Visibilidad.TODO);
        assertThat(permisos.visibilidad(ADMIN)).isEqualTo(Visibilidad.TODO);
    }

    @Test
    @DisplayName("Con concesiones de ver, se ocultan las carpetas y los procesos que la persona no puede ver")
    void visibilidadRestringida() {
        conConcesiones(concesion(null, 2L, SujetoConcesion.ROL, null, 3L, Accion.VER),
                concesion(11L, null, SujetoConcesion.PERSONA, 5L, null, Accion.VER));

        Visibilidad sinRol = permisos.visibilidad(SIN_ROL);
        assertThat(sinRol.todo()).isFalse();
        assertThat(sinRol.carpetasOcultas()).containsExactly(2L);
        assertThat(sinRol.procesosVisibles()).containsExactly(11L);
        assertThat(sinRol.procesosOcultos()).isEmpty();

        Visibilidad conRol = permisos.visibilidad(CON_ROL);
        assertThat(conRol.carpetasOcultas()).isEmpty();
        assertThat(conRol.procesosOcultos()).containsExactly(11L);
    }

    @Test
    @DisplayName("Solo lectura no ve todo cuando hay concesiones, aunque ninguna sea de ver")
    void visibilidadSoloLectura() {
        conConcesiones(concesion(10L, null, SujetoConcesion.PERSONA, 4L, null, Accion.MODELAR));

        Visibilidad lector = permisos.visibilidad(LECTOR);

        assertThat(lector.todo()).isFalse();
        assertThat(lector.carpetasOcultas()).isEmpty();
        assertThat(lector.procesosVisibles()).containsExactly(10L);
    }

    private void conConcesiones(Concesion... concesiones) {
        when(concesionRepository.existsByEmpresaId(EMPRESA)).thenReturn(true);
        when(concesionRepository.findAllByEmpresaId(EMPRESA)).thenReturn(List.of(concesiones));
    }

    private void conPersona(Long id, RolAcceso rol) {
        when(usuarioRepository.findByIdAndEmpresaId(id, EMPRESA))
                .thenReturn(Optional.of(Usuario.builder().id(id).rolAcceso(rol).build()));
    }

    private static Concesion concesion(Long procesoId, Long carpetaId, SujetoConcesion sujeto, Long usuarioId,
            Long rolProcesoId, Accion accion) {
        return Concesion.builder().procesoId(procesoId).carpetaId(carpetaId).sujeto(sujeto).usuarioId(usuarioId)
                .rolProcesoId(rolProcesoId).accion(accion).build();
    }
}
