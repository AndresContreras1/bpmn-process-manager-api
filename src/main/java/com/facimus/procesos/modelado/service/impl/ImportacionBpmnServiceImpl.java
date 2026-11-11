package com.facimus.procesos.modelado.service.impl;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.facimus.procesos.common.Huella;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.auditoria.Auditoria;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.HistorialCambioService;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.modelado.bpmn.ConversionBpmn;
import com.facimus.procesos.modelado.bpmn.EntradaBpmn;
import com.facimus.procesos.modelado.bpmn.EsquemaBpmn;
import com.facimus.procesos.modelado.bpmn.InformeBpmn;
import com.facimus.procesos.modelado.bpmn.LectorBpmn;
import com.facimus.procesos.modelado.bpmn.PlanBpmn;
import com.facimus.procesos.modelado.dto.response.HallazgoImportacionResponse;
import com.facimus.procesos.modelado.dto.response.ImportacionResponse;
import com.facimus.procesos.modelado.dto.response.InformeImportacionResponse;
import com.facimus.procesos.modelado.model.Importacion;
import com.facimus.procesos.modelado.repository.ImportacionRepository;
import com.facimus.procesos.modelado.service.ImportacionBpmnService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * PR 54: la tuberia de una importacion, en una transaccion. El archivo se lee con {@link EntradaBpmn} (SecureXml y
 * los limites de BPMN); lo que incumple del esquema de la OMG se avisa y no se rechaza, porque muchas herramientas escriben BPMN que
 * no valida; {@link ConversionBpmn} decide que es cada cosa y comprueba las reglas; {@link AplicacionDelModelo} lo
 * aplica al borrador. Queda una linea en el historial (que recalcula la geometria una sola vez), una en la bitacora
 * y la importacion con su informe.
 */
@Service
@RequiredArgsConstructor
public class ImportacionBpmnServiceImpl implements ImportacionBpmnService {

    /** Los avisos del esquema que se devuelven: los demas suelen ser el mismo error repetido. */
    private static final int AVISOS = 20;
    private static final String DESCRIPCION = "Importado de un archivo BPMN.";
    private static final String CATEGORIA = "General";

    private final ProcesoService procesoService;
    private final ProcesoRepository procesoRepository;
    private final AplicacionDelModelo aplicacion;
    private final ImportacionRepository importacionRepository;
    private final HistorialCambioService historialCambioService;
    private final Auditoria auditoria;
    private final JsonMapper json;

    @Override
    @Transactional
    public ImportacionResponse importar(Long empresaId, Long usuarioId, RolAcceso rol, InputStream archivo,
            String nombre) {
        EntradaBpmn.Leida leida = EntradaBpmn.leer(archivo, LimitesXml.BPMN);
        PlanBpmn plan = convertir(leida);
        String elNombre = nombre != null && !nombre.isBlank() ? nombre.strip()
                : plan.nombre() != null ? plan.nombre() : "Proceso importado";
        Long procesoId = procesoService.crear(empresaId, usuarioId, elNombre,
                plan.descripcion() != null ? plan.descripcion() : DESCRIPCION,
                plan.categoria() != null ? plan.categoria() : CATEGORIA).id();
        return aplicar(empresaId, usuarioId, rol, buscar(empresaId, procesoId), plan, leida.recibidos());
    }

    @Override
    @Transactional
    public ImportacionResponse reemplazar(Long empresaId, Long usuarioId, RolAcceso rol, Long procesoId,
            InputStream archivo) {
        Proceso proceso = buscar(empresaId, procesoId);
        EntradaBpmn.Leida leida = EntradaBpmn.leer(archivo, LimitesXml.BPMN);
        return aplicar(empresaId, usuarioId, rol, proceso, convertir(leida), leida.recibidos());
    }

    private static PlanBpmn convertir(EntradaBpmn.Leida leida) {
        List<String> avisos = EsquemaBpmn.problemas(leida.bytes());
        return ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()),
                avisos.subList(0, Math.min(avisos.size(), AVISOS)), leida.codificacion());
    }

    private ImportacionResponse aplicar(Long empresaId, Long usuarioId, RolAcceso rol, Proceso proceso,
            PlanBpmn plan, byte[] bytes) {
        AplicacionDelModelo.Cambios cambios = aplicacion.aplicar(empresaId, usuarioId, rol, proceso, plan);
        if (cambios.total() > 0) {
            historialCambioService.registrar(empresaId, usuarioId, proceso, "Modelo importado de un archivo BPMN: "
                    + cambios.creados() + " creados, " + cambios.editados() + " editados y " + cambios.dadosDeBaja()
                    + " dados de baja.");
        }
        ImportacionResponse respuesta = new ImportacionResponse(proceso.getId(), proceso.getNombre(),
                cambios.creados(), cambios.editados(), cambios.dadosDeBaja(), informe(plan.informe()));
        importacionRepository.save(Importacion.builder()
                .empresa(proceso.getEmpresa())
                .procesoId(proceso.getId())
                .usuarioId(usuarioId)
                .fecha(LocalDateTime.now())
                .bytes(bytes.length)
                .huellaArchivo(Huella.de(bytes))
                .creados(cambios.creados())
                .editados(cambios.editados())
                .dadosDeBaja(cambios.dadosDeBaja())
                .informe(json.writeValueAsString(respuesta.informe()))
                .build());
        auditoria.registrar(empresaId, usuarioId, "PROCESO_IMPORTADO", "PROCESO", proceso.getId(),
                Map.of("creados", cambios.creados(), "editados", cambios.editados(),
                        "dadosDeBaja", cambios.dadosDeBaja(), "bytes", bytes.length));
        return respuesta;
    }

    private Proceso buscar(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }

    private static InformeImportacionResponse informe(InformeBpmn informe) {
        return new InformeImportacionResponse(informe.soportados(),
                informe.convertidos().stream().map(hallazgo -> new HallazgoImportacionResponse(hallazgo.elemento(),
                        hallazgo.detalle())).toList(),
                informe.noSoportados().stream().map(hallazgo -> new HallazgoImportacionResponse(hallazgo.elemento(),
                        hallazgo.detalle())).toList(),
                informe.extensionesConservadas(), informe.avisosDelEsquema());
    }
}
