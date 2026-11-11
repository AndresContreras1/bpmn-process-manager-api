/** El modelo como archivo BPMN, y lo que se dice de el al guardarlo o compararlo. */

export interface ModeloLeido {
  xml: string;
  huella: string | null;
}

export interface ModeloGuardado {
  procesoId: number;
  creados: number;
  editados: number;
  dadosDeBaja: number;
  huella: string;
}

export interface Revision {
  id: number;
  motivo: 'AUTOGUARDADO' | 'CIERRE' | 'ANTES_DE_RESTAURAR';
  huella: string;
  usuarioId: number | null;
  fecha: string;
}

export interface ElementoComparado {
  bpmnId: string;
  tipo: string;
  nombre: string | null;
}

export interface CampoCambiado {
  campo: string;
  antes: string | null;
  despues: string | null;
}

export interface ElementoCambiado extends ElementoComparado {
  campos: CampoCambiado[];
}

export interface Comparacion {
  de: string;
  a: string;
  agregados: ElementoComparado[];
  quitados: ElementoComparado[];
  cambiados: ElementoCambiado[];
  movidos: ElementoComparado[];
}
