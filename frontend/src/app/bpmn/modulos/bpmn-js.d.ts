/** diagram-js-minimap no publica tipos. */
declare module 'diagram-js-minimap';

/**
 * bpmn-moddle, el lector y escritor de XML que usa bpmn-js. Su paquete solo publica los tipos de los elementos, no los
 * de la clase: aqui va lo poco que usa la prueba del espacio bpm:.
 */
declare module 'bpmn-moddle' {
  export class BpmnModdle {
    constructor(paquetes?: Record<string, unknown>);
    fromXML(xml: string): Promise<{ rootElement: object; warnings: unknown[] }>;
    toXML(elemento: object): Promise<{ xml: string }>;
  }
}
