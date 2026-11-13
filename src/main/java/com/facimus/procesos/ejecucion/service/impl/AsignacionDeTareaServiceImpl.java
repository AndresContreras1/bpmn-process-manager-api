package com.facimus.procesos.ejecucion.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.mapper.CasoMapper;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.Delegacion;
import com.facimus.procesos.ejecucion.model.EstadoActividadCaso;
import com.facimus.procesos.ejecucion.model.EstadoCaso;
import com.facimus.procesos.ejecucion.model.EstadoDelegacion;
import com.facimus.procesos.ejecucion.model.TipoEventoCaso;
import com.facimus.procesos.ejecucion.repository.ActividadCasoRepository;
import com.facimus.procesos.ejecucion.repository.DelegacionRepository;
import com.facimus.procesos.ejecucion.service.AsignacionDeTareaService;
import com.facimus.procesos.gestion.dto.response.UsuarioResponse;

import lombok.RequiredArgsConstructor;

/**
 * PR 70: tomar, soltar, reasignar y delegar. Cada accion bloquea el caso antes de leer la tarea, como completar (D3),
 * asi que no se cruza con una cancelacion ni con un temporizador que salta; reclamar ademas es una escritura atomica
 * que solo pasa si nadie tiene la tarea (ver {@link ActividadCasoRepository#reclamar}). Solo se hace algo con una
 * tarea que espera en un caso abierto. Cada accion deja su linea en el caso y su suceso en la bitacora.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsignacionDeTareaServiceImpl implements AsignacionDeTareaService {

    static final String YA_NO_ESPERA = "La tarea ya no espera a nadie.";
    static final String YA_LA_TIENE_ALGUIEN = "La tarea ya la tiene alguien.";
    static final String NO_LA_TIENE_NADIE = "La tarea no la tiene nadie: espera en la bandeja de su rol.";
    static final String SOLTAR_NO_ES_SUYO = "Solo quien tiene la tarea, o un administrador, la suelta.";
    static final String REASIGNAR_NO_ES_SUYO = "Solo quien tiene la tarea, o un administrador, la reasigna.";
    static final String REASIGNAR_DELEGADA = "Una tarea delegada la reasigna quien la delego, o un administrador: "
            + "el delegado la completa o la devuelve.";
    static final String DELEGAR_NO_ES_SUYO = "Solo quien tiene la tarea la delega.";
    static final String YA_ES_DE_ESA_PERSONA = "La tarea ya la tiene esa persona.";
    static final String YA_DELEGADA = "Una tarea delegada no se vuelve a delegar: quien la tiene la completa o la "
            + "devuelve.";
    static final String A_SI_MISMO = "La tarea ya es tuya: se delega en otra persona.";

    static final String RECLAMADA = "TAREA_RECLAMADA";
    static final String LIBERADA = "TAREA_LIBERADA";
    static final String REASIGNADA = "TAREA_REASIGNADA";
    static final String DELEGADA = "TAREA_DELEGADA";
    static final String CASO_ID = "casoId";

    private final ActividadCasoRepository actividadCasoRepository;
    private final DelegacionRepository delegacionRepository;
    private final Tareas tareas;
    private final PersonasDeLaTarea personas;
    private final RelojDeLaTienda reloj;
    private final Bitacora bitacora;
    private final Auditoria auditoria;
    private final CasoMapper casoMapper;

    @Override
    @Transactional
    public TareaResponse reclamar(Long empresaId, Long usuarioId, Long tareaId) {
        Caso caso = tareas.bloquearSuCaso(empresaId, tareaId);
        ActividadCaso tarea = enEspera(caso, tareas.exigir(empresaId, tareaId));
        if (tarea.getAsignadoA() != null) {
            throw new ReglaNegocioException(YA_LA_TIENE_ALGUIEN);
        }
        UsuarioResponse quien = personas.quienActua(empresaId, usuarioId);
        if (!personas.esCandidata(empresaId, quien, tarea)) {
            throw new SinPermisoException(TareaServiceImpl.NO_ES_DE_SU_ROL);
        }
        // La escritura solo pasa si la tarea sigue sin nadie: la que alguien tomo de otro modo no se toca
        if (actividadCasoRepository.reclamar(tareaId, empresaId, usuarioId, LocalDateTime.now()) == 0) {
            throw new ReglaNegocioException(YA_LA_TIENE_ALGUIEN);
        }
        ActividadCaso tomada = tareas.exigir(empresaId, tareaId);
        anotar(tomada, TipoEventoCaso.TAREA_RECLAMADA,
                "\"" + tomada.getNodoNombre() + "\" la tomo el usuario " + usuarioId + ".", usuarioId);
        auditoria.registrar(empresaId, usuarioId, RECLAMADA, TareaServiceImpl.RECURSO, tareaId,
                Map.of(CASO_ID, tomada.getCaso().getId()));
        return casoMapper.toTarea(tomada);
    }

    @Override
    @Transactional
    public TareaResponse soltar(Long empresaId, Long usuarioId, Long tareaId, String motivo) {
        Caso caso = tareas.bloquearSuCaso(empresaId, tareaId);
        ActividadCaso tarea = enEspera(caso, tareas.exigir(empresaId, tareaId));
        Long laTenia = tarea.getAsignadoA();
        if (laTenia == null) {
            throw new ReglaNegocioException(NO_LA_TIENE_NADIE);
        }
        boolean esSuya = usuarioId.equals(laTenia);
        if (!esSuya && !PersonasDeLaTarea.administra(personas.quienActua(empresaId, usuarioId))) {
            throw new SinPermisoException(SOLTAR_NO_ES_SUYO);
        }
        // Un delegado que la suelta se la devuelve a quien se la delego, si esa persona aun puede tenerla; lo demas
        // vuelve a la bandeja del rol
        Long delegador = esSuya ? tarea.getDelegadaPor() : null;
        Long vuelveA = delegador == null ? null : personas.siPuedeTenerla(empresaId, delegador).orElse(null);
        tareas.cerrarDelegacion(tarea, vuelveA == null ? EstadoDelegacion.ANULADA : EstadoDelegacion.DEVUELTA);
        tarea.setAsignadoA(vuelveA);
        tarea.setDelegadaPor(null);
        actividadCasoRepository.save(tarea);
        anotar(tarea, TipoEventoCaso.TAREA_LIBERADA, "\"" + tarea.getNodoNombre() + "\" deja de tenerla el usuario "
                + laTenia + " y " + adonde(delegador, vuelveA) + conMotivo(motivo), usuarioId);
        auditoria.registrar(empresaId, usuarioId, LIBERADA, TareaServiceImpl.RECURSO, tareaId,
                detalle(tarea, laTenia, vuelveA));
        return casoMapper.toTarea(tarea);
    }

    @Override
    @Transactional
    public TareaResponse reasignar(Long empresaId, Long usuarioId, Long tareaId, Long aQuien, String motivo) {
        Caso caso = tareas.bloquearSuCaso(empresaId, tareaId);
        ActividadCaso tarea = enEspera(caso, tareas.exigir(empresaId, tareaId));
        Long laTenia = tarea.getAsignadoA();
        // La delegada la reparte quien la delego: el delegado no se la quita pasandosela a un tercero
        Long responde = tarea.getDelegadaPor() == null ? laTenia : tarea.getDelegadaPor();
        if (!usuarioId.equals(responde) && !PersonasDeLaTarea.administra(personas.quienActua(empresaId, usuarioId))) {
            throw new SinPermisoException(usuarioId.equals(laTenia) ? REASIGNAR_DELEGADA : REASIGNAR_NO_ES_SUYO);
        }
        personas.quienRecibe(empresaId, aQuien);
        if (aQuien.equals(laTenia)) {
            throw new ReglaNegocioException(YA_ES_DE_ESA_PERSONA);
        }
        tareas.cerrarDelegacion(tarea, EstadoDelegacion.ANULADA);
        tarea.setAsignadoA(aQuien);
        tarea.setDelegadaPor(null);
        actividadCasoRepository.save(tarea);
        String desde = laTenia == null ? "de la bandeja de su rol" : "del usuario " + laTenia;
        anotar(tarea, TipoEventoCaso.TAREA_REASIGNADA, "\"" + tarea.getNodoNombre() + "\" pasa " + desde
                + " al usuario " + aQuien + conMotivo(motivo), usuarioId);
        auditoria.registrar(empresaId, usuarioId, REASIGNADA, TareaServiceImpl.RECURSO, tareaId,
                detalle(tarea, laTenia, aQuien));
        return casoMapper.toTarea(tarea);
    }

    @Override
    @Transactional
    public TareaResponse delegar(Long empresaId, Long usuarioId, Long tareaId, Long aQuien, String motivo) {
        Caso caso = tareas.bloquearSuCaso(empresaId, tareaId);
        ActividadCaso tarea = enEspera(caso, tareas.exigir(empresaId, tareaId));
        if (!usuarioId.equals(tarea.getAsignadoA())) {
            throw new SinPermisoException(DELEGAR_NO_ES_SUYO);
        }
        if (tarea.getDelegadaPor() != null) {
            throw new ReglaNegocioException(YA_DELEGADA);
        }
        if (usuarioId.equals(aQuien)) {
            throw new ReglaNegocioException(A_SI_MISMO);
        }
        personas.quienRecibe(empresaId, aQuien);
        delegacionRepository.save(Delegacion.builder()
                .empresa(tarea.getEmpresa())
                .tarea(tarea)
                .caso(caso)
                .delegadaPor(usuarioId)
                .delegadaA(aQuien)
                .motivo(motivo.strip())
                .estado(EstadoDelegacion.ACTIVA)
                .build());
        tarea.setDelegadaPor(usuarioId);
        tarea.setAsignadoA(aQuien);
        actividadCasoRepository.save(tarea);
        anotar(tarea, TipoEventoCaso.TAREA_DELEGADA, "\"" + tarea.getNodoNombre() + "\" la delega el usuario "
                + usuarioId + " en el usuario " + aQuien + conMotivo(motivo), usuarioId);
        auditoria.registrar(empresaId, usuarioId, DELEGADA, TareaServiceImpl.RECURSO, tareaId,
                detalle(tarea, usuarioId, aQuien));
        return casoMapper.toTarea(tarea);
    }

    /**
     * Solo se hace algo con una tarea que espera en un caso abierto: la completada ya no es de nadie, y la de un caso
     * en error o cerrado no la completaria nadie.
     */
    private static ActividadCaso enEspera(Caso caso, ActividadCaso tarea) {
        if (tarea.getEstado() != EstadoActividadCaso.EN_ESPERA) {
            throw new ReglaNegocioException(YA_NO_ESPERA);
        }
        if (caso.getEstado() != EstadoCaso.ABIERTO) {
            throw new ReglaNegocioException(Tareas.CASO_CERRADO);
        }
        return tarea;
    }

    /** Adonde va la tarea que se suelta: a quien la delego, o a la bandeja del rol, y si no vuelve a el, por que. */
    private static String adonde(Long delegador, Long vuelveA) {
        if (vuelveA != null) {
            return "vuelve al usuario " + vuelveA + ", que la habia delegado";
        }
        if (delegador != null) {
            return "vuelve a la bandeja de su rol, porque el usuario " + delegador
                    + ", que la habia delegado, ya no puede tener tareas";
        }
        return "vuelve a la bandeja de su rol";
    }

    private void anotar(ActividadCaso tarea, TipoEventoCaso tipo, String detalle, Long usuarioId) {
        Caso caso = tarea.getCaso();
        bitacora.anotar(caso, reloj.ahora(caso.getEmpresa().getId()), tipo, detalle, usuarioId);
    }

    private static String conMotivo(String motivo) {
        return motivo == null || motivo.isBlank() ? "." : ": " + motivo.strip();
    }

    /** Lo que la bitacora guarda de cada accion: el caso y entre quienes paso; el motivo se queda en el caso. */
    private static Map<String, Object> detalle(ActividadCaso tarea, Long de, Long a) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put(CASO_ID, tarea.getCaso().getId());
        if (de != null) {
            detalle.put("de", de);
        }
        if (a != null) {
            detalle.put("a", a);
        }
        return detalle;
    }
}
