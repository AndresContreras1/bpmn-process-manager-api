package com.facimus.procesos.gestion.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.model.Carpeta;
import com.facimus.procesos.gestion.model.Concesion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.CarpetaRepository;
import com.facimus.procesos.gestion.repository.ConcesionRepository;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.Visibilidad;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermisosImpl implements Permisos {

    private static final Set<Accion> DE_LECTURA = EnumSet.of(Accion.VER, Accion.COMENTAR, Accion.EXPORTAR);

    private final ConcesionRepository concesionRepository;
    private final CarpetaRepository carpetaRepository;
    private final ProcesoRepository procesoRepository;
    private final MembresiaRolRepository membresiaRolRepository;
    private final UsuarioRepository usuarioRepository;

    /** Lo que hace falta para evaluar: se lee una vez por pregunta, y si la tienda no concedio nada, no se lee. */
    private record Tablas(List<Concesion> concesiones, Map<Long, Long> padres, Set<Long> roles) {
    }

    @Override
    public boolean puede(Actor actor, Accion accion, Recurso recurso) {
        // Separacion de funciones: lo que uno mismo cambio lo aprueba otra persona, tambien si administra
        if (accion == Accion.APROBAR && esElUltimoEnCambiarlo(actor, recurso)) {
            return false;
        }
        if (actor.rol() == RolAcceso.ADMINISTRADOR) {
            return true;
        }
        if (actor.rol() == RolAcceso.SOLO_LECTURA && !DE_LECTURA.contains(accion)) {
            // Lo que no es de la tienda no se evalua aqui: responde el servicio, con su 404
            return !esDeLaTienda(actor.empresaId(), recurso);
        }
        if (!concesionRepository.existsByEmpresaId(actor.empresaId())) {
            return porDefecto(actor, accion);
        }
        Tablas tablas = tablas(actor);
        Optional<List<Concesion>> cadena = cadena(tablas, actor.empresaId(), recurso);
        return cadena.isEmpty() || decidir(cadena.get(), tablas.roles(), actor, accion);
    }

    @Override
    public void exigir(Actor actor, Accion accion, Recurso recurso) {
        if (!puede(actor, accion, recurso)) {
            if (accion == Accion.VER) {
                throw noEncontrado(recurso);
            }
            throw new SinPermisoException("No tienes permiso para hacer esto aquí.");
        }
    }

    @Override
    public void exigir(Long empresaId, Long usuarioId, Accion accion, Recurso recurso) {
        exigir(actor(empresaId, usuarioId), accion, recurso);
    }

    @Override
    public Set<Accion> acciones(Actor actor, Recurso recurso) {
        // Lo ajeno no tiene acciones que contar: responde 404, como cada lectura del recurso
        if (!esDeLaTienda(actor.empresaId(), recurso)) {
            throw noEncontrado(recurso);
        }
        Set<Accion> acciones = EnumSet.noneOf(Accion.class);
        for (Accion accion : Accion.values()) {
            if (puede(actor, accion, recurso)) {
                acciones.add(accion);
            }
        }
        return acciones;
    }

    @Override
    public boolean concedido(Long empresaId, Long usuarioId, Accion accion, Recurso recurso) {
        if (!concesionRepository.existsByEmpresaId(empresaId)) {
            return false;
        }
        Actor actor = actor(empresaId, usuarioId);
        Tablas tablas = tablas(actor);
        return cadena(tablas, empresaId, recurso).orElse(List.of()).stream()
                .anyMatch(concesion -> aplica(concesion, actor, tablas.roles()) && cubre(concesion, accion));
    }

    @Override
    public boolean tieneConcesiones(Long empresaId, Recurso recurso) {
        if (!concesionRepository.existsByEmpresaId(empresaId)) {
            return false;
        }
        Tablas tablas = tablas(new Actor(null, empresaId, RolAcceso.ADMINISTRADOR));
        return !cadena(tablas, empresaId, recurso).orElse(List.of()).isEmpty();
    }

    @Override
    public Visibilidad visibilidad(Actor actor) {
        if (actor.rol() == RolAcceso.ADMINISTRADOR || !concesionRepository.existsByEmpresaId(actor.empresaId())) {
            return Visibilidad.TODO;
        }
        Tablas tablas = tablas(actor);
        if (tablas.concesiones().stream().noneMatch(concesion -> concesion.getAccion() == Accion.VER)
                && actor.rol() != RolAcceso.SOLO_LECTURA) {
            return Visibilidad.TODO;
        }
        Set<Long> carpetasOcultas = new HashSet<>();
        for (Long carpeta : tablas.padres().keySet()) {
            if (!decidir(cadenaDeCarpeta(tablas, carpeta), tablas.roles(), actor, Accion.VER)) {
                carpetasOcultas.add(carpeta);
            }
        }
        Set<Long> ocultos = new HashSet<>();
        Set<Long> visibles = new HashSet<>();
        Set<Long> conConcesion = new HashSet<>();
        tablas.concesiones().stream().map(Concesion::getProcesoId).filter(Objects::nonNull).forEach(conConcesion::add);
        for (Proceso proceso : procesoRepository.findAllByIdInAndEmpresaId(conConcesion, actor.empresaId())) {
            List<Concesion> cadena = cadenaDeProceso(tablas, proceso.getId(), proceso.getCarpetaId());
            (decidir(cadena, tablas.roles(), actor, Accion.VER) ? visibles : ocultos).add(proceso.getId());
        }
        return new Visibilidad(false, carpetasOcultas, ocultos, visibles);
    }

    @Override
    public Set<Long> quienesPueden(Long empresaId, Recurso recurso, Collection<Actor> actores, Set<Accion> acciones) {
        Set<Long> pueden = new HashSet<>();
        if (actores.isEmpty()) {
            return pueden;
        }
        Lectura lectura = leer(empresaId, recurso, actores);
        for (Actor actor : actores) {
            if (acciones.stream().allMatch(accion -> puede(lectura, actor, accion))) {
                pueden.add(actor.usuarioId());
            }
        }
        return pueden;
    }

    /**
     * Lo que se lee una vez para evaluar a varias personas sobre el mismo recurso: quien lo cambio por ultima vez, si
     * es de la tienda, las concesiones que lo alcanzan (null si la tienda no concedio nada o el recurso no es suyo) y
     * los roles de proceso de cada una.
     */
    private record Lectura(Long ultimo, boolean deLaTienda, boolean conConcesiones, List<Concesion> cadena,
            Map<Long, Set<Long>> roles) {
    }

    private Lectura leer(Long empresaId, Recurso recurso, Collection<Actor> actores) {
        Optional<Proceso> proceso = recurso.tipo() == Recurso.Tipo.PROCESO
                ? procesoRepository.findByIdAndEmpresaId(recurso.id(), empresaId) : Optional.empty();
        Long ultimo = proceso.map(Proceso::getModificadoPor).orElse(null);
        boolean deLaTienda = recurso.tipo() == Recurso.Tipo.PROCESO ? proceso.isPresent()
                : carpetaRepository.existsByIdAndEmpresaId(recurso.id(), empresaId);
        if (!concesionRepository.existsByEmpresaId(empresaId)) {
            return new Lectura(ultimo, deLaTienda, false, null, Map.of());
        }
        Tablas tablas = tablas(new Actor(null, empresaId, RolAcceso.ADMINISTRADOR));
        List<Concesion> cadena = null;
        if (recurso.tipo() == Recurso.Tipo.CARPETA && tablas.padres().containsKey(recurso.id())) {
            cadena = cadenaDeCarpeta(tablas, recurso.id());
        } else if (proceso.isPresent()) {
            cadena = cadenaDeProceso(tablas, proceso.get().getId(), proceso.get().getCarpetaId());
        }
        Map<Long, Set<Long>> roles = new HashMap<>();
        List<Long> ids = actores.stream().map(Actor::usuarioId).distinct().toList();
        for (Object[] par : membresiaRolRepository.rolesDeCada(empresaId, ids)) {
            roles.computeIfAbsent((Long) par[0], _ -> new HashSet<>()).add((Long) par[1]);
        }
        return new Lectura(ultimo, deLaTienda, true, cadena, roles);
    }

    /** La misma decision que {@link #puede(Actor, Accion, Recurso)}, con lo leido para todas. */
    private static boolean puede(Lectura lectura, Actor actor, Accion accion) {
        if (accion == Accion.APROBAR && actor.usuarioId().equals(lectura.ultimo())) {
            return false;
        }
        if (actor.rol() == RolAcceso.ADMINISTRADOR) {
            return true;
        }
        if (actor.rol() == RolAcceso.SOLO_LECTURA && !DE_LECTURA.contains(accion)) {
            return !lectura.deLaTienda();
        }
        if (!lectura.conConcesiones()) {
            return porDefecto(actor, accion);
        }
        return lectura.cadena() == null
                || decidir(lectura.cadena(), lectura.roles().getOrDefault(actor.usuarioId(), Set.of()), actor, accion);
    }

    /** Con concesiones de esa accion, solo quien tenga una; sin ninguna, lo de siempre. Ver lo trae cualquier otra. */
    private static boolean decidir(List<Concesion> cadena, Set<Long> roles, Actor actor, Accion accion) {
        boolean tiene = cadena.stream().anyMatch(concesion -> aplica(concesion, actor, roles) && cubre(concesion, accion));
        if (tiene) {
            return true;
        }
        boolean restringida = cadena.stream().anyMatch(concesion -> concesion.getAccion() == accion);
        return !restringida && porDefecto(actor, accion);
    }

    private static boolean cubre(Concesion concesion, Accion accion) {
        return concesion.getAccion() == accion || concesion.getAccion() == Accion.ADMINISTRAR || accion == Accion.VER;
    }

    private static boolean aplica(Concesion concesion, Actor actor, Set<Long> roles) {
        return switch (concesion.getSujeto()) {
            case ORGANIZACION -> true;
            case PERSONA -> concesion.getUsuarioId().equals(actor.usuarioId());
            case ROL -> roles.contains(concesion.getRolProcesoId());
        };
    }

    /** D16 entra aqui como concesion por defecto: sin ninguna expresa, quien edita modela. */
    private static boolean porDefecto(Actor actor, Accion accion) {
        return switch (accion) {
            case VER, COMENTAR, EXPORTAR -> true;
            case MODELAR, REVISAR, APROBAR, PUBLICAR -> actor.rol() != RolAcceso.SOLO_LECTURA;
            case ADMINISTRAR -> actor.rol() == RolAcceso.ADMINISTRADOR;
        };
    }

    private static RecursoNoEncontradoException noEncontrado(Recurso recurso) {
        return new RecursoNoEncontradoException(
                recurso.tipo() == Recurso.Tipo.PROCESO ? "Proceso no encontrado." : "Carpeta no encontrada.");
    }

    private boolean esDeLaTienda(Long empresaId, Recurso recurso) {
        return recurso.tipo() == Recurso.Tipo.PROCESO ? procesoRepository.existsByIdAndEmpresaId(recurso.id(), empresaId)
                : carpetaRepository.existsByIdAndEmpresaId(recurso.id(), empresaId);
    }

    private boolean esElUltimoEnCambiarlo(Actor actor, Recurso recurso) {
        return recurso.tipo() == Recurso.Tipo.PROCESO
                && procesoRepository.findByIdAndEmpresaId(recurso.id(), actor.empresaId())
                        .map(proceso -> actor.usuarioId().equals(proceso.getModificadoPor())).orElse(false);
    }

    private Tablas tablas(Actor actor) {
        Map<Long, Long> padres = new HashMap<>();
        for (Carpeta carpeta : carpetaRepository.findAllByEmpresaId(actor.empresaId())) {
            padres.put(carpeta.getId(), carpeta.getPadreId());
        }
        Set<Long> roles = actor.usuarioId() == null ? Set.of()
                : new HashSet<>(membresiaRolRepository.rolesDe(actor.empresaId(), actor.usuarioId()));
        return new Tablas(concesionRepository.findAllByEmpresaId(actor.empresaId()), padres, roles);
    }

    /** Las concesiones que alcanzan al recurso; vacio si el recurso no es de la tienda. */
    private Optional<List<Concesion>> cadena(Tablas tablas, Long empresaId, Recurso recurso) {
        if (recurso.tipo() == Recurso.Tipo.CARPETA) {
            return tablas.padres().containsKey(recurso.id())
                    ? Optional.of(cadenaDeCarpeta(tablas, recurso.id())) : Optional.empty();
        }
        return procesoRepository.findByIdAndEmpresaId(recurso.id(), empresaId)
                .map(proceso -> cadenaDeProceso(tablas, proceso.getId(), proceso.getCarpetaId()));
    }

    private static List<Concesion> cadenaDeProceso(Tablas tablas, Long procesoId, Long carpetaId) {
        List<Concesion> cadena = new ArrayList<>(
                tablas.concesiones().stream().filter(una -> procesoId.equals(una.getProcesoId())).toList());
        if (carpetaId != null) {
            cadena.addAll(cadenaDeCarpeta(tablas, carpetaId));
        }
        return cadena;
    }

    private static List<Concesion> cadenaDeCarpeta(Tablas tablas, Long carpetaId) {
        Set<Long> ancestros = new HashSet<>();
        for (Long actual = carpetaId; actual != null && ancestros.add(actual); actual = tablas.padres().get(actual)) {
            // Solo recorre: el arbol no tiene ciclos, y el add corta si alguna vez los tuviera
        }
        return tablas.concesiones().stream()
                .filter(una -> una.getCarpetaId() != null && ancestros.contains(una.getCarpetaId())).toList();
    }

    private Actor actor(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .map(usuario -> new Actor(usuarioId, empresaId, usuario.getRolAcceso()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
