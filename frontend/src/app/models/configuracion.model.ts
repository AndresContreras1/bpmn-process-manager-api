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
  /** Minutos que una sesion aguanta sin renovarse, de 30 a 60. */
  inactividadSesionMinutos: number;
  /** Horas desde el login tras las que hay que volver a entrar, de 1 a 24. */
  duracionSesionHoras: number;
  version: number;
  fechaModificacion: string;
  modificadoPor: number | null;
}

/**
 * Lo que se manda al guardarla. `simulacion` viaja vacia cuando solo se cambia la politica, y entera cuando se
 * ajustan los socios desde el panel de simulacion: la API la toma completa o no la toma. Los limites de las sesiones
 * que no viajan se quedan como estaban.
 */
export interface ConfiguracionTiendaRequest {
  politicaEstructura: PoliticaEstructura;
  modoSimulacion: ModoSimulacion | null;
  simulacion: ParametrosSimulacion | null;
  inactividadSesionMinutos?: number | null;
  duracionSesionHoras?: number | null;
  version: number;
}

/** Los limites de las sesiones que la API acepta: los de NIST SP 800-63B-4 para AAL2. */
export const LIMITES_DE_SESION = {
  inactividad: { minimo: 30, maximo: 60 },
  duracion: { minimo: 1, maximo: 24 },
} as const;

/** Quien puede tocar la estructura, en la interfaz. */
export const NOMBRE_POLITICA: Record<PoliticaEstructura, string> = {
  SOLO_ADMINISTRADOR: 'Only administrators',
  ADMINISTRADOR_Y_EDITOR: 'Administrators and editors',
};
