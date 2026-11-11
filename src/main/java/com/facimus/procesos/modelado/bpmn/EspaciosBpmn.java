package com.facimus.procesos.modelado.bpmn;

import javax.xml.XMLConstants;

/** PR 53: los espacios de nombres de un archivo BPMN 2.0.2, y el propio para lo que BPMN no tiene. */
public final class EspaciosBpmn {

    public static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    public static final String DC = "http://www.omg.org/spec/DD/20100524/DC";
    public static final String DI = "http://www.omg.org/spec/DD/20100524/DI";
    public static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;

    /** Las extensiones de BPMN Process Manager: {@code bpmn/xsd/bpm.xsd}. */
    public static final String BPM = "urn:bpmn-process-manager:bpm:1";

    /** El lenguaje de las condiciones de los arcos (D6), en el atributo {@code language} de la expresion. */
    public static final String CONDICIONES = "urn:bpmn-process-manager:condiciones:1";

    /** Donde se dejan los procesos exportados; un archivo BPMN necesita uno. */
    public static final String PROCESOS = "urn:bpmn-process-manager:procesos";

    private EspaciosBpmn() {
    }
}
