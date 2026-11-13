package com.facimus.procesos.gestion.service.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.gestion.dto.response.PoliticaDePublicacionResponse;
import com.facimus.procesos.gestion.dto.response.PoliticaDePublicacionResponse.Aprobador;
import com.facimus.procesos.gestion.event.UsuarioAnonimizado;
import com.facimus.procesos.gestion.model.ModoDePublicacion;
import com.facimus.procesos.gestion.model.PoliticaPublicacion;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.RolProceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.MembresiaRolRepository;
import com.facimus.procesos.gestion.repository.PoliticaPublicacionRepository;
import com.facimus.procesos.gestion.repository.RolProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Actor;
import com.facimus.procesos.gestion.service.DatosDePolitica;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.PoliticaDePublicacionService;
import com.facimus.procesos.gestion.service.Recurso;
import com.facimus.procesos.gestion.service.ReglaDeAprobacion;

import lombok.RequiredArgsConstructor;

/**
 * PR 64: la politica de publicacion de una tienda. Sin fila, publica directo. Los aprobadores de una solicitud se
 * resuelven al pedirla: las personas de la lista o del rol que siguen activas, que pueden aprobar ese proceso segun el
 * evaluador de permisos (D31), y nunca quien la pide.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PoliticaDePublicacionServiceImpl implements PoliticaDePublicacionService {

    private static final String APROBADORES = "aprobadores";
    private static final String NO_ES_DE_LA_TIENDA = "No es una persona activa de la organización.";
    private static final String SOLO_LEE = "Es de solo lectura: no puede aprobar.";
    private static final Set<Accion> PARA_APROBAR = EnumSet.of(Accion.VER, Accion.APROBAR);

    private final PoliticaPublicacionRepository politicaRepository;
    private final RolProcesoRepository rolProcesoRepository;
    private final MembresiaRolRepository membresiaRolRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final Permisos permisos;
    private final HistorialCambioService historialCambioService;
    private final Auditoria auditoria;

    @Override
    public PoliticaDePublicacionResponse obtener(Long empresaId) {
        return politicaRepository.findByEmpresaId(empresaId).map(politica -> respuesta(empresaId, politica))
                .orElseGet(() -> new PoliticaDePublicacionResponse(ModoDePublicacion.DIRECTA, 1, null, null,
                        List.of(), null, null, null));
    }

    @Override
    @Transactional
    public PoliticaDePublicacionResponse guardar(Long empresaId, Long usuarioId, DatosDePolitica datos) {
        PoliticaPublicacion politica = politicaRepository.findByEmpresaId(empresaId).orElse(null);
        if (politica == null) {
            if (datos.version() != null) {
                // Quien manda una version leyo una politica que ya no esta: la tienda nunca guardo esta
                throw new ConflictoDeVersionException(datos.version(), null);
            }
            Empresa empresa = empresaRepository.findById(empresaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
            politica = PoliticaPublicacion.builder().empresa(empresa).build();
        } else {
            politica.verificarVersion(datos.version());
        }
        int necesarias = datos.aprobacionesNecesarias() == null ? 1 : datos.aprobacionesNecesarias();
        List<Long> aprobadores = datos.aprobadores().stream().distinct().toList();
        Long rolProcesoId = datos.rolProcesoId();
        if (rolProcesoId != null && !aprobadores.isEmpty()) {
            throw new SolicitudInvalidaException("Los aprobadores salen de una lista o de un rol de proceso, no de los "
                    + "dos.");
        }
        if (datos.modo() == ModoDePublicacion.CON_APROBACION) {
            validar(empresaId, necesarias, rolProcesoId, aprobadores);
        } else {
            // Apagarla no depende de quienes aprobaban: quien ya no puede aprobar, o un rol que ya no esta, se
            // descarta sin error, y lo demas se guarda para volver a encenderla igual
            Set<Long> pueden = idsDe(quienesAprueban(empresaId, aprobadores));
            aprobadores = aprobadores.stream().filter(pueden::contains).toList();
            rolProcesoId = siSigueActivo(empresaId, rolProcesoId);
        }

        ModoDePublicacion antes = politica.getModo();
        politica.setModo(datos.modo());
        politica.setAprobacionesNecesarias(necesarias);
        politica.setRolProcesoId(rolProcesoId);
        politica.setAprobadores(new ArrayList<>(aprobadores));
        politica = politicaRepository.saveAndFlush(politica);

        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                descripcion(politica));
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("modo", datos.modo().name());
        detalle.put("antes", antes.name());
        detalle.put("aprobacionesNecesarias", necesarias);
        detalle.put(APROBADORES, aprobadores.size());
        if (rolProcesoId != null) {
            detalle.put("rolProcesoId", rolProcesoId);
        }
        auditoria.registrar(empresaId, usuarioId, "POLITICA_PUBLICACION_CAMBIADA", "EMPRESA", empresaId, detalle);
        return respuesta(empresaId, politica);
    }

    /** El rol, si sigue activo en la tienda; si no, ninguno. */
    private Long siSigueActivo(Long empresaId, Long rolProcesoId) {
        if (rolProcesoId == null
                || rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolProcesoId, empresaId).isEmpty()) {
            return null;
        }
        return rolProcesoId;
    }

    /** Lo que queda en el historial de la tienda. */
    private static String descripcion(PoliticaPublicacion politica) {
        if (!politica.conAprobacion()) {
            return "Publicación directa.";
        }
        int necesarias = politica.getAprobacionesNecesarias();
        String cuantas = necesarias == 1 ? " aprobación" : " aprobaciones";
        String deQuien = politica.getRolProcesoId() == null ? " de una lista de personas." : " de un rol de proceso.";
        return "Publicación con aprobación: " + necesarias + cuantas + deQuien;
    }

    /** PR 35: quien se anonimiza deja la lista de aprobadores, como deja sus roles de proceso. */
    @EventListener
    @Transactional
    public void alAnonimizarse(UsuarioAnonimizado anonimizado) {
        politicaRepository.quitarAprobador(anonimizado.empresaId(), anonimizado.usuarioId());
    }

    @Override
    public boolean conAprobacion(Long empresaId) {
        return politicaRepository.findByEmpresaId(empresaId).map(PoliticaPublicacion::conAprobacion).orElse(false);
    }

    @Override
    public Optional<ReglaDeAprobacion> reglaPara(Long empresaId, Long procesoId, Long solicitanteId) {
        return politicaRepository.findByEmpresaId(empresaId).filter(PoliticaPublicacion::conAprobacion)
                .map(politica -> new ReglaDeAprobacion(politica.getAprobacionesNecesarias(),
                        aprobadoresDe(empresaId, procesoId, solicitanteId, fuente(empresaId, politica))));
    }

    /**
     * Las personas de la fuente que siguen activas y pueden ver y aprobar ese proceso, sin quien pide, de menor a
     * mayor id. Aprobar lo decide el evaluador entero (D31): una concesion que reserva aprobar, y la separacion de
     * funciones, que no deja aprobar a quien cambio el proceso por ultima vez. Se evaluan todas de una vez, con las
     * tablas leidas una sola vez: el proceso esta tomado mientras se abre la solicitud.
     */
    private List<Long> aprobadoresDe(Long empresaId, Long procesoId, Long solicitanteId, List<Long> fuente) {
        List<Long> otros = fuente.stream().filter(id -> !id.equals(solicitanteId)).distinct().toList();
        List<Actor> actores = activos(empresaId, otros).stream()
                .map(usuario -> new Actor(usuario.getId(), empresaId, usuario.getRolAcceso())).toList();
        return permisos.quienesPueden(empresaId, Recurso.proceso(procesoId), actores, PARA_APROBAR).stream().sorted()
                .toList();
    }

    /** Las que siguen activas en la tienda, de esas. */
    private List<Usuario> activos(Long empresaId, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, ids).stream().filter(Usuario::isActivo)
                .toList();
    }

    /** Las que podrian aprobar algo: activas y con un rol que no es solo de lectura. */
    private List<Usuario> quienesAprueban(Long empresaId, List<Long> ids) {
        return activos(empresaId, ids).stream().filter(usuario -> usuario.getRolAcceso() != RolAcceso.SOLO_LECTURA)
                .toList();
    }

    private static Set<Long> idsDe(List<Usuario> usuarios) {
        return usuarios.stream().map(Usuario::getId).collect(Collectors.toSet());
    }

    /** Un rol desactivado ya no tiene a nadie que apruebe. */
    private List<Long> fuente(Long empresaId, PoliticaPublicacion politica) {
        if (politica.getRolProcesoId() == null) {
            return politica.getAprobadores();
        }
        return rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(politica.getRolProcesoId(), empresaId)
                .map(rol -> membresiaRolRepository.personasDe(empresaId, rol.getId())).orElse(List.of());
    }

    /**
     * Con aprobacion, la fuente elegida tiene que tener al menos tantas personas que aprueban como aprobaciones pide.
     * Cada aprobador de la lista es alguien activo de la tienda que no es de solo lectura: uno que no esta activo, de
     * otra tienda o que no existe responde lo mismo. De un rol cuentan solo esas personas.
     */
    private void validar(Long empresaId, int necesarias, Long rolProcesoId, List<Long> aprobadores) {
        int personas;
        if (rolProcesoId != null) {
            RolProceso rol = rolProcesoRepository.findByIdAndEmpresaIdAndActivoTrue(rolProcesoId, empresaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Rol de proceso no encontrado."));
            personas = quienesAprueban(empresaId, membresiaRolRepository.personasDe(empresaId, rol.getId())).size();
        } else if (aprobadores.isEmpty()) {
            throw new SolicitudInvalidaException("Con aprobación hay que decir quién aprueba: una lista de personas o "
                    + "un rol de proceso.", Map.of(APROBADORES, "Elige personas o un rol de proceso."));
        } else {
            exigirQuienesAprueban(empresaId, aprobadores);
            personas = aprobadores.size();
        }
        if (personas < necesarias) {
            throw new SolicitudInvalidaException("La política pide " + necesarias + " aprobaciones y quienes aprueban "
                    + "son " + personas + ".", Map.of("aprobacionesNecesarias",
                            "No puede pedir más aprobaciones que personas que aprueban."));
        }
    }

    private void exigirQuienesAprueban(Long empresaId, List<Long> aprobadores) {
        Map<Long, Usuario> activos = new HashMap<>();
        activos(empresaId, aprobadores).forEach(usuario -> activos.put(usuario.getId(), usuario));
        Map<String, String> errores = new TreeMap<>();
        for (int i = 0; i < aprobadores.size(); i++) {
            Usuario usuario = activos.get(aprobadores.get(i));
            String campo = APROBADORES + "[" + i + "]";
            if (usuario == null) {
                errores.put(campo, NO_ES_DE_LA_TIENDA);
            } else if (usuario.getRolAcceso() == RolAcceso.SOLO_LECTURA) {
                errores.put(campo, SOLO_LEE);
            }
        }
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException("Solo aprueban personas activas de la organización que no son de "
                    + "solo lectura.", errores);
        }
    }

    private PoliticaDePublicacionResponse respuesta(Long empresaId, PoliticaPublicacion politica) {
        String rol = politica.getRolProcesoId() == null ? null
                : rolProcesoRepository.findByIdAndEmpresaId(politica.getRolProcesoId(), empresaId)
                        .map(RolProceso::getNombre).orElse(null);
        Map<Long, Usuario> personas = new HashMap<>();
        if (!politica.getAprobadores().isEmpty()) {
            usuarioRepository.findAllByEmpresaIdAndIdIn(empresaId, new HashSet<>(politica.getAprobadores()))
                    .forEach(usuario -> personas.put(usuario.getId(), usuario));
        }
        List<Aprobador> aprobadores = politica.getAprobadores().stream().map(id -> {
            Usuario usuario = personas.get(id);
            return new Aprobador(id, usuario == null ? null : usuario.getNombre(),
                    usuario != null && usuario.isActivo());
        }).toList();
        return new PoliticaDePublicacionResponse(politica.getModo(), politica.getAprobacionesNecesarias(),
                politica.getRolProcesoId(), rol, aprobadores, politica.getVersion(), politica.getModificadoPor(),
                politica.getFechaModificacion());
    }
}
