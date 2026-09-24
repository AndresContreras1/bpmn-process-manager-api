package com.facimus.procesos.modelado.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.MensajeResponse;
import com.facimus.procesos.modelado.mapper.MensajeMapper;
import com.facimus.procesos.modelado.model.AccionSiFalla;
import com.facimus.procesos.modelado.model.CampoDeMensaje;
import com.facimus.procesos.modelado.model.Mensaje;
import com.facimus.procesos.modelado.model.NodoFlujo;
import com.facimus.procesos.modelado.model.Pool;
import com.facimus.procesos.modelado.repository.ActividadRepository;
import com.facimus.procesos.modelado.repository.CorrelacionRepository;
import com.facimus.procesos.modelado.repository.MensajeRepository;
import com.facimus.procesos.modelado.repository.NodoFlujoRepository;
import com.facimus.procesos.modelado.repository.PoolRepository;
import com.facimus.procesos.modelado.service.DatosDeMensaje;
import com.facimus.procesos.modelado.service.MensajeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MensajeServiceImpl implements MensajeService {

    /** Un nombre de variable que un evaluador de condiciones pueda leer sin comillas. */
    private static final Pattern VARIABLE = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*");
    private static final int LARGO_DE_LA_VARIABLE = 60;

    private final HistorialCambioService historialCambioService;
    private final MensajeRepository mensajeRepository;
    private final PoolRepository poolRepository;
    private final ProcesoRepository procesoRepository;
    private final NodoFlujoRepository nodoFlujoRepository;
    private final ActividadRepository actividadRepository;
    private final CorrelacionRepository correlacionRepository;
    private final MensajeMapper mensajeMapper;

    @Override
    @Transactional
    public MensajeResponse crear(Long empresaId, Long usuarioId, Long procesoId, DatosDeMensaje datos) {
        if (datos.poolOrigenId().equals(datos.poolDestinoId())) {
            throw new ReglaNegocioException("Un mensaje debe conectar dos pools diferentes.");
        }
        Proceso proceso = procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
        Pool poolOrigen = buscarPool(datos.poolOrigenId(), empresaId, "Pool de origen no encontrado.");
        Pool poolDestino = buscarPool(datos.poolDestinoId(), empresaId, "Pool de destino no encontrado.");
        if (!participaEn(poolOrigen, proceso) || !participaEn(poolDestino, proceso)) {
            throw new ReglaNegocioException("Los dos pools de un mensaje tienen que ser participantes de su proceso.");
        }

        Mensaje mensaje = Mensaje.builder()
                .empresa(proceso.getEmpresa())
                .proceso(proceso)
                .poolOrigen(poolOrigen)
                .poolDestino(poolDestino)
                .build();
        aplicar(mensaje, datos, empresaId);
        Mensaje guardado = mensajeRepository.save(mensaje);
        historialCambioService.registrar(empresaId, usuarioId, proceso,
                "Mensaje \"" + datos.nombre() + "\" agregado.");
        return mensajeMapper.toResponse(guardado);
    }

    @Override
    @Transactional
    public MensajeResponse editar(Long empresaId, Long usuarioId, Long mensajeId, DatosDeMensaje datos,
            Long version) {
        Mensaje mensaje = buscar(empresaId, mensajeId);
        mensaje.verificarVersion(version);
        exigirLosMismosPools(mensaje, datos);
        aplicar(mensaje, datos, empresaId);
        historialCambioService.registrar(empresaId, usuarioId, mensaje.getProceso(),
                "Mensaje \"" + datos.nombre() + "\" editado.");
        return mensajeMapper.toResponse(mensajeRepository.saveAndFlush(mensaje));
    }

    @Override
    @Transactional
    public void eliminar(Long empresaId, Long usuarioId, Long mensajeId) {
        Mensaje mensaje = buscar(empresaId, mensajeId);
        correlacionRepository.findByMensajeIdAndEmpresaId(mensajeId,
                empresaId).ifPresent(correlacionRepository::delete);
        mensajeRepository.delete(mensaje);
        historialCambioService.registrar(empresaId, usuarioId, mensaje.getProceso(),
                "Mensaje \"" + mensaje.getNombre() + "\" eliminado.");
    }

    @Override
    public List<MensajeResponse> listarPorProceso(Long empresaId, Long procesoId) {
        if (!procesoRepository.existsByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)) {
            throw new RecursoNoEncontradoException("Proceso no encontrado.");
        }
        return mensajeMapper.toResponses(mensajeRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId,
                empresaId));
    }

    @Override
    public MensajeResponse obtener(Long empresaId, Long mensajeId) {
        return mensajeMapper.toResponse(buscar(empresaId, mensajeId));
    }

    /** Todas las reglas del mensaje en un solo sitio: valen igual al crearlo y al editarlo. */
    private void aplicar(Mensaje mensaje, DatosDeMensaje datos, Long empresaId) {
        Pool poolOrigen = mensaje.getPoolOrigen();
        Pool poolDestino = mensaje.getPoolDestino();
        AccionSiFalla siFalla = datos.siFalla() == null ? AccionSiFalla.CONTINUAR : datos.siFalla();

        mensaje.setNombre(datos.nombre());
        mensaje.setContenido(datos.contenido());
        mensaje.setNodoOrigen(anclaje(datos.nodoOrigenId(), poolOrigen, empresaId, true));
        mensaje.setNodoDestino(anclaje(datos.nodoDestinoId(), poolDestino, empresaId, false));
        mensaje.setTipoDestino(datos.tipoDestino());
        mensaje.setSiFalla(siFalla);
        mensaje.setNodoManejoError(manejoDelError(datos.nodoManejoErrorId(), siFalla, poolOrigen, empresaId));
        mensaje.setOrigenExterno(datos.origenExterno());
        mensaje.setCampos(camposValidados(datos.campos()));
        mensaje.setUsoDeLosDatos(datos.usoDeLosDatos());
        mensaje.setVariable(variable(datos));
        mensaje.setRespuestaEsperada(respuestaEsperada(datos.respuestaEsperadaId(), mensaje, empresaId));
    }

    /**
     * R-37 y R-38: el nodo anclado esta en el pool de su lado y sabe hacer lo que el mensaje le pide, mandar o
     * esperar. Un pool de caja negra no ancla nada: por dentro no se modela.
     */
    private NodoFlujo anclaje(Long nodoId, Pool pool, Long empresaId, boolean deSalida) {
        if (nodoId == null) {
            return null;
        }
        String lado = deSalida ? "origen" : "destino";
        if (pool.isCajaNegra()) {
            throw new ReglaNegocioException("El pool de " + lado
                    + " es una caja negra: no se modela por dentro, asi que el mensaje no se ancla a un nodo suyo.");
        }
        NodoFlujo nodo = nodoFlujoRepository.findByIdAndEmpresaId(nodoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Nodo de " + lado + " del mensaje no encontrado."));
        if (!nodo.getLane().getPool().getId().equals(pool.getId())) {
            throw new ReglaNegocioException("El nodo de " + lado + " del mensaje debe estar en el pool de "
                    + lado + ".");
        }
        if (deSalida && !nodo.puedeEnviarMensajes()) {
            throw new ReglaNegocioException("El nodo de origen del mensaje debe poder enviarlo.");
        }
        if (!deSalida && !nodo.puedeRecibirMensajes()) {
            throw new ReglaNegocioException("El nodo de destino del mensaje debe poder recibirlo.");
        }
        return nodo;
    }

    /** R-39: desviar el flujo exige decir a que actividad del pool que envia. */
    private NodoFlujo manejoDelError(Long nodoId, AccionSiFalla siFalla, Pool poolOrigen, Long empresaId) {
        if (!siFalla.necesitaActividad()) {
            if (nodoId != null) {
                throw new ReglaNegocioException(
                        "Solo un mensaje que maneja el error indica la actividad que lo atiende.");
            }
            return null;
        }
        if (nodoId == null) {
            throw new ReglaNegocioException("Indique la actividad que maneja el error.");
        }
        NodoFlujo actividad = actividadRepository.findByIdAndEmpresaId(nodoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Actividad de manejo de error no encontrada."));
        if (!actividad.getLane().getPool().getId().equals(poolOrigen.getId())) {
            throw new ReglaNegocioException(
                    "La actividad que maneja el error debe estar en el pool que envia el mensaje.");
        }
        return actividad;
    }

    /** R-40: la respuesta es otro mensaje del proceso que vuelve desde el pool de destino. */
    private Mensaje respuestaEsperada(Long respuestaId, Mensaje mensaje, Long empresaId) {
        if (respuestaId == null) {
            return null;
        }
        if (respuestaId.equals(mensaje.getId())) {
            throw new ReglaNegocioException("Un mensaje no puede ser su propia respuesta.");
        }
        Mensaje respuesta = mensajeRepository.findByIdAndEmpresaId(respuestaId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje de respuesta no encontrado."));
        boolean mismoProceso = respuesta.getProceso().getId().equals(mensaje.getProceso().getId());
        boolean vuelve = respuesta.getPoolOrigen().getId().equals(mensaje.getPoolDestino().getId())
                && respuesta.getPoolDestino().getId().equals(mensaje.getPoolOrigen().getId());
        if (!mismoProceso || !vuelve) {
            throw new ReglaNegocioException(
                    "La respuesta esperada debe ser un mensaje que vuelve desde el pool destino.");
        }
        return respuesta;
    }

    /** Ningun campo se llama como otro: en ejecucion el cuerpo es un mapa y el ultimo pisaria al primero. */
    private static List<CampoDeMensaje> camposValidados(List<CampoDeMensaje> campos) {
        if (campos == null || campos.isEmpty()) {
            return List.of();
        }
        Set<String> vistos = new HashSet<>();
        for (CampoDeMensaje campo : campos) {
            if (!vistos.add(campo.nombre().toLowerCase(Locale.ROOT))) {
                throw new ReglaNegocioException("El mensaje repite el campo \"" + campo.nombre() + "\".");
            }
        }
        return List.copyOf(campos);
    }

    /**
     * El nombre con el que el cuerpo entra a las variables del caso. Si no lo mandan, sale del nombre del mensaje:
     * "Payment authorization result" queda como paymentAuthorizationResult.
     */
    private static String variable(DatosDeMensaje datos) {
        if (!StringUtils.hasText(datos.variable())) {
            return porDefecto(datos.nombre());
        }
        String variable = datos.variable().trim();
        if (!VARIABLE.matcher(variable).matches()) {
            throw new ReglaNegocioException(
                    "El nombre de la variable empieza por letra y solo admite letras, numeros y guion bajo.");
        }
        return variable;
    }

    private static String porDefecto(String nombre) {
        StringBuilder variable = new StringBuilder();
        boolean siguienteEnMayuscula = false;
        for (char letra : nombre.toCharArray()) {
            if (!Character.isLetterOrDigit(letra)) {
                siguienteEnMayuscula = !variable.isEmpty();
            } else if (variable.isEmpty()) {
                variable.append(Character.isLetter(letra) ? Character.toLowerCase(letra) : 'm');
            } else {
                variable.append(siguienteEnMayuscula ? Character.toUpperCase(letra) : letra);
                siguienteEnMayuscula = false;
            }
        }
        String nombreDeVariable = variable.length() > LARGO_DE_LA_VARIABLE
                ? variable.substring(0, LARGO_DE_LA_VARIABLE)
                : variable.toString();
        return nombreDeVariable.isEmpty() ? "mensaje" : nombreDeVariable;
    }

    private void exigirLosMismosPools(Mensaje mensaje, DatosDeMensaje datos) {
        boolean cambiaOrigen = datos.poolOrigenId() != null
                && !Objects.equals(datos.poolOrigenId(), mensaje.getPoolOrigen().getId());
        boolean cambiaDestino = datos.poolDestinoId() != null
                && !Objects.equals(datos.poolDestinoId(), mensaje.getPoolDestino().getId());
        if (cambiaOrigen || cambiaDestino) {
            throw new ReglaNegocioException("Los pools de un mensaje no se cambian: trace otro mensaje.");
        }
    }

    private Pool buscarPool(Long poolId, Long empresaId, String siNoEsta) {
        return poolRepository.findByIdAndEmpresaId(poolId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(siNoEsta));
    }

    /** Un pool de otro proceso de la misma tienda existe, pero no es participante de este. */
    private static boolean participaEn(Pool pool, Proceso proceso) {
        return pool.getProceso().getId().equals(proceso.getId());
    }

    private Mensaje buscar(Long empresaId, Long mensajeId) {
        return mensajeRepository.findByIdAndEmpresaId(mensajeId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mensaje no encontrado."));
    }
}
