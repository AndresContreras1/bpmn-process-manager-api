package com.facimus.procesos.modelado.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.ConflictoDeVersionException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.markdown.Markdown;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.modelado.dto.response.CaracterizacionResponse;
import com.facimus.procesos.modelado.dto.response.DocumentacionResponse;
import com.facimus.procesos.modelado.dto.response.VistaPreviaResponse;
import com.facimus.procesos.modelado.model.Caracterizacion;
import com.facimus.procesos.modelado.model.Documentacion;
import com.facimus.procesos.modelado.repository.CaracterizacionRepository;
import com.facimus.procesos.modelado.repository.DocumentacionRepository;
import com.facimus.procesos.modelado.service.DatosDeCaracterizacion;
import com.facimus.procesos.modelado.service.DocumentacionService;

import lombok.RequiredArgsConstructor;

/**
 * PR 62: la documentacion se guarda dos veces, como se escribio y como HTML saneado, para que mostrarla nunca dependa
 * de convertirla bien en el navegador. Cada texto es por proceso o por elemento; el elemento tiene que existir en el
 * proceso cuando se escribe, y su texto se queda aunque despues se borre, para el manual de las versiones que lo
 * tenian.
 */
@Service
@RequiredArgsConstructor
public class DocumentacionServiceImpl implements DocumentacionService {

    private final DocumentacionRepository documentacionRepository;
    private final CaracterizacionRepository caracterizacionRepository;
    private final ProcesoRepository procesoRepository;
    private final ElementosDelProceso elementos;
    private final HistorialCambioService historial;
    private final Markdown markdown;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentacionResponse> listar(Long empresaId, Long procesoId) {
        buscarProceso(empresaId, procesoId);
        return documentacionRepository.findAllByProcesoIdAndEmpresaIdOrderByIdAsc(procesoId, empresaId).stream()
                .map(DocumentacionServiceImpl::respuesta).toList();
    }

    @Override
    @Transactional
    public DocumentacionResponse guardar(Long empresaId, Long usuarioId, Long procesoId, String elemento,
            String texto, Long version) {
        Proceso proceso = buscarProceso(empresaId, procesoId);
        String bpmnId = elemento == null || elemento.isBlank() ? null : elemento.strip();
        if (bpmnId != null && !elementos.existe(empresaId, procesoId, bpmnId)) {
            throw new RecursoNoEncontradoException("El proceso no tiene el elemento " + bpmnId + ".");
        }
        Documentacion actual = (bpmnId == null
                ? documentacionRepository.findByProcesoIdAndBpmnIdIsNullAndEmpresaId(procesoId, empresaId)
                : documentacionRepository.findByProcesoIdAndBpmnIdAndEmpresaId(procesoId, bpmnId, empresaId))
                .orElse(null);
        if (actual == null && version != null) {
            throw new ConflictoDeVersionException(version, null);
        }
        if (actual != null) {
            actual.verificarVersion(version);
        }
        String sobre = bpmnId == null ? "del proceso" : "de " + bpmnId;
        if (texto.isBlank()) {
            if (actual != null) {
                documentacionRepository.delete(actual);
                historial.registrar(empresaId, usuarioId, proceso, "Documentación " + sobre + " borrada.");
            }
            return new DocumentacionResponse(bpmnId, "", "", null, null, null);
        }
        Documentacion guardada = actual == null
                ? Documentacion.builder().empresa(proceso.getEmpresa()).proceso(proceso).bpmnId(bpmnId).build()
                : actual;
        guardada.setMarkdown(texto);
        guardada.setHtml(markdown.aHtml(texto));
        guardada = documentacionRepository.saveAndFlush(guardada);
        historial.registrar(empresaId, usuarioId, proceso, "Documentación " + sobre + " actualizada.");
        return respuesta(guardada);
    }

    @Override
    public VistaPreviaResponse vistaPrevia(String texto) {
        return new VistaPreviaResponse(markdown.aHtml(texto));
    }

    @Override
    @Transactional(readOnly = true)
    public CaracterizacionResponse caracterizacion(Long empresaId, Long procesoId) {
        buscarProceso(empresaId, procesoId);
        return caracterizacionRepository.findByProcesoIdAndEmpresaId(procesoId, empresaId)
                .map(DocumentacionServiceImpl::respuesta)
                .orElseGet(() -> new CaracterizacionResponse(null, null, null, null, null, null, null, null, null,
                        null, null));
    }

    @Override
    @Transactional
    public CaracterizacionResponse guardarCaracterizacion(Long empresaId, Long usuarioId, Long procesoId,
            DatosDeCaracterizacion ficha) {
        Proceso proceso = buscarProceso(empresaId, procesoId);
        Caracterizacion actual = caracterizacionRepository.findByProcesoIdAndEmpresaId(procesoId, empresaId)
                .orElse(null);
        if (actual == null && ficha.version() != null) {
            throw new ConflictoDeVersionException(ficha.version(), null);
        }
        if (actual != null) {
            actual.verificarVersion(ficha.version());
        }
        Caracterizacion guardada = actual == null
                ? Caracterizacion.builder().empresa(proceso.getEmpresa()).proceso(proceso).build()
                : actual;
        guardada.setObjetivo(vacioANulo(ficha.objetivo()));
        guardada.setAlcance(vacioANulo(ficha.alcance()));
        guardada.setEntradas(vacioANulo(ficha.entradas()));
        guardada.setSalidas(vacioANulo(ficha.salidas()));
        guardada.setProveedores(vacioANulo(ficha.proveedores()));
        guardada.setClientes(vacioANulo(ficha.clientes()));
        guardada.setIndicadores(vacioANulo(ficha.indicadores()));
        guardada.setRiesgos(vacioANulo(ficha.riesgos()));
        guardada.setNormativa(vacioANulo(ficha.normativa()));
        guardada = caracterizacionRepository.saveAndFlush(guardada);
        historial.registrar(empresaId, usuarioId, proceso, "Ficha de caracterización actualizada.");
        return respuesta(guardada);
    }

    private Proceso buscarProceso(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static DocumentacionResponse respuesta(Documentacion documentacion) {
        return new DocumentacionResponse(documentacion.getBpmnId(), documentacion.getMarkdown(),
                documentacion.getHtml(), documentacion.getVersion(), documentacion.getModificadoPor(),
                documentacion.getFechaModificacion());
    }

    private static CaracterizacionResponse respuesta(Caracterizacion ficha) {
        return new CaracterizacionResponse(ficha.getObjetivo(), ficha.getAlcance(), ficha.getEntradas(),
                ficha.getSalidas(), ficha.getProveedores(), ficha.getClientes(), ficha.getIndicadores(),
                ficha.getRiesgos(), ficha.getNormativa(), ficha.getVersion(), ficha.getFechaModificacion());
    }
}
