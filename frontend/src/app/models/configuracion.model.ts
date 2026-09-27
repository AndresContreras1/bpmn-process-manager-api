/** Quien puede tocar la estructura del diagrama: solo el administrador, o tambien los editores. */
export type PoliticaEstructura = 'SOLO_ADMINISTRADOR' | 'ADMINISTRADOR_Y_EDITOR';
/** Si el reloj de la tienda lo mueve una persona o corre solo. */
export type ModoSimulacion = 'MANUAL' | 'AUTOMATICO';

/** Como responde cada socio simulado. Se ajusta desde las pantallas de operacion. */
export interface ParametrosSimulacion {
  /** De aqui salen todas las decisiones de los socios: la misma semilla repite la misma simulacion. */
  semilla: number;
  tasaRechazoPagos: number;
  ticksRespuestaPagos: number;
  reglaRechazoPagos: string | null;
  ticksRespuestaTransporte: number;
  ticksEntrega: number;
  tasaPerdidaEnvios: number;
  tasaFalloNotificaciones: number;
}

/** La configuracion de la tienda (ConfiguracionTiendaResponse). */
export interface ConfiguracionTienda {
  politicaEstructura: PoliticaEstructura;
  /** En que tick va el reloj de la tienda. */
  reloj: number;
  modoSimulacion: ModoSimulacion;
  simulacion: ParametrosSimulacion;
  version: number;
  fechaModificacion: string;
  modificadoPor: number | null;
}

/**
 * Lo que se manda al guardarla. `simulacion` viaja vacia cuando solo se cambia la politica: los parametros de
 * los socios se ajustan desde la simulacion, no desde aqui.
 */
export interface ConfiguracionTiendaRequest {
  politicaEstructura: PoliticaEstructura;
  modoSimulacion: ModoSimulacion | null;
  simulacion: null;
  version: number;
}

/** Quien puede tocar la estructura, en la interfaz. */
export const NOMBRE_POLITICA: Record<PoliticaEstructura, string> = {
  SOLO_ADMINISTRADOR: 'Only administrators',
  ADMINISTRADOR_Y_EDITOR: 'Administrators and editors',
};
