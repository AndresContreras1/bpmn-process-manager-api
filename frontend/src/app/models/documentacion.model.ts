import { Tono } from './tono.model';

/** La documentacion de un proceso o de uno de sus elementos, con el HTML que la API ya saneo (DocumentacionResponse). */
export interface Documentacion {
  /** El elemento que documenta; vacio para el proceso entero. */
  bpmnId: string | null;
  markdown: string;
  /** Lo unico que se muestra: el Markdown convertido y limpiado en el servidor. */
  html: string;
  /** Se reenvia al guardar; vacia mientras no hay texto. */
  version: number | null;
  modificadoPor: number | null;
  fechaModificacion: string | null;
}

/** Cuerpo de PUT /procesos/{id}/documentacion. Un texto vacio la borra. */
export interface DocumentacionRequest {
  elemento: string | null;
  markdown: string;
  version: number | null;
}

/** Los nueve campos de la ficha de caracterizacion, en el orden en que se leen. */
export const CAMPOS_CARACTERIZACION = [
  'objetivo',
  'alcance',
  'entradas',
  'salidas',
  'proveedores',
  'clientes',
  'indicadores',
  'riesgos',
  'normativa',
] as const;

export type CampoCaracterizacion = (typeof CAMPOS_CARACTERIZACION)[number];

export const NOMBRE_CAMPO_CARACTERIZACION: Record<CampoCaracterizacion, string> = {
  objetivo: $localize`Objective`,
  alcance: $localize`Scope`,
  entradas: $localize`Inputs`,
  salidas: $localize`Outputs`,
  proveedores: $localize`Suppliers`,
  clientes: $localize`Customers`,
  indicadores: $localize`Indicators`,
  riesgos: $localize`Risks`,
  normativa: $localize`Regulation`,
};

/** El tope de cada campo, el mismo que valida la API. */
export const LARGO_CAMPO_CARACTERIZACION: number = 4000;

/** La ficha tal como la escribe y la devuelve la API: cada campo es texto libre y puede ir vacio. */
export type DatosCaracterizacion = Record<CampoCaracterizacion, string | null>;

/** CaracterizacionResponse: con la version vacia mientras nadie la escribio. */
export interface Caracterizacion extends DatosCaracterizacion {
  version: number | null;
  fechaModificacion: string | null;
}

/** Cuerpo de PUT /procesos/{id}/caracterizacion: la ficha entera con la version leida. */
export interface CaracterizacionRequest extends DatosCaracterizacion {
  version: number | null;
}

/** PENDIENTE mientras la cola lo arma, DISPONIBLE, o FALLIDO con su motivo. */
export type EstadoManual = 'PENDIENTE' | 'DISPONIBLE' | 'FALLIDO';

export const NOMBRE_ESTADO_MANUAL: Record<EstadoManual, string> = {
  PENDIENTE: $localize`Being built`,
  DISPONIBLE: $localize`Ready`,
  FALLIDO: $localize`Failed`,
};

export const TONO_ESTADO_MANUAL: Record<EstadoManual, Tono> = {
  PENDIENTE: 'aviso',
  DISPONIBLE: 'exito',
  FALLIDO: 'peligro',
};

/** Un manual pedido y en que va (ManualResponse). */
export interface Manual {
  id: number;
  procesoId: number;
  /** La version publicada que describe. */
  version: number;
  idioma: string;
  estado: EstadoManual;
  motivo: string | null;
  pedidoEn: string;
  listoEn: string | null;
}

/** Los dos formatos en que se baja un manual. */
export type FormatoManual = 'pdf' | 'html';
