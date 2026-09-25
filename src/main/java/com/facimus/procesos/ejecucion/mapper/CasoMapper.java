package com.facimus.procesos.ejecucion.mapper;

import java.util.List;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import com.facimus.procesos.ejecucion.dto.response.CasoResponse;
import com.facimus.procesos.ejecucion.dto.response.EventoCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.PasoDelCasoResponse;
import com.facimus.procesos.ejecucion.dto.response.TareaResponse;
import com.facimus.procesos.ejecucion.model.ActividadCaso;
import com.facimus.procesos.ejecucion.model.Caso;
import com.facimus.procesos.ejecucion.model.EventoCaso;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce lo que la ejecucion guarda a lo que la API responde. Es una clase y no una interfaz porque las variables
 * del caso y los datos de una tarea se guardan como JSON en una columna y salen como objeto: esa traduccion
 * necesita el mismo JsonMapper que usa el resto de la aplicacion.
 *
 * <p>La fecha de inicio de un caso es su fecha de creacion, y quien lo abrio, quien creo la fila: un caso no tiene
 * columnas propias para eso porque la auditoria ya las guarda.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class CasoMapper {

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {
    };

    @Autowired
    protected JsonMapper json;

    @Mapping(target = "procesoId", source = "proceso.id")
    @Mapping(target = "procesoNombre", source = "proceso.nombre")
    @Mapping(target = "versionNumero", source = "versionProceso.numero")
    @Mapping(target = "fechaInicio", source = "fechaCreacion")
    public abstract CasoResponse toResponse(Caso caso);

    public abstract List<CasoResponse> toResponses(List<Caso> casos);

    public abstract PasoDelCasoResponse toPaso(ActividadCaso paso);

    public abstract List<PasoDelCasoResponse> toPasos(List<ActividadCaso> pasos);

    @Mapping(target = "casoId", source = "caso.id")
    @Mapping(target = "casoReferencia", source = "caso.referencia")
    @Mapping(target = "procesoId", source = "caso.proceso.id")
    @Mapping(target = "procesoNombre", source = "caso.proceso.nombre")
    public abstract TareaResponse toTarea(ActividadCaso tarea);

    public abstract List<TareaResponse> toTareas(List<ActividadCaso> tareas);

    public abstract EventoCasoResponse toEvento(EventoCaso evento);

    public abstract List<EventoCasoResponse> toEventos(List<EventoCaso> eventos);

    /** El JSON guardado, como objeto; una columna vacia no es un objeto vacio, es que no hay datos. */
    public Map<String, Object> aMapa(String guardado) {
        return guardado == null || guardado.isBlank() ? null : json.readValue(guardado, MAPA);
    }

    /** Lo que llega del cliente, como se guarda; sin variables se guarda un objeto vacio, nunca nulo. */
    public String aJson(Map<String, Object> variables) {
        return json.writeValueAsString(variables == null ? Map.of() : variables);
    }
}
