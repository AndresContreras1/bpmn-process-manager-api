package com.facimus.procesos.modelado.bpmn;

/**
 * PR 53: los ids de un archivo BPMN. Los de los elementos son su {@code bpmn_id}; los que el modelo no guarda (el
 * proceso de cada pool, el mensaje de cada flujo de mensaje, la definicion de un evento) se derivan de el, asi que
 * tampoco cambian entre una exportacion y la siguiente.
 */
final class IdsBpmn {

    private IdsBpmn() {
    }

    /**
     * El bpmn_id del elemento. Una instantanea anterior al PR 53 no lo traia: entonces es el que le dio la migracion
     * V41 a cada elemento que ya existia, su tipo y su id.
     */
    static String de(String bpmnId, String tipo, Long id) {
        return bpmnId != null ? bpmnId : tipo + "_" + id;
    }

    /** Otro id del mismo elemento: {@code Participant_42} da {@code Process_42}. */
    static String derivado(String bpmnId, String prefijoDelElemento, String prefijoNuevo) {
        String base = bpmnId.startsWith(prefijoDelElemento + "_")
                ? bpmnId.substring(prefijoDelElemento.length() + 1)
                : bpmnId;
        return prefijoNuevo + "_" + base;
    }
}
