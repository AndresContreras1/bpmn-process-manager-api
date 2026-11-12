package com.facimus.procesos.gestion.service.impl;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.facimus.procesos.gestion.service.CalendarioService;
import com.facimus.procesos.gestion.service.Festivo;
import com.facimus.procesos.gestion.service.FestivosPorPais;

import lombok.RequiredArgsConstructor;

/**
 * PR 68: el calendario de una tienda. Mientras no lo edita vale el de su pais, con la zona de su configuracion y una
 * jornada de lunes a viernes de 8 a 17, y no se guarda nada: leerlo no escribe. Cada cambio queda en la bitacora.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarioServiceImpl implements CalendarioService {

    static final LocalTime ABRE = LocalTime.of(8, 0);
    static final LocalTime CIERRA = LocalTime.of(17, 0);
    static final Set<DayOfWeek> ENTRE_SEMANA = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    static final int PRIMER_ANIO = 1900;
    static final int ULTIMO_ANIO = 2199;

    private static final String FESTIVO = "FESTIVO";
    private static final String FECHA = "fecha";

    private final CalendarioRepository calendarioRepository;
    private final FestivoEditadoRepository festivoEditadoRepository;
    private final ConfiguracionTiendaRepository configuracionRepository;
    private final EmpresaRepository empresaRepository;
    private final Auditoria auditoria;

    @Override
    public CalendarioResponse obtener(Long empresaId) {
        return calendarioRepository.findByEmpresaId(empresaId).map(CalendarioServiceImpl::respuesta)
                .orElseGet(() -> porDefecto(empresaId));
    }

    @Override
    @Transactional
    public CalendarioResponse editar(Long empresaId, Long usuarioId, Jornada jornada, Long version) {
        validar(jornada);
        Calendario calendario = calendarioRepository.findByEmpresaId(empresaId).orElse(null);
        if (calendario == null) {
            if (version != null) {
                throw new ReglaNegocioException("El calendario todavía no se ha guardado: envíelo sin versión.");
            }
            calendario = Calendario.builder().empresa(tienda(empresaId)).build();
        } else {
            calendario.verificarVersion(version);
        }
        calendario.setPais(jornada.pais());
        calendario.setZonaHoraria(jornada.zonaHoraria().strip());
        calendario.setHoraInicio(jornada.horaInicio());
        calendario.setHoraFin(jornada.horaFin());
        calendario.setDiasHabiles(EnumSet.copyOf(jornada.diasHabiles()));
        calendario = calendarioRepository.saveAndFlush(calendario);
        auditoria.registrar(empresaId, usuarioId, "CALENDARIO_EDITADO", "CALENDARIO", calendario.getId(),
                Map.of("pais", jornada.pais().name(), "zonaHoraria", calendario.getZonaHoraria()));
        return respuesta(calendario);
    }

    @Override
    public List<FestivoResponse> festivos(Long empresaId, int anio) {
        exigirAnio(anio);
        return laboral(empresaId).festivos(anio).stream()
                .map(festivo -> new FestivoResponse(festivo.fecha(), festivo.nombre(), festivo.origen()))
                .toList();
    }

    @Override
    public List<EdicionFestivoResponse> ediciones(Long empresaId) {
        return festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(empresaId).stream()
                .map(edicion -> new EdicionFestivoResponse(edicion.getFecha(), edicion.getNombre(),
                        edicion.isFestivo()))
                .toList();
    }

    @Override
    @Transactional
    public FestivoResponse nombrarFestivo(Long empresaId, Long usuarioId, LocalDate fecha, String nombre) {
        exigirAnio(fecha.getYear());
        FestivoEditado edicion = edicionDe(empresaId, fecha);
        edicion.setFestivo(true);
        edicion.setNombre(nombre.strip());
        edicion = festivoEditadoRepository.save(edicion);
        auditoria.registrar(empresaId, usuarioId, "FESTIVO_NOMBRADO", FESTIVO, edicion.getId(),
                Map.of(FECHA, fecha.toString(), "nombre", edicion.getNombre()));
        return new FestivoResponse(fecha, edicion.getNombre(), OrigenFestivo.EDITADO);
    }

    /**
     * Si el festivo lo da la ley, queda anotado que la tienda lo quito; si lo habia agregado ella, se olvida sin mas.
     */
    @Override
    @Transactional
    public void quitarFestivo(Long empresaId, Long usuarioId, LocalDate fecha) {
        if (!laboral(empresaId).esFestivo(fecha)) {
            throw new ReglaNegocioException("El " + fecha + " no es festivo en el calendario de la organización.");
        }
        FestivoEditado edicion = edicionDe(empresaId, fecha);
        if (esCalculado(paisDe(empresaId), fecha)) {
            edicion.setFestivo(false);
            edicion.setNombre(null);
            edicion = festivoEditadoRepository.save(edicion);
        } else if (edicion.getId() != null) {
            festivoEditadoRepository.delete(edicion);
        }
        auditoria.registrar(empresaId, usuarioId, "FESTIVO_QUITADO", FESTIVO, edicion.getId(),
                Map.of(FECHA, fecha.toString()));
    }

    @Override
    @Transactional
    public void restaurarFestivo(Long empresaId, Long usuarioId, LocalDate fecha) {
        FestivoEditado edicion = festivoEditadoRepository.findByEmpresaIdAndFecha(empresaId, fecha)
                .orElseThrow(() -> new RecursoNoEncontradoException("La organización no cambió el " + fecha + "."));
        festivoEditadoRepository.delete(edicion);
        auditoria.registrar(empresaId, usuarioId, "FESTIVO_RESTAURADO", FESTIVO, edicion.getId(),
                Map.of(FECHA, fecha.toString()));
    }

    @Override
    public CalendarioLaboral laboral(Long empresaId) {
        CalendarioResponse calendario = obtener(empresaId);
        Map<LocalDate, String> renombrados = new HashMap<>();
        Set<LocalDate> quitados = new HashSet<>();
        for (FestivoEditado edicion : festivoEditadoRepository.findAllByEmpresaIdOrderByFechaAsc(empresaId)) {
            if (edicion.isFestivo()) {
                renombrados.put(edicion.getFecha(), edicion.getNombre());
            } else {
                quitados.add(edicion.getFecha());
            }
        }
        return new CalendarioLaboral(calendario.pais(), ZoneId.of(calendario.zonaHoraria()), calendario.horaInicio(),
                calendario.horaFin(), EnumSet.copyOf(calendario.diasHabiles()), renombrados, quitados);
    }

    /** El de una tienda que nunca edito el suyo: su pais sale del idioma, y su zona, de su configuracion. */
    private CalendarioResponse porDefecto(Long empresaId) {
        ConfiguracionTienda configuracion = configuracionRepository.findByEmpresaId(empresaId).orElse(null);
        Pais pais = Pais.delIdioma(configuracion == null ? null : configuracion.getIdioma());
        String zona = configuracion == null ? "America/Bogota" : configuracion.getZonaHoraria();
        return new CalendarioResponse(pais, zona, ABRE, CIERRA, List.copyOf(ENTRE_SEMANA), null);
    }

    private static CalendarioResponse respuesta(Calendario calendario) {
        return new CalendarioResponse(calendario.getPais(), calendario.getZonaHoraria(), calendario.getHoraInicio(),
                calendario.getHoraFin(), calendario.getDiasHabiles().stream().sorted().toList(),
                calendario.getVersion());
    }

    /** Los mismos anos que se pueden leer: asi lo que una tienda agrega tiene un tope. */
    private static void exigirAnio(int anio) {
        if (anio < PRIMER_ANIO || anio > ULTIMO_ANIO) {
            throw new SolicitudInvalidaException("El año va de " + PRIMER_ANIO + " a " + ULTIMO_ANIO + ".");
        }
    }

    private static void validar(Jornada jornada) {
        try {
            ZoneId.of(jornada.zonaHoraria().strip());
        } catch (DateTimeException _) {
            throw new SolicitudInvalidaException("\"" + jornada.zonaHoraria() + "\" no es una zona horaria.");
        }
        if (!jornada.horaInicio().isBefore(jornada.horaFin())) {
            throw new SolicitudInvalidaException("La jornada empieza antes de terminar.");
        }
    }

    private FestivoEditado edicionDe(Long empresaId, LocalDate fecha) {
        return festivoEditadoRepository.findByEmpresaIdAndFecha(empresaId, fecha)
                .orElseGet(() -> FestivoEditado.builder().empresa(tienda(empresaId)).fecha(fecha).build());
    }

    private Pais paisDe(Long empresaId) {
        return obtener(empresaId).pais();
    }

    private static boolean esCalculado(Pais pais, LocalDate fecha) {
        return FestivosPorPais.de(pais, fecha.getYear()).stream().map(Festivo::fecha).anyMatch(fecha::equals);
    }

    private Empresa tienda(Long empresaId) {
        return empresaRepository.getReferenceById(empresaId);
    }
}
