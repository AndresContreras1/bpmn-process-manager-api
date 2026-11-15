package com.facimus.procesos.gestion.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.facimus.procesos.gestion.dto.response.CalendarioResponse;
import com.facimus.procesos.gestion.dto.response.EdicionFestivoResponse;
import com.facimus.procesos.gestion.dto.response.FestivoResponse;
import com.facimus.procesos.gestion.model.Pais;

/**
 * PR 68 (D35): el calendario laboral de cada tienda: su pais, su zona, su jornada y sus festivos, que se calculan de
 * la ley y la tienda edita. Con el se cuenta el tiempo habil de los temporizadores y de los plazos.
 */
public interface CalendarioService {

    /** Lo que la tienda edita de su calendario. */
    record Jornada(Pais pais, String zonaHoraria, LocalTime horaInicio, LocalTime horaFin, Set<DayOfWeek> diasHabiles) {
    }

    /** El calendario de la tienda; el de su pais si nunca lo edito. */
    CalendarioResponse obtener(Long empresaId);

    /** Lo cambia entero; con la version que se leyo, vacia la primera vez. */
    CalendarioResponse editar(Long empresaId, Long usuarioId, Jornada jornada, Long version);

    /** Los festivos de ese ano, calculados y editados, en orden. */
    List<FestivoResponse> festivos(Long empresaId, int anio);

    /** Lo que la tienda cambio de los festivos de su pais. */
    List<EdicionFestivoResponse> ediciones(Long empresaId);

    /** Ese dia queda festivo con ese nombre: agrega uno, o renombra el calculado. */
    FestivoResponse nombrarFestivo(Long empresaId, Long usuarioId, LocalDate fecha, String nombre);

    /** Ese dia deja de ser festivo. */
    void quitarFestivo(Long empresaId, Long usuarioId, LocalDate fecha);

    /** Ese dia vuelve a ser lo que dice la ley: se olvida lo que la tienda cambio. */
    void restaurarFestivo(Long empresaId, Long usuarioId, LocalDate fecha);

    /** El calendario ya armado para contar tiempo habil. */
    CalendarioLaboral laboral(Long empresaId);
}
