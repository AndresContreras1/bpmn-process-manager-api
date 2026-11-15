package com.facimus.procesos.gestion.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SinPermisoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.condiciones.CondicionMalEscrita;
import com.facimus.procesos.common.condiciones.EvaluadorDeCondiciones;
import com.facimus.procesos.common.model.Accion;
import com.facimus.procesos.common.model.Empresa;
import com.facimus.procesos.gestion.dto.response.ConfiguracionTiendaResponse;
import com.facimus.procesos.gestion.mapper.ConfiguracionTiendaMapper;
import com.facimus.procesos.gestion.model.ConfiguracionTienda;
import com.facimus.procesos.gestion.model.ModoSimulacion;
import com.facimus.procesos.gestion.model.ParametrosSimulacion;
import com.facimus.procesos.gestion.model.PoliticaEstructura;
import com.facimus.procesos.gestion.model.PoliticaSegundoFactor;
import com.facimus.procesos.gestion.model.RecursoDeHistorial;
import com.facimus.procesos.gestion.model.Usuario;
import com.facimus.procesos.gestion.repository.ConfiguracionTiendaRepository;
import com.facimus.procesos.gestion.repository.EmpresaRepository;
import com.facimus.procesos.gestion.repository.UsuarioRepository;
import com.facimus.procesos.gestion.service.ConfiguracionTiendaService;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.LimitesDeSesion;
import com.facimus.procesos.gestion.service.Permisos;
import com.facimus.procesos.gestion.service.Recurso;

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
    private static final String SIN_CONFIGURACION = "Configuración de la tienda no encontrada.";

    private final ConfiguracionTiendaRepository configuracionTiendaRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialCambioService historialCambioService;
    private final ConfiguracionTiendaMapper configuracionTiendaMapper;
    private final Auditoria auditoria;
    private final Permisos permisos;

    @Override
    @Transactional
    public void crearPara(Long empresaId, String idioma, String zonaHoraria) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        configuracionTiendaRepository.save(ConfiguracionTienda.builder()
                .empresa(empresa)
                .politicaEstructura(PoliticaEstructura.ADMINISTRADOR_Y_EDITOR)
                .idioma(idioma)
                .zonaHoraria(zonaHoraria)
                .build());
    }

    @Override
    public ConfiguracionTiendaResponse obtener(Long empresaId) {
        return configuracionTiendaMapper.toResponse(buscar(empresaId));
    }

    @Override
    @Transactional
    @SuppressWarnings("java:S107") // cada parte de la configuracion, por su nombre
    public ConfiguracionTiendaResponse editar(Long empresaId, Long usuarioId, PoliticaEstructura politica,
            ModoSimulacion modo, ParametrosSimulacion parametros, LimitesDeSesion sesiones,
            PoliticaSegundoFactor segundoFactor, Boolean soloEntradaExterna, Boolean administradorCompletaPorOtro,
            Long version) {
        ConfiguracionTienda configuracion = buscar(empresaId);
        configuracion.verificarVersion(version);
        configuracion.setPoliticaEstructura(politica);
        if (modo != null) {
            configuracion.setModoSimulacion(modo);
        }
        if (parametros != null) {
            exigirReglaQueCompila(parametros.getReglaRechazoPagos());
            configuracion.setSimulacion(parametros);
        }
        boolean cambianLasSesiones = acotarSesiones(configuracion, sesiones);
        boolean cambiaElSegundoFactor = cambiarSegundoFactor(configuracion, segundoFactor);
        boolean cambiaLaEntrada = cambiarEntrada(configuracion, soloEntradaExterna);
        boolean cambiaCompletarPorOtro = cambiarCompletarPorOtro(configuracion, administradorCompletaPorOtro);
        configuracion = configuracionTiendaRepository.saveAndFlush(configuracion);

        historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                politica == PoliticaEstructura.SOLO_ADMINISTRADOR
                        ? "La estructura de los diagramas queda reservada a los administradores."
                        : "Los editores vuelven a poder cambiar la estructura de los diagramas.");
        if (cambianLasSesiones) {
            historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                    "Las sesiones se cierran tras " + configuracion.getInactividadSesionMinutos()
                            + " minutos sin renovarse y " + horas(configuracion.getDuracionSesionHoras())
                            + " después del login.");
        }
        if (cambiaElSegundoFactor) {
            historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                    switch (segundoFactor) {
                        case OPCIONAL -> "El segundo factor queda a elección de cada persona.";
                        case ADMINISTRADORES -> "Los administradores tienen que usar un segundo factor.";
                        case TODOS -> "Todos tienen que usar un segundo factor.";
                    });
        }
        auditoria.registrar(empresaId, usuarioId, "CONFIGURACION_CAMBIADA", "EMPRESA", empresaId, Map.of(
                "politicaEstructura", configuracion.getPoliticaEstructura().name(),
                "politicaSegundoFactor", configuracion.getPoliticaSegundoFactor().name(),
                "soloEntradaExterna", configuracion.isSoloEntradaExterna(),
                "inactividadSesionMinutos", configuracion.getInactividadSesionMinutos(),
                "duracionSesionHoras", configuracion.getDuracionSesionHoras(),
                "administradorCompletaPorOtro", configuracion.isAdministradorCompletaPorOtro()));
        if (cambiaCompletarPorOtro) {
            historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                    Boolean.TRUE.equals(administradorCompletaPorOtro)
                            ? "Un administrador puede completar la tarea que tiene otra persona."
                            : "Solo quien tiene una tarea la completa.");
        }
        if (cambiaLaEntrada) {
            historialCambioService.registrarDeTienda(empresaId, usuarioId, RecursoDeHistorial.EMPRESA, empresaId,
                    Boolean.TRUE.equals(soloEntradaExterna)
                            ? "Se entra solo con Google o Microsoft; los administradores conservan su contraseña."
                            : "Se vuelve a poder entrar con contraseña.");
        }
        return configuracionTiendaMapper.toResponse(configuracion);
    }

    private static String horas(int horas) {
        return horas == 1 ? "una hora" : horas + " horas";
    }

    /** Cambia la politica del segundo factor si llega una distinta; dice si cambio. */
    private static boolean cambiarSegundoFactor(ConfiguracionTienda configuracion, PoliticaSegundoFactor nueva) {
        if (nueva == null || nueva == configuracion.getPoliticaSegundoFactor()) {
            return false;
        }
        configuracion.setPoliticaSegundoFactor(nueva);
        return true;
    }

    /** Cambia si se entra solo por fuera, si llega un valor distinto; dice si cambio. */
    private static boolean cambiarEntrada(ConfiguracionTienda configuracion, Boolean soloEntradaExterna) {
        if (soloEntradaExterna == null || soloEntradaExterna == configuracion.isSoloEntradaExterna()) {
            return false;
        }
        configuracion.setSoloEntradaExterna(soloEntradaExterna);
        return true;
    }

    /** Cambia si un administrador completa la tarea de otra persona, si llega un valor distinto; dice si cambio. */
    private static boolean cambiarCompletarPorOtro(ConfiguracionTienda configuracion, Boolean completaPorOtro) {
        if (completaPorOtro == null || completaPorOtro == configuracion.isAdministradorCompletaPorOtro()) {
            return false;
        }
        configuracion.setAdministradorCompletaPorOtro(completaPorOtro);
        return true;
    }

    /** Pone los limites que llegaron y dice si alguno cambio. Las sesiones abiertas toman la duracion nueva. */
    private static boolean acotarSesiones(ConfiguracionTienda configuracion, LimitesDeSesion sesiones) {
        int inactividad = configuracion.getInactividadSesionMinutos();
        int duracion = configuracion.getDuracionSesionHoras();
        if (sesiones.inactividadMinutos() != null) {
            configuracion.setInactividadSesionMinutos(sesiones.inactividadMinutos());
        }
        if (sesiones.duracionHoras() != null) {
            configuracion.setDuracionSesionHoras(sesiones.duracionHoras());
        }
        return inactividad != configuracion.getInactividadSesionMinutos()
                || duracion != configuracion.getDuracionSesionHoras();
    }

    /**
     * R-52: la regla con la que la pasarela rechaza un pago se escribe en la gramatica del proyecto, la misma de
     * las condiciones de un gateway. Se comprueba al guardarla y no al simular: una regla mal escrita descubierta
     * a mitad de una demo no se puede arreglar sin parar la demo.
     */
    private static void exigirReglaQueCompila(String regla) {
        if (regla == null || regla.isBlank()) {
            return;
        }
        try {
            EvaluadorDeCondiciones.compilar(regla);
        } catch (CondicionMalEscrita malEscrita) {
            throw new ReglaNegocioException("La regla de rechazo de pagos no está bien escrita: "
                    + malEscrita.getMessage());
        }
    }

    @Override
    public boolean administradorCompletaPorOtro(Long empresaId) {
        return buscar(empresaId).isAdministradorCompletaPorOtro();
    }

    @Override
    public int reloj(Long empresaId) {
        return configuracionTiendaRepository.relojDe(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(SIN_CONFIGURACION));
    }

    /**
     * Mover el reloj es una edicion de la fila de la tienda como cualquier otra, asi que sube su version: quien
     * estuviera editando la configuracion con la version de antes recibe un 409 y la vuelve a leer, que es
     * exactamente lo que el bloqueo optimista existe para contar.
     */
    @Override
    @Transactional
    public int avanzarReloj(Long empresaId, int ticks) {
        ConfiguracionTienda configuracion = buscar(empresaId);
        configuracion.setReloj(configuracion.getReloj() + ticks);
        return configuracionTiendaRepository.saveAndFlush(configuracion).getReloj();
    }

    @Override
    public List<Long> tiendasEnAutomatico() {
        return configuracionTiendaRepository.empresasEnAutomatico();
    }

    @Override
    public void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId) {
        Usuario usuario = usuarioRepository.findByIdAndEmpresaId(usuarioId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (!buscar(empresaId).getPoliticaEstructura().permiteA(usuario.getRolAcceso())) {
            throw new SinPermisoException(SOLO_ADMINISTRADORES);
        }
    }

    @Override
    public void exigirPuedeEditarEstructura(Long empresaId, Long usuarioId, Long procesoId) {
        if (!permisos.concedido(empresaId, usuarioId, Accion.MODELAR, Recurso.proceso(procesoId))) {
            exigirPuedeEditarEstructura(empresaId, usuarioId);
        }
    }

    /** Toda tienda tiene su fila desde que se registra, y la migracion se la dio a las que ya existian. */
    private ConfiguracionTienda buscar(Long empresaId) {
        return configuracionTiendaRepository.findByEmpresaId(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(SIN_CONFIGURACION));
    }
}
