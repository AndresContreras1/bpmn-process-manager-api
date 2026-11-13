package com.facimus.procesos.gestion.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.almacen.EnlacesFirmados;
import com.facimus.procesos.common.correo.Correo;
import com.facimus.procesos.common.correo.Redactor;
import com.facimus.procesos.common.trabajos.ManejadorDeTrabajo;
import com.facimus.procesos.common.trabajos.Trabajo;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.EstadoExportacion;
import com.facimus.procesos.gestion.model.Exportacion;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.ExportacionRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.Idiomas;

/**
 * PR 66: el correo que lleva el enlace de una exportacion lista a quien la pidio, en su idioma. El enlace va a la API
 * por la misma direccion publica de la web, que NGINX le pasa, firmado hasta el vencimiento de la exportacion: es lo
 * unico que lo abre, y no queda en ninguna otra parte. Si mientras esperaba en la cola la exportacion vencio, o quien
 * la pidio ya no esta activo, no sale.
 */
@Component
public class CorreoDeExportacion implements ManejadorDeTrabajo {

    /** El tipo de trabajo de la cola. */
    public static final String TRABAJO_DE_CORREO = "exportaciones.correo";

    private static final String PLANTILLA = "exportacion-lista";
    private static final long KILOBYTE = 1024;

    private final ExportacionRepository exportacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionTiendaRepository configuracionRepository;
    private final EnlacesFirmados enlaces;
    private final Redactor redactor;
    private final Correo correo;
    private final Clock reloj;
    private final String web;

    @SuppressWarnings("java:S107") // cada pieza del correo, por su nombre
    public CorreoDeExportacion(ExportacionRepository exportacionRepository, UsuarioRepository usuarioRepository,
            ConfiguracionTiendaRepository configuracionRepository, EnlacesFirmados enlaces, Redactor redactor,
            Correo correo, Clock reloj, @Value("${app.url-publica}") String web) {
        this.exportacionRepository = exportacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionRepository = configuracionRepository;
        this.enlaces = enlaces;
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
    @Transactional(readOnly = true)
    public void ejecutar(Trabajo trabajo) {
        Long empresaId = trabajo.empresaId();
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Optional<Exportacion> lista = exportacionRepository.findByIdAndEmpresaId(Long.valueOf(trabajo.datos()),
                empresaId).filter(exportacion -> exportacion.getEstado() == EstadoExportacion.LISTA)
                .filter(exportacion -> ahora.isBefore(exportacion.getVenceEn()));
        if (lista.isEmpty() || lista.get().getPedidaPor() == null) {
            return;
        }
        Exportacion exportacion = lista.get();
        usuarioRepository.findByIdAndEmpresaId(exportacion.getPedidaPor(), empresaId).filter(Usuario::isActivo)
                .ifPresent(usuario -> enviar(empresaId, exportacion, usuario, ahora));
    }

    private void enviar(Long empresaId, Exportacion exportacion, Usuario usuario, LocalDateTime ahora) {
        String idioma = Idiomas.deLaWeb(usuario.getIdioma() != null ? usuario.getIdioma()
                : configuracionRepository.findByEmpresaId(empresaId).map(ConfiguracionTienda::getIdioma)
                        .orElse(Idiomas.POR_DEFECTO));
        Map<String, Object> datos = new HashMap<>();
        datos.put("nombre", usuario.getNombre());
        datos.put("organizacion", exportacion.getEmpresa().getNombre());
        datos.put("archivos", exportacion.getArchivos());
        datos.put("tamano", kilobytes(exportacion.getTamano()));
        datos.put("sha256", exportacion.getSha256());
        datos.put("horas", Math.max(1, Duration.between(ahora, exportacion.getVenceEn()).toHours()));
        datos.put("enlace", web + ExportacionServiceImpl.ruta(enlaces, empresaId, exportacion.getId(),
                exportacion.getVenceEn()));
        correo.enviar(redactor.redactar(PLANTILLA, PLANTILLA + ".asunto", List.of(
                exportacion.getEmpresa().getNombre()), Locale.forLanguageTag(idioma), usuario.getEmail(), datos));
    }

    /** El tamano en KB redondeado hacia arriba: igual en cualquier idioma, y nunca cero. */
    static String kilobytes(long bytes) {
        return Math.max(1, (bytes + KILOBYTE - 1) / KILOBYTE) + " KB";
    }
}
