package com.facimus.procesos.modelado.bpmn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PR 54: lo que paso con cada cosa de un archivo importado. Lo soportado se cuenta por tipo; lo convertido y lo no
 * soportado se dice elemento por elemento, con el motivo; lo ajeno que se conservo, por su clave; y lo que el archivo
 * incumple del esquema de la OMG va como aviso, porque muchas herramientas escriben BPMN que no valida y se lee igual.
 */
public record InformeBpmn(Map<String, Integer> soportados, List<Hallazgo> convertidos, List<Hallazgo> noSoportados,
        List<String> extensionesConservadas, List<String> avisosDelEsquema) {

    /** Un elemento del archivo y lo que se hizo con el. */
    public record Hallazgo(String elemento, String detalle) {
    }

    /** El informe que se va llenando mientras se convierte. */
    static final class Notas {

        private final Map<String, Integer> soportados = new LinkedHashMap<>();
        private final List<Hallazgo> convertidos = new ArrayList<>();
        private final List<Hallazgo> noSoportados = new ArrayList<>();
        private final List<String> errores = new ArrayList<>();

        void soportado(String tipo) {
            soportados.merge(tipo, 1, Integer::sum);
        }

        void convertido(String elemento, String detalle) {
            convertidos.add(new Hallazgo(elemento, detalle));
        }

        void noSoportado(String elemento, String detalle) {
            noSoportados.add(new Hallazgo(elemento, detalle));
        }

        /** Algo que impide importar el archivo: van todos juntos en la respuesta. */
        void error(String error) {
            errores.add(error);
        }

        List<String> errores() {
            return errores;
        }

        InformeBpmn informe(List<String> extensiones, List<String> avisosDelEsquema) {
            return new InformeBpmn(Collections.unmodifiableMap(new LinkedHashMap<>(soportados)), List.copyOf(convertidos), List.copyOf(noSoportados),
                    List.copyOf(extensiones), List.copyOf(avisosDelEsquema));
        }
    }
}
