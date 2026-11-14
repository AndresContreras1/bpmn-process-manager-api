package com.facimus.procesos.ejecucion.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.facimus.procesos.common.CamposInvalidosException;
import com.facimus.procesos.common.RecursoNoEncontradoException;
import com.facimus.procesos.common.condiciones.Variables;
import com.facimus.procesos.common.formularios.ArchivosDeFormularios;
import com.facimus.procesos.common.formularios.Formulario;
import com.facimus.procesos.common.formularios.RespuestasDelFormulario;
import com.facimus.procesos.common.formularios.RespuestasDelFormulario.Referencias;
import com.facimus.procesos.common.formularios.RespuestasDelFormulario.Resultado;
import com.facimus.procesos.ejecucion.model.ModoCaso;
import com.facimus.procesos.gestion.service.UsuarioService;
import com.facimus.procesos.modelado.dto.response.FormularioResponse;

import lombok.RequiredArgsConstructor;

/**
 * PR 69: lo que entra a un caso por una persona pasa por el formulario que la version congelo: al abrirlo, el del
 * evento de inicio; al completar una tarea, el de la tarea. Lo que no lo cumple es un 400 con el error de cada campo, y
 * lo que si, entra con su tipo.
 *
 * <p>Sin formulario, los datos libres (clave y valor) quedan para el modo PRUEBA, que es donde se prueban los caminos
 * a mano. En PRODUCCION un nodo sin formulario no recibe datos: las variables se corrigen aparte, con
 * {@code PATCH /casos/{id}/variables}, que es del administrador.
 */
@Component
@RequiredArgsConstructor
class FormulariosDelCaso {

    static final String NO_CUMPLE = "Las respuestas no cumplen el formulario.";
    static final String SIN_FORMULARIO = "Sin formulario no se reciben datos en un caso de produccion: los datos "
            + "libres son del modo PRUEBA.";
    static final String NO_ES_UN_CAMPO = "No es un campo: este paso no tiene formulario, y en produccion solo entran "
            + "los datos de un formulario.";

    private final UsuarioService usuarioService;
    private final ArchivosDeFormularios archivos;

    /** Las variables con que se abre el caso, en la ruta {@code variables} de la peticion. */
    Map<String, Object> alAbrir(Long empresaId, Long procesoId, GrafoDeVersion grafo, NodoDeLaVersion inicio,
            ModoCaso modo, Map<String, Object> variables) {
        return contra(empresaId, procesoId, grafo.formularioDe(inicio.id()).orElse(null), modo, variables,
                Variables.de(Map.of()), "variables");
    }

    /** Los datos con que se completa la tarea, en la ruta {@code datos} de la peticion. */
    Map<String, Object> alCompletar(Long empresaId, Long procesoId, GrafoDeVersion grafo, Long nodoId, ModoCaso modo,
            Map<String, Object> datos, Variables delCaso) {
        return contra(empresaId, procesoId, grafo.formularioDe(nodoId).orElse(null), modo, datos, delCaso, "datos");
    }

    /** Lo que se propone en cada campo del formulario, por lo que el caso ya tiene. */
    static Map<String, Object> iniciales(FormularioResponse formulario, Variables delCaso) {
        return formulario == null ? Map.of() : RespuestasDelFormulario.iniciales(formulario.formulario(), delCaso);
    }

    private Map<String, Object> contra(Long empresaId, Long procesoId, FormularioResponse formulario,
            ModoCaso modo, Map<String, Object> datos, Variables delCaso, String ruta) {
        Map<String, Object> llegados = datos == null ? Map.of() : datos;
        if (formulario == null) {
            return sinFormulario(modo, llegados, ruta);
        }
        Formulario esquema = formulario.formulario();
        Resultado resultado = RespuestasDelFormulario.validar(esquema, llegados, delCaso,
                new DeLaTienda(empresaId, procesoId));
        if (!resultado.valido()) {
            throw CamposInvalidosException.en(ruta, NO_CUMPLE, resultado.errores());
        }
        return resultado.valores();
    }

    private static Map<String, Object> sinFormulario(ModoCaso modo, Map<String, Object> llegados, String ruta) {
        if (modo == ModoCaso.PRUEBA || llegados.isEmpty()) {
            return llegados;
        }
        Map<String, String> errores = new LinkedHashMap<>();
        llegados.keySet().forEach(clave -> errores.put(clave, NO_ES_UN_CAMPO));
        throw CamposInvalidosException.en(ruta, SIN_FORMULARIO, errores);
    }

    /** Lo que solo se sabe mirando la tienda: si la persona sigue en ella y si el documento es del proceso. */
    private final class DeLaTienda implements Referencias {

        private final Long empresaId;
        private final Long procesoId;

        DeLaTienda(Long empresaId, Long procesoId) {
            this.empresaId = empresaId;
            this.procesoId = procesoId;
        }

        @Override
        public boolean esPersona(long id) {
            try {
                return usuarioService.obtener(empresaId, id).activo();
            } catch (RecursoNoEncontradoException _) {
                return false;
            }
        }

        @Override
        public boolean esArchivo(long id) {
            return archivos.disponible(empresaId, procesoId, id);
        }
    }
}
