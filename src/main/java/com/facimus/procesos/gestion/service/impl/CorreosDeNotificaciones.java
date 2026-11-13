package com.facimus.procesos.gestion.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.CorreoSaliente;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.Notificacion;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.NotificacionRepository;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Idiomas;

import tools.jackson.databind.json.JsonMapper;

/**
 * PR 63: manda el correo de unas notificaciones, el de una sola en cuanto paso o el resumen del dia. Solo cuenta lo que
 * sigue sin leer: lo leido en la aplicacion ya se supo. El correo dice quien, que y en que proceso, con un enlace a la
 * web en el idioma de quien lo recibe; nunca lleva el texto de un comentario, que se queda en la aplicacion. Lo que
 * entra al HTML sale escapado. Mandarlo y marcarlo resuelto van en la misma transaccion: si el servidor de correo no
 * lo acepta, la cola lo reintenta, y lo que ya se resolvio no vuelve a salir.
 */
@Component
public class CorreosDeNotificaciones implements ManejadorDeTrabajo {

    /** El tipo de trabajo de la cola. */
    public static final String TRABAJO_DE_CORREO = "notificaciones.correo";

    private static final String NOTIFICACION = "notificacion";
    private static final String RESUMEN = "resumen-de-notificaciones";

    private final JsonMapper json;
    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final ConfiguracionTiendaRepository configuracionRepository;
    private final Redactor redactor;
    private final Correo correo;
    private final Clock reloj;
    private final String web;

    @SuppressWarnings("java:S107")
    public CorreosDeNotificaciones(JsonMapper json, NotificacionRepository notificacionRepository,
            UsuarioRepository usuarioRepository, ProcesoRepository procesoRepository,
            ConfiguracionTiendaRepository configuracionRepository, Redactor redactor, Correo correo, Clock reloj,
            @Value("${app.url-publica}") String web) {
        this.json = json;
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.procesoRepository = procesoRepository;
        this.configuracionRepository = configuracionRepository;
        this.redactor = redactor;
        this.correo = correo;
        this.reloj = reloj;
        this.web = web.endsWith("/") ? web.substring(0, web.length() - 1) : web;
    }

    @Override
    public String tipo() {
        return TRABAJO_DE_CORREO;
    }

    @Override
    @Transactional
    public void ejecutar(Trabajo trabajo) {
        PedidoDeAviso pedido = json.readValue(trabajo.datos(), PedidoDeAviso.class);
        Long empresaId = trabajo.empresaId();
        List<Notificacion> pendientes = notificacionRepository
                .findAllByEmpresaIdAndUsuarioIdAndIdInAndCorreoEnIsNullOrderByCreadaEnAscIdAsc(empresaId,
                        pedido.usuarioId(), pedido.notificaciones());
        if (pendientes.isEmpty()) {
            return;
        }
        List<Notificacion> sinLeer = pendientes.stream().filter(una -> una.getLeidaEn() == null).toList();
        // Quien se desactivo o se anonimizo mientras el correo esperaba ya no lo recibe
        usuarioRepository.findByIdAndEmpresaId(pedido.usuarioId(), empresaId).filter(Usuario::isActivo)
                .filter(usuario -> !sinLeer.isEmpty())
                .ifPresent(usuario -> correo.enviar(redactar(empresaId, usuario, sinLeer, pedido.resumen())));
        notificacionRepository.resolverCorreo(empresaId, pedido.usuarioId(),
                pendientes.stream().map(Notificacion::getId).toList(),
                LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS));
    }

    private CorreoSaliente redactar(Long empresaId, Usuario usuario,
            List<Notificacion> notificaciones, boolean resumen) {
        String idioma = Idiomas.deLaWeb(usuario.getIdioma() != null ? usuario.getIdioma()
                : configuracionRepository.findByEmpresaId(empresaId).map(ConfiguracionTienda::getIdioma)
                        .orElse(Idiomas.POR_DEFECTO));
        Map<Long, String> procesos = procesoRepository
                .findAllByIdInAndEmpresaId(ids(notificaciones, Notificacion::getProcesoId), empresaId).stream()
                .collect(Collectors.toMap(Proceso::getId, Proceso::getNombre));
        Map<Long, String> autores = usuarioRepository
                .findAllByEmpresaIdAndIdIn(empresaId, ids(notificaciones, Notificacion::getAutorId)).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
        List<Map<String, Object>> lineas = new ArrayList<>();
        for (Notificacion notificacion : notificaciones) {
            Map<String, Object> linea = new HashMap<>();
            linea.put("tipo", notificacion.getTipo().name());
            linea.put("autor", autores.getOrDefault(notificacion.getAutorId(), ""));
            linea.put("proceso", procesos.getOrDefault(notificacion.getProcesoId(), ""));
            linea.put("enlace", web + "/" + idioma + "/procesos/" + notificacion.getProcesoId() + "?comentario="
                    + notificacion.getReferenciaId());
            lineas.add(linea);
        }
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", usuario.getNombre());
        datos.put("notificaciones", lineas);
        datos.put("cuenta", web + "/" + idioma + "/cuenta");
        Locale locale = Locale.forLanguageTag(idioma);
        if (resumen) {
            return redactor.redactar(RESUMEN, RESUMEN + ".asunto", List.of(lineas.size()), locale,
                    usuario.getEmail(), datos);
        }
        Map<String, Object> una = lineas.getFirst();
        datos.putAll(una);
        return redactor.redactar(NOTIFICACION, NOTIFICACION + "." + una.get("tipo") + ".asunto",
                List.of(una.get("autor"), una.get("proceso")), locale, usuario.getEmail(), datos);
    }

    private static Set<Long> ids(List<Notificacion> notificaciones, Function<Notificacion, Long> campo) {
        return notificaciones.stream().map(campo).filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
