package com.facimus.procesos.gestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.CarpetaResponse;
import com.facimus.procesos.gestion.model.Carpeta;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.ProcesosPorCarpeta;
import com.facimus.procesos.gestion.service.impl.CarpetaServiceImpl;

/**
 * El arbol de carpetas del repositorio de procesos: sin ciclos, sin mas de ocho niveles, sin dos hermanas con el mismo
 * nombre, y sin perder lo que una carpeta hereda sin que lo decida quien puede administrarla.
 */
@ExtendWith(MockitoExtension.class)
class CarpetaServiceTest {

    private static final Long EMPRESA = 7L;
    private static final Actor EDITOR = new Actor(4L, EMPRESA, RolAcceso.EDITOR);

    @Mock
    private CarpetaRepository carpetaRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private ProcesoRepository procesoRepository;
    @Mock
    private Permisos permisos;

    @InjectMocks
    private CarpetaServiceImpl service;

    @Test
    @DisplayName("Listar dice cuantos procesos tiene cada carpeta, y cero a la que no tiene")
    void listar() {
        when(procesoRepository.contarPorCarpeta(EMPRESA)).thenReturn(List.of(new ProcesosPorCarpeta(1L, 3)));
        when(carpetaRepository.findAllByEmpresaIdOrderByNombreAsc(EMPRESA))
                .thenReturn(List.of(carpeta(1L, "Ventas", null), carpeta(2L, "Bodega", 1L)));

        assertThat(service.listar(EMPRESA)).containsExactly(new CarpetaResponse(1L, "Ventas", null, 3, 0L),
                new CarpetaResponse(2L, "Bodega", 1L, 0, 0L));
    }

    @Test
    @DisplayName("Crear guarda la carpeta en su padre; sin padre queda en la raiz")
    void crear() {
        conCarpetas(carpeta(1L, "Ventas", null));
        conEmpresa();
        when(carpetaRepository.saveAndFlush(any(Carpeta.class))).thenAnswer(invocacion -> {
            Carpeta guardada = invocacion.getArgument(0);
            guardada.setId(9L);
            return guardada;
        });

        CarpetaResponse dentro = service.crear(EMPRESA, "Pagos", 1L);
        CarpetaResponse enLaRaiz = service.crear(EMPRESA, "Pagos", null);

        assertThat(dentro).isEqualTo(new CarpetaResponse(9L, "Pagos", 1L, 0, null));
        assertThat(enLaRaiz.padreId()).isNull();
    }

    @Test
    @DisplayName("Dos carpetas hermanas no se llaman igual, aunque cambien las mayusculas")
    void nombreRepetido() {
        conCarpetas(carpeta(1L, "Ventas", null), carpeta(2L, "Pagos", 1L));

        assertThatThrownBy(() -> service.crear(EMPRESA, "PAGOS", 1L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya hay una carpeta con el nombre \"PAGOS\" en ese lugar.");
        assertThatThrownBy(() -> service.crear(EMPRESA, "ventas", null)).isInstanceOf(ReglaNegocioException.class);
        verify(carpetaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Un padre que no existe no se usa")
    void padreQueNoExiste() {
        conCarpetas(carpeta(1L, "Ventas", null));

        assertThatThrownBy(() -> service.crear(EMPRESA, "Pagos", 5L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("El arbol no pasa de ocho niveles, contando lo que la carpeta lleva debajo")
    void profundidadMaxima() {
        List<Carpeta> cadena = new ArrayList<>();
        for (long id = 1; id <= 7; id++) {
            cadena.add(carpeta(id, "N" + id, id == 1 ? null : id - 1));
        }
        conCarpetas(cadena.toArray(Carpeta[]::new));
        conEmpresa();
        when(carpetaRepository.saveAndFlush(any(Carpeta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        assertThat(service.crear(EMPRESA, "Octavo", 7L).padreId()).isEqualTo(7L);

        cadena.add(carpeta(8L, "N8", 7L));
        conCarpetas(cadena.toArray(Carpeta[]::new));
        assertThatThrownBy(() -> service.crear(EMPRESA, "Noveno", 8L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Las carpetas no pueden tener más de 8 niveles.");
    }

    @Test
    @DisplayName("Mover una carpeta con hijas cuenta sus niveles: no cabe donde una sola si cabria")
    void moverConHijas() {
        Carpeta raiz = carpeta(1L, "Raiz", null);
        Carpeta movida = carpeta(10L, "Movida", null);
        List<Carpeta> todas = new ArrayList<>(List.of(raiz, movida));
        for (long id = 2; id <= 6; id++) {
            todas.add(carpeta(id, "N" + id, id - 1));
        }
        todas.add(carpeta(11L, "Hija", 10L));
        todas.add(carpeta(12L, "Nieta", 11L));
        conCarpetas(todas.toArray(Carpeta[]::new));
        when(carpetaRepository.findByIdAndEmpresaId(10L, EMPRESA)).thenReturn(Optional.of(movida));

        assertThatThrownBy(() -> service.editar(EMPRESA, 10L, "Movida", 6L, 0L))
                .isInstanceOf(ReglaNegocioException.class);

        when(carpetaRepository.saveAndFlush(movida)).thenReturn(movida);
        when(procesoRepository.countByCarpetaIdAndEmpresaIdAndActivoTrue(10L, EMPRESA)).thenReturn(2L);
        CarpetaResponse movidaAlCinco = service.editar(EMPRESA, 10L, "Movida", 5L, 0L);
        assertThat(movidaAlCinco).isEqualTo(new CarpetaResponse(10L, "Movida", 5L, 2, 0L));
    }

    @Test
    @DisplayName("Una carpeta no queda dentro de si misma ni de algo que lleva dentro")
    void sinCiclos() {
        Carpeta ventas = carpeta(1L, "Ventas", null);
        conCarpetas(ventas, carpeta(2L, "Pagos", 1L), carpeta(3L, "Tarjetas", 2L));
        when(carpetaRepository.findByIdAndEmpresaId(1L, EMPRESA)).thenReturn(Optional.of(ventas));

        assertThatThrownBy(() -> service.editar(EMPRESA, 1L, "Ventas", 3L, 0L))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Una carpeta no puede quedar dentro de sí misma.");
        assertThatThrownBy(() -> service.editar(EMPRESA, 1L, "Ventas", 1L, 0L))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(ventas.getPadreId()).isNull();
    }

    @Test
    @DisplayName("Renombrar con su propio nombre no choca consigo misma; una version vieja no edita")
    void editarRenombrar() {
        Carpeta pagos = carpeta(2L, "Pagos", 1L);
        conCarpetas(carpeta(1L, "Ventas", null), pagos);
        when(carpetaRepository.findByIdAndEmpresaId(2L, EMPRESA)).thenReturn(Optional.of(pagos));
        when(carpetaRepository.saveAndFlush(pagos)).thenReturn(pagos);

        assertThat(service.editar(EMPRESA, 2L, "pagos", 1L, 0L).nombre()).isEqualTo("pagos");

        ReflectionTestUtils.setField(pagos, "version", 4L);
        assertThatThrownBy(() -> service.editar(EMPRESA, 2L, "Cobros", 1L, 3L))
                .isInstanceOf(ConflictoDeVersionException.class);
        assertThat(pagos.getNombre()).isEqualTo("pagos");
        assertThatThrownBy(() -> service.editar(EMPRESA, 9L, "X", null, 0L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Crear dentro de una carpeta exige poder modelar en ella; en la raiz no se pregunta")
    void crearComoActor() {
        conCarpetas(carpeta(1L, "Ventas", null));
        conEmpresa();
        when(carpetaRepository.saveAndFlush(any(Carpeta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        service.crear(EDITOR, "Pagos", 1L);
        service.crear(EDITOR, "Otra", null);

        verify(permisos).exigir(EDITOR, Accion.MODELAR, Recurso.carpeta(1L));
        verify(permisos, times(1)).exigir(any(Actor.class), any(), any());
    }

    @Test
    @DisplayName("Mover una carpeta que tiene o recibe concesiones exige administrarla; renombrarla no")
    void moverConConcesiones() {
        Carpeta pagos = carpeta(2L, "Pagos", null);
        conCarpetas(carpeta(1L, "Ventas", null), pagos, carpeta(3L, "Bodega", null));
        when(carpetaRepository.findByIdAndEmpresaId(2L, EMPRESA)).thenReturn(Optional.of(pagos));
        when(carpetaRepository.saveAndFlush(pagos)).thenReturn(pagos);
        when(permisos.tieneConcesiones(EMPRESA, Recurso.carpeta(2L))).thenReturn(false);
        when(permisos.tieneConcesiones(EMPRESA, Recurso.carpeta(1L))).thenReturn(true);

        service.editar(EDITOR, 2L, "Cobros", null, 0L);
        verify(permisos, never()).exigir(any(Actor.class), any(), any());

        service.editar(EDITOR, 2L, "Cobros", 1L, 0L);
        verify(permisos).exigir(EDITOR, Accion.ADMINISTRAR, Recurso.carpeta(2L));
    }

    @Test
    @DisplayName("Mover entre carpetas sin concesiones no pide administrar")
    void moverSinConcesiones() {
        Carpeta pagos = carpeta(2L, "Pagos", null);
        conCarpetas(carpeta(1L, "Ventas", null), pagos);
        when(carpetaRepository.findByIdAndEmpresaId(2L, EMPRESA)).thenReturn(Optional.of(pagos));
        when(carpetaRepository.saveAndFlush(pagos)).thenReturn(pagos);

        service.editar(EDITOR, 2L, "Pagos", 1L, 0L);
        pagos.setPadreId(1L);
        service.editar(EDITOR, 2L, "Pagos", null, 0L);

        verify(permisos, never()).exigir(any(Actor.class), any(), any());
        verify(permisos, never()).tieneConcesiones(EMPRESA, Recurso.carpeta(null));
    }

    @Test
    @DisplayName("Una carpeta con subcarpetas o con procesos no se borra; vacia si")
    void eliminar() {
        Carpeta ventas = carpeta(1L, "Ventas", null);
        when(carpetaRepository.findByIdAndEmpresaId(1L, EMPRESA)).thenReturn(Optional.of(ventas));
        when(carpetaRepository.existsByEmpresaIdAndPadreId(EMPRESA, 1L)).thenReturn(true, false, false);
        when(procesoRepository.countByCarpetaIdAndEmpresaIdAndActivoTrue(1L, EMPRESA)).thenReturn(1L, 0L);

        assertThatThrownBy(() -> service.eliminar(EMPRESA, 1L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La carpeta tiene subcarpetas: muévelas o bórralas primero.");
        assertThatThrownBy(() -> service.eliminar(EMPRESA, 1L)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La carpeta tiene procesos: muévelos primero.");
        verify(carpetaRepository, never()).delete(any());
        service.eliminar(EMPRESA, 1L);
        verify(carpetaRepository).delete(ventas);
    }

    @Test
    @DisplayName("Una carpeta con sus subcarpetas son ella y todo lo que tiene debajo, a cualquier profundidad")
    void conSubcarpetas() {
        conCarpetas(carpeta(1L, "Ventas", null), carpeta(2L, "Pagos", 1L), carpeta(3L, "Tarjetas", 2L),
                carpeta(4L, "Bodega", null), carpeta(5L, "Envios", 4L));

        assertThat(service.conSubcarpetas(EMPRESA, 1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(service.conSubcarpetas(EMPRESA, 3L)).containsExactly(3L);
    }

    private void conCarpetas(Carpeta... carpetas) {
        when(carpetaRepository.findAllByEmpresaIdOrderByNombreAsc(EMPRESA)).thenReturn(List.of(carpetas));
    }

    private void conEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setId(EMPRESA);
        when(empresaRepository.findById(EMPRESA)).thenReturn(Optional.of(empresa));
    }

    private static Carpeta carpeta(Long id, String nombre, Long padreId) {
        Carpeta carpeta = Carpeta.builder().id(id).nombre(nombre).padreId(padreId).build();
        ReflectionTestUtils.setField(carpeta, "version", 0L);
        return carpeta;
    }
}
