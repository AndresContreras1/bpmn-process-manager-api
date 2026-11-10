package com.facimus.procesos.gestion.service.impl;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.gestion.dto.response.PreferenciasResponse;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.Idiomas;
import com.facimus.procesos.gestion.service.PreferenciasService;

@Service
@Transactional(readOnly = true)
public class PreferenciasServiceImpl implements PreferenciasService {

    private final ConfiguracionTiendaRepository configuracionRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioService historialCambioService;
    private final Auditoria auditoria;

    public PreferenciasServiceImpl(ConfiguracionTiendaRepository configuracionRepository,
            UsuarioRepository usuarioRepository, HistorialCambioService historialCambioService, Auditoria auditoria) {
        this.configuracionRepository = configuracionRepository;
        this.usuarioRepository = usuarioRepository;
        this.historialCambioService = historialCambioService;
        this.auditoria = auditoria;
    }

    @Override
    public PreferenciasResponse obtener(Long empresaId, Long usuarioId) {
        return respuesta(configuracion(empresaId), usuario(empresaId, usuarioId));
    }

    @Override
    @Transactional
    public PreferenciasResponse cambiarLasPropias(Long empresaId, Long usuarioId, String idioma, String zonaHoraria) {
        Usuario usuario = usuario(empresaId, usuarioId);
        usuario.setIdioma(vacioANulo(idioma) == null ? null : Idiomas.validar(idioma));
        usuario.setZonaHoraria(vacioANulo(zonaHoraria) == null ? null : Idiomas.validarZona(zonaHoraria));
        return respuesta(configuracion(empresaId), usuario);
    }

    @Override
    @Transactional
    public PreferenciasResponse cambiarLasDeLaOrganizacion(Long empresaId, Long autorId, String idioma,
            String zonaHoraria) {
        if (vacioANulo(idioma) == null || vacioANulo(zonaHoraria) == null) {
            throw new SolicitudInvalidaException("La organización necesita su idioma y su zona horaria.");
        }
        ConfiguracionTienda configuracion = configuracion(empresaId);
        configuracion.setIdioma(Idiomas.validar(idioma));
        configuracion.setZonaHoraria(Idiomas.validarZona(zonaHoraria));
        historialCambioService.registrarDeTienda(empresaId, autorId, RecursoDeHistorial.EMPRESA, empresaId,
                "Idioma " + configuracion.getIdioma() + " y zona horaria " + configuracion.getZonaHoraria()
                        + " de la organización.");
        auditoria.registrar(empresaId, autorId, "PREFERENCIAS_CAMBIADAS", "EMPRESA", empresaId,
                Map.of("idioma", configuracion.getIdioma(), "zonaHoraria", configuracion.getZonaHoraria()));
        return respuesta(configuracion, usuario(empresaId, autorId));
    }

    private static PreferenciasResponse respuesta(ConfiguracionTienda configuracion, Usuario usuario) {
        return new PreferenciasResponse(
                usuario.getIdioma() == null ? configuracion.getIdioma() : usuario.getIdioma(),
                usuario.getZonaHoraria() == null ? configuracion.getZonaHoraria() : usuario.getZonaHoraria(),
                usuario.getIdioma(), usuario.getZonaHoraria(), configuracion.getIdioma(),
                configuracion.getZonaHoraria());
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    private ConfiguracionTienda configuracion(Long empresaId) {
        return configuracionRepository.findByEmpresaId(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Configuración no encontrada."));
    }

    private Usuario usuario(Long empresaId, Long usuarioId) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }
}
