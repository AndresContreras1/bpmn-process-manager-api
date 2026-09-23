package com.facimus.procesos.modelado.service;

/** Quien revisa un diagrama BPMN y devuelve hallazgos. Hoy lo implementa un modelo de lenguaje. */
public interface RevisorDeDiagramas {

    /** Si esta instalacion tiene con que llamarlo. Sin eso el endpoint responde 503 sin gastar una llamada. */
    boolean estaConfigurado();

    /** Manda el diagrama descrito en texto y devuelve lo que contesto, ya comprobado contra el formato acordado. */
    Dictamen revisar(String diagrama);
}
