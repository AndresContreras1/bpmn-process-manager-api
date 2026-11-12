package com.facimus.procesos.gestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
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

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.CalendarioResponse;
import com.facimus.procesos.gestion.dto.response.EdicionFestivoResponse;
import com.facimus.procesos.gestion.dto.response.FestivoResponse;
import com.facimus.procesos.gestion.model.Calendario;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.FestivoEditado;
import com.facimus.procesos.gestion.model.OrigenFestivo;
import com.facimus.procesos.gestion.model.Pais;
import com.facimus.procesos.gestion.repository.CalendarioRepository;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.FestivoEditadoRepository;
import com.facimus.procesos.gestion.service.CalendarioLaboral;
import com.facimus.procesos.gestion.service.CalendarioService.Jornada;

/** PR 68: el calendario de una tienda: el de su pais mientras no lo edita, y lo que cambia queda en la bitacora. */
@ExtendWith(MockitoExtension.class)
class CalendarioServiceImplTest {

    private static final Long TIENDA = 7L;
    private static final Long ADMIN = 3L;
    private static final LocalDate ASUNCION = LocalDate.of(2026, 8, 17);

    @Mock
    private CalendarioRepository calendarioRepository;
    @Mock
    private FestivoEditadoRepository festivoEditadoRepository;
    @Mock
    private ConfiguracionTiendaRepository configuracionRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private Auditoria auditoria;

    @InjectMocks
    private CalendarioServiceImpl service;

    private static Jornada paris() {
        return new Jornada(Pais.FR, " Europe/Paris ", LocalTime.of(9, 0), LocalTime.of(18, 0),
                EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY));
    }

    private void sinCalendarioGuardado() {
        given(calendarioRepository.findByEmpresaId(TIENDA)).willReturn(Optional.empty());
    }

    private void conConfiguracion(String idioma, String zona) {
        given(configuracionRepository.findByEmpresaId(TIENDA)).willReturn(Optional.of(
                ConfiguracionTienda.builder().idioma(idioma).zonaHoraria(zona).build()));
    }

    @Test
    @DisplayName("Sin calendario guardado vale el del pais de su idioma, en su zona, de lunes a viernes de 8 a 17")
    void porDefecto_elDeSuPais() {
        sinCalendarioGuardado();
        conConfiguracion("en-AU", "Australia/Sydney");

        CalendarioResponse calendario = service.obtener(TIENDA);

        assertThat(calendario).isEqualTo(new CalendarioResponse(Pais.AU, "Australia/Sydney", LocalTime.of(8, 0),
                LocalTime.of(17, 0), List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), null));
    }

    @Test
    @DisplayName("Una tienda sin configuracion cae en Colombia, en Bogota")
    void porDefecto_sinConfiguracion() {
        sinCalendarioGuardado();
        given(configuracionRepository.findByEmpresaId(TIENDA)).willReturn(Optional.empty());

        CalendarioResponse calendario = service.obtener(TIENDA);

        assertThat(calendario.pais()).isEqualTo(Pais.CO);
        assertThat(calendario.zonaHoraria()).isEqualTo("America/Bogota");
    }

    @Test
    @DisplayName("El guardado se lee como se guardo, con su version y los dias en orden")
    void guardado() {
        given(calendarioRepository.findByEmpresaId(TIENDA)).willReturn(Optional.of(Calendario.builder().id(1L)
                .pais(Pais.US).zonaHoraria("America/New_York").horaInicio(LocalTime.of(7, 0))
                .horaFin(LocalTime.of(15, 0)).diasHabiles(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.MONDAY))
                .version(4L).build()));

        CalendarioResponse calendario = service.obtener(TIENDA);

        assertThat(calendario).isEqualTo(new CalendarioResponse(Pais.US, "America/New_York", LocalTime.of(7, 0),
                LocalTime.of(15, 0), List.of(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), 4L));
    }

    @Test
    @DisplayName("La primera edicion se guarda sin version, en la tienda, y queda en la bitacora")
    void editar_primeraVez() {
        sinCalendarioGuardado();
        Empresa tienda = Empresa.builder().id(TIENDA).build();
        given(empresaRepository.getReferenceById(TIENDA)).willReturn(tienda);
        given(calendarioRepository.saveAndFlush(any(Calendario.class))).willAnswer(guardar -> {
            Calendario calendario = guardar.getArgument(0);
            calendario.setId(11L);
            return calendario;
        });

        CalendarioResponse calendario = service.editar(TIENDA, ADMIN, paris(), null);

        ArgumentCaptor<Calendario> guardado = ArgumentCaptor.forClass(Calendario.class);
        verify(calendarioRepository).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getEmpresa()).isSameAs(tienda);
        assertThat(guardado.getValue().getZonaHoraria()).isEqualTo("Europe/Paris");
        assertThat(calendario.diasHabiles()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        assertThat(calendario.pais()).isEqualTo(Pais.FR);
        verify(auditoria).registrar(TIENDA, ADMIN, "CALENDARIO_EDITADO", "CALENDARIO", 11L,
                Map.of("pais", "FR", "zonaHoraria", "Europe/Paris"));
    }

    @Test
    @DisplayName("Un calendario que nunca se guardo no tiene version que comparar")
    void editar_primeraVezConVersion() {
        sinCalendarioGuardado();
        Jornada jornada = paris();

        assertThatThrownBy(() -> service.editar(TIENDA, ADMIN, jornada, 0L))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("sin versión");
        verify(calendarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Uno guardado se edita con la version que se leyo; con otra es un conflicto")
    void editar_conVersion() {
        Calendario guardado = Calendario.builder().id(11L).pais(Pais.CO).zonaHoraria("America/Bogota")
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(17, 0))
                .diasHabiles(EnumSet.of(DayOfWeek.MONDAY)).version(2L).build();
        given(calendarioRepository.findByEmpresaId(TIENDA)).willReturn(Optional.of(guardado));
        given(calendarioRepository.saveAndFlush(guardado)).willReturn(guardado);
        Jornada jornada = paris();

        assertThatThrownBy(() -> service.editar(TIENDA, ADMIN, jornada, 1L))
                .isInstanceOf(ConflictoDeVersionException.class);
        service.editar(TIENDA, ADMIN, jornada, 2L);

        assertThat(guardado.getPais()).isEqualTo(Pais.FR);
        assertThat(guardado.getHoraFin()).isEqualTo(LocalTime.of(18, 0));
        assertThat(guardado.getDiasHabiles()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    }

    @Test
    @DisplayName("Una zona que no existe o una jornada al reves no se guardan")
    void editar_invalido() {
        Jornada sinZona = new Jornada(Pais.CO, "America/Atlantida", LocalTime.of(8, 0), LocalTime.of(17, 0),
                EnumSet.of(DayOfWeek.MONDAY));
        Jornada alReves = new Jornada(Pais.CO, "America/Bogota", LocalTime.of(17, 0), LocalTime.of(17, 0),
                EnumSet.of(DayOfWeek.MONDAY));

        assertThatThrownBy(() -> service.editar(TIENDA, ADMIN, sinZona, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("no es una zona horaria");
        assertThatThrownBy(() -> service.editar(TIENDA, ADMIN, alReves, null))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("antes de terminar");
        verify(calendarioRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Los festivos de un ano: los de la ley con lo que cambio la tienda")
    void festivos() {
        sinCalendarioGuardado();
        conConfiguracion("es-CO", "America/Bogota");
        given(festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(TIENDA)).willReturn(List.of(
                FestivoEditado.builder().fecha(ASUNCION).festivo(false).build(),
                FestivoEditado.builder().fecha(LocalDate.of(2026, 9, 4)).nombre("Aniversario").festivo(true).build()));

        List<FestivoResponse> festivos = service.festivos(TIENDA, 2026);

        assertThat(festivos).hasSize(18).extracting(FestivoResponse::fecha).doesNotContain(ASUNCION)
                .contains(LocalDate.of(2026, 9, 4));
        assertThat(festivos).contains(new FestivoResponse(LocalDate.of(2026, 9, 4), "Aniversario",
                OrigenFestivo.EDITADO));
    }

    @Test
    @DisplayName("Un ano fuera de 1900 a 2199 es una peticion invalida; los extremos valen")
    void festivos_anioFueraDeRango() {
        assertThatThrownBy(() -> service.festivos(TIENDA, 1899)).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.festivos(TIENDA, 2200)).isInstanceOf(SolicitudInvalidaException.class);
        sinCalendarioGuardado();
        conConfiguracion("es-CO", "America/Bogota");

        assertThat(service.festivos(TIENDA, 1900)).isNotEmpty();
        assertThat(service.festivos(TIENDA, 2199)).isNotEmpty();
    }

    @Test
    @DisplayName("Las ediciones se leen tal cual, en orden")
    void ediciones() {
        given(festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(TIENDA)).willReturn(List.of(
                FestivoEditado.builder().fecha(ASUNCION).festivo(false).build()));

        assertThat(service.ediciones(TIENDA)).containsExactly(new EdicionFestivoResponse(ASUNCION, null, false));
    }

    @Test
    @DisplayName("Nombrar un dia lo vuelve festivo con ese nombre, sobre la edicion que ya tuviera")
    void nombrar() {
        FestivoEditado quitado = FestivoEditado.builder().id(5L).fecha(ASUNCION).festivo(false).build();
        given(festivoEditadoRepository.findByEmpresaIdAndFecha(TIENDA, ASUNCION)).willReturn(Optional.of(quitado));
        given(festivoEditadoRepository.save(quitado)).willReturn(quitado);

        FestivoResponse festivo = service.nombrarFestivo(TIENDA, ADMIN, ASUNCION, "  Asunción  ");

        assertThat(festivo).isEqualTo(new FestivoResponse(ASUNCION, "Asunción", OrigenFestivo.EDITADO));
        assertThat(quitado.isFestivo()).isTrue();
        verify(auditoria).registrar(TIENDA, ADMIN, "FESTIVO_NOMBRADO", "FESTIVO", 5L,
                Map.of("fecha", "2026-08-17", "nombre", "Asunción"));
    }

    @Test
    @DisplayName("Un festivo fuera de los anos que se leen no se agrega")
    void nombrar_fueraDeRango() {
        LocalDate lejos = LocalDate.of(2200, 1, 1);
        LocalDate antes = LocalDate.of(1899, 12, 31);

        assertThatThrownBy(() -> service.nombrarFestivo(TIENDA, ADMIN, lejos, "Lejos"))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.nombrarFestivo(TIENDA, ADMIN, antes, "Antes"))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(festivoEditadoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un dia sin edicion se edita por primera vez en la tienda")
    void nombrar_nuevo() {
        Empresa tienda = Empresa.builder().id(TIENDA).build();
        LocalDate aniversario = LocalDate.of(2026, 9, 4);
        given(festivoEditadoRepository.findByEmpresaIdAndFecha(TIENDA, aniversario)).willReturn(Optional.empty());
        given(empresaRepository.getReferenceById(TIENDA)).willReturn(tienda);
        given(festivoEditadoRepository.save(any(FestivoEditado.class))).willAnswer(guardar -> guardar.getArgument(0));

        service.nombrarFestivo(TIENDA, ADMIN, aniversario, "Aniversario");

        ArgumentCaptor<FestivoEditado> guardado = ArgumentCaptor.forClass(FestivoEditado.class);
        verify(festivoEditadoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getEmpresa()).isSameAs(tienda);
        assertThat(guardado.getValue().getFecha()).isEqualTo(aniversario);
    }

    @Test
    @DisplayName("Quitar un festivo de la ley lo anota como quitado")
    void quitar_calculado() {
        sinCalendarioGuardado();
        conConfiguracion("es-CO", "America/Bogota");
        given(festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(TIENDA)).willReturn(List.of());
        FestivoEditado renombrado = FestivoEditado.builder().id(9L).fecha(ASUNCION).nombre("Otro nombre")
                .festivo(true).build();
        given(festivoEditadoRepository.findByEmpresaIdAndFecha(TIENDA, ASUNCION)).willReturn(Optional.of(renombrado));
        given(festivoEditadoRepository.save(renombrado)).willReturn(renombrado);

        service.quitarFestivo(TIENDA, ADMIN, ASUNCION);

        assertThat(renombrado.isFestivo()).isFalse();
        assertThat(renombrado.getNombre()).isNull();
        verify(festivoEditadoRepository, never()).delete(any());
        verify(auditoria).registrar(TIENDA, ADMIN, "FESTIVO_QUITADO", "FESTIVO", 9L, Map.of("fecha", "2026-08-17"));
    }

    @Test
    @DisplayName("Quitar uno que agrego la tienda olvida la edicion")
    void quitar_agregado() {
        LocalDate aniversario = LocalDate.of(2026, 9, 4);
        sinCalendarioGuardado();
        conConfiguracion("es-CO", "America/Bogota");
        FestivoEditado agregado = FestivoEditado.builder().id(12L).fecha(aniversario).nombre("Aniversario")
                .festivo(true).build();
        given(festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(TIENDA)).willReturn(List.of(agregado));
        given(festivoEditadoRepository.findByEmpresaIdAndFecha(TIENDA, aniversario)).willReturn(Optional.of(agregado));

        service.quitarFestivo(TIENDA, ADMIN, aniversario);

        verify(festivoEditadoRepository).delete(agregado);
        verify(festivoEditadoRepository, never()).save(any());
        verify(auditoria).registrar(TIENDA, ADMIN, "FESTIVO_QUITADO", "FESTIVO", 12L,
                Map.of("fecha", "2026-09-04"));
    }

    @Test
    @DisplayName("Un dia que no es festivo no se puede quitar")
    void quitar_noEsFestivo() {
        sinCalendarioGuardado();
        conConfiguracion("es-CO", "America/Bogota");
        given(festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(TIENDA)).willReturn(List.of());
        LocalDate martes = LocalDate.of(2026, 9, 8);

        assertThatThrownBy(() -> service.quitarFestivo(TIENDA, ADMIN, martes))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no es festivo");
        verify(auditoria, never()).registrar(anyLong(), anyLong(), anyString(), anyString(), anyLong(), anyMap());
    }

    @Test
    @DisplayName("Restaurar un dia olvida lo que cambio la tienda; si no cambio nada es un 404")
    void restaurar() {
        FestivoEditado quitado = FestivoEditado.builder().id(5L).fecha(ASUNCION).festivo(false).build();
        given(festivoEditadoRepository.findByEmpresaIdAndFecha(TIENDA, ASUNCION)).willReturn(Optional.of(quitado));
        LocalDate otro = LocalDate.of(2026, 9, 8);
        given(festivoEditadoRepository.findByEmpresaIdAndFecha(TIENDA, otro)).willReturn(Optional.empty());

        service.restaurarFestivo(TIENDA, ADMIN, ASUNCION);

        verify(festivoEditadoRepository).delete(quitado);
        verify(auditoria).registrar(TIENDA, ADMIN, "FESTIVO_RESTAURADO", "FESTIVO", 5L,
                Map.of("fecha", "2026-08-17"));
        assertThatThrownBy(() -> service.restaurarFestivo(TIENDA, ADMIN, otro))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("El calendario laboral se arma con su pais, su zona, su jornada y lo que la tienda cambio")
    void laboral() {
        given(calendarioRepository.findByEmpresaId(TIENDA)).willReturn(Optional.of(Calendario.builder().id(1L)
                .pais(Pais.CO).zonaHoraria("America/Bogota").horaInicio(LocalTime.of(7, 0))
                .horaFin(LocalTime.of(16, 0)).diasHabiles(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
                .version(0L).build()));
        given(festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(TIENDA)).willReturn(List.of(
                FestivoEditado.builder().fecha(ASUNCION).festivo(false).build(),
                FestivoEditado.builder().fecha(LocalDate.of(2026, 9, 8)).nombre("Cierre").festivo(true).build()));

        CalendarioLaboral laboral = service.laboral(TIENDA);

        assertThat(laboral.zona()).isEqualTo(ZoneId.of("America/Bogota"));
        assertThat(laboral.jornada()).hasHours(9);
        assertThat(laboral.esDiaHabil(ASUNCION)).isTrue();
        assertThat(laboral.esDiaHabil(LocalDate.of(2026, 9, 8))).isFalse();
        assertThat(laboral.esDiaHabil(LocalDate.of(2026, 9, 9))).isFalse();
        verify(configuracionRepository, never()).findByEmpresaId(TIENDA);
    }
}
