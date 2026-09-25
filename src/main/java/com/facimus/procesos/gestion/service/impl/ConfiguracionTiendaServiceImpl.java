package com.facimus.procesos.gestion.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.mapper.ConfiguracionTiendaMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;

import lombok.RequiredArgsConstructor;

/**
 * D16: la tienda decide quien dibuja su estructura. La decision no puede estar en SecurityConfig, que es igual para
 * todas: es un dato de cada tienda, asi que se comprueba aqui y sale como un 403 con su motivo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfiguracionTiendaServiceImpl implements ConfiguracionTiendaService {

    static final String SOLO_ADMINISTRADORES = "La tienda reserva la estructura de los diagramas a los "
            + "administradores.";

    private final ConfiguracionTiendaRepository configuracionTiendaRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioService historialCambioService;
    private final ConfiguracionTiendaMapper configuracionTiendaMapper;

    @Override
    @Transactional
    public void crearPara(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        configuracionTiendaRepository.save(ConfiguracionTienda.builder()
                .empresa(empresa)
                .politicaEstructura(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR)
                .build());
    }

    @Override
    public ConfiguracionTiendaResponse obtener(Long empresaId) {
        return configuracionTiendaMapper.toResponse(buscar(empresaId));
    }

    @Override
    @Transactional
    public ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            Long version) {
        ConfiguracionTienda configuracion = buscar(empresaId);
        configuracion.verificarVersion(version);
        configuracion.setPoliticaEstructura(politica);
        configuracion = configuracionTiendaRepository.saveAndFlush(configuracion);

        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                politica == PoliticaEstructura.SOLO_ADMINISTRADOR
                        ? "La estructura de los diagramas queda reservada a los administradores."
                        : "Los editores vuelven a poder cambiar la estructura de los diagramas.");
        return configuracionTiendaMapper.toResponse(configuracion);
    }

    @Override
    public void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (!buscar(empresaId).getPoliticaEstructura().permiteA(usuario.getRolAcceso())) {
            throw new SinPermisoException(SOLO_ADMINISTRADORES);
        }
    }

    /** Toda tienda tiene su fila desde que se registra, y la migracion se la dio a las que ya existian. */
    private ConfiguracionTienda buscar(Long empresaId) {
        return configuracionTiendaRepository.findByEmpresaId(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Configuración de la tienda no encontrada."));
    }
}
