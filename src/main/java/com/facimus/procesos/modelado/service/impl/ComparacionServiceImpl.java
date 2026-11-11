package com.facimus.procesos.modelado.service.impl;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.ReglaNegocioException;
import com.facimus.procesos.common.SolicitudInvalidaException;
import com.facimus.procesos.common.model.RolAcceso;
import com.facimus.procesos.common.xml.LimitesXml;
import com.facimus.procesos.gestion.model.Proceso;
import com.facimus.procesos.gestion.repository.ProcesoRepository;
import com.facimus.procesos.gestion.service.ProcesoService;
import com.facimus.procesos.gestion.service.VersionService;
import com.facimus.procesos.modelado.bpmn.ConversionBpmn;
import com.facimus.procesos.modelado.bpmn.EntradaBpmn;
import com.facimus.procesos.modelado.bpmn.LectorBpmn;
import com.facimus.procesos.modelado.dto.response.ComparacionResponse;
import com.facimus.procesos.modelado.dto.response.DiagramaResponse;
import com.facimus.procesos.modelado.service.ComparacionService;
import com.facimus.procesos.modelado.service.DiagramaService;

import tools.jackson.databind.json.JsonMapper;

/** PR 57: se compara sobre el JSON de las instantaneas, el mismo de la huella, y no sobre el XML. */
@Service
public class ComparacionServiceImpl implements ComparacionService {

    private static final String BORRADOR = "borrador";
    private static final String VIGENTE = "vigente";

    private final DiagramaService diagramaService;
    private final VersionService versionService;
    private final ProcesoRepository procesoRepository;
    private final ProcesoService procesoService;
    private final AplicacionDelModelo aplicacion;
    private final ArmadoDelDiagrama armado;
    private final JsonMapper json;
    private final TransactionTemplate simulacro;

    public ComparacionServiceImpl(DiagramaService diagramaService, VersionService versionService,
            ProcesoRepository procesoRepository, ProcesoService procesoService, AplicacionDelModelo aplicacion,
            ArmadoDelDiagrama armado, JsonMapper json, PlatformTransactionManager transacciones) {
        this.diagramaService = diagramaService;
        this.versionService = versionService;
        this.procesoRepository = procesoRepository;
        this.procesoService = procesoService;
        this.aplicacion = aplicacion;
        this.armado = armado;
        this.json = json;
        this.simulacro = new TransactionTemplate(transacciones);
        this.simulacro.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparacionResponse comparar(Long empresaId, Long procesoId, String de, String a) {
        Proceso proceso = buscar(empresaId, procesoId);
        return ComparacionDeDiagramas.comparar(de, lado(empresaId, proceso, de), a, lado(empresaId, proceso, a), json);
    }

    /** El archivo se aplica de verdad dentro de una transaccion que se deshace: nadie duplica las reglas. */
    @Override
    public ComparacionResponse compararArchivo(Long empresaId, Long usuarioId, Long procesoId, InputStream archivo) {
        EntradaBpmn.Leida leida = EntradaBpmn.leer(archivo, LimitesXml.BPMN);
        return simulacro.execute(estado -> {
            estado.setRollbackOnly();
            Proceso proceso = buscar(empresaId, procesoId);
            DiagramaResponse antes = armado.armar(procesoService.obtener(empresaId, procesoId, false), false, empresaId);
            aplicacion.aplicar(empresaId, usuarioId, RolAcceso.ADMINISTRADOR, proceso,
                    ConversionBpmn.convertir(LectorBpmn.leer(leida.bytes()), List.of(), leida.codificacion()));
            DiagramaResponse despues = armado.armar(procesoService.obtener(empresaId, procesoId, false), false,
                    empresaId);
            return ComparacionDeDiagramas.comparar(BORRADOR, antes, "archivo", despues, json);
        });
    }

    private DiagramaResponse lado(Long empresaId, Proceso proceso, String lado) {
        if (BORRADOR.equals(lado)) {
            return diagramaService.obtener(empresaId, proceso.getId());
        }
        return json.readValue(versionService.definicion(empresaId, proceso.getId(), numero(proceso, lado)),
                DiagramaResponse.class);
    }

    private static int numero(Proceso proceso, String lado) {
        if (VIGENTE.equals(lado)) {
            if (proceso.getVersionPublicada() == null) {
                throw new ReglaNegocioException("El proceso no tiene una versión vigente.");
            }
            return proceso.getVersionPublicada();
        }
        try {
            return Integer.parseInt(lado);
        } catch (NumberFormatException noEsUnNumero) {
            throw new SolicitudInvalidaException("Se compara una versión por su número, vigente o borrador.");
        }
    }

    private Proceso buscar(Long empresaId, Long procesoId) {
        return procesoRepository.findByIdAndEmpresaIdAndActivoTrue(procesoId, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proceso no encontrado."));
    }
}
