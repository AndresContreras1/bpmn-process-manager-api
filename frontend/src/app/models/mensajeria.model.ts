import { Datos } from './caso.model';
import { Integracion, TipoDestino } from './diagrama.model';

/** Un mensaje que salio espera en la bandeja hasta que el reloj llega a su tick de entrega. */
export type EstadoMensajeSaliente = 'PENDIENTE' | 'ENTREGADO' | 'FALLIDO';

/** Que se hizo con un mensaje que llego. */
export type ResultadoCorrelacion = 'ENTREGADO_A_CASO' | 'CASO_NUEVO' | 'EN_ESPERA' | 'PROGRAMADO' | 'DESCARTADO';

/** De donde salio un mensaje que llego: de una persona, o de uno de los socios simulados. */
export type OrigenMensajeEntrante =
  | 'MANUAL'
  | 'CLIENTE_SIMULADO'
  | 'SIMULADOR_PAGOS'
  | 'SIMULADOR_TRANSPORTE'
  | 'SIMULADOR_NOTIFICACIONES';

/** Un mensaje que el proceso mando (MensajeSalienteResponse). */
export interface MensajeSaliente {
  id: number;
  casoId: number;
  casoReferencia: string;
  nombre: string;
  poolDestinoNombre: string;
  integracion: Integracion;
  tipoDestino: TipoDestino | null;
  clave: string | null;
  cuerpo: Datos | null;
  estado: EstadoMensajeSaliente;
  tickCreacion: number;
  tickEntrega: number;
  intentos: number;
  /** Por que no llego, cuando no llego. */
  error: string | null;
  fecha: string;
}

/** Un mensaje que llego al proceso, y lo que se hizo con el (MensajeEntranteResponse). */
export interface MensajeEntrante {
  id: number;
  procesoId: number;
  /** El caso con el que se correlaciono; vacio cuando nadie lo esperaba. */
  casoId: number | null;
  casoReferencia: string | null;
  nombre: string;
  clave: string | null;
  cuerpo: Datos | null;
  origen: OrigenMensajeEntrante;
  claveExterna: string | null;
  resultado: ResultadoCorrelacion;
  tick: number;
  fecha: string;
  /** True cuando ese mismo mensaje ya se habia recibido y no se volvio a hacer nada. */
  repetido: boolean;
}

/** Lo que un caso mando y lo que le llego: es lo que explica un caso que esta esperando. */
export interface MensajesDelCaso {
  salientes: MensajeSaliente[];
  entrantes: MensajeEntrante[];
}

export const NOMBRE_ESTADO_SALIENTE: Record<EstadoMensajeSaliente, string> = {
  PENDIENTE: 'Waiting for the clock',
  ENTREGADO: 'Delivered',
  FALLIDO: 'Failed',
};

export const NOMBRE_RESULTADO: Record<ResultadoCorrelacion, string> = {
  ENTREGADO_A_CASO: 'Delivered to a case',
  CASO_NUEVO: 'Opened a case',
  EN_ESPERA: 'Nobody was waiting for it',
  PROGRAMADO: 'Held for a later tick',
  DESCARTADO: 'Discarded',
};

export const NOMBRE_ORIGEN: Record<OrigenMensajeEntrante, string> = {
  MANUAL: 'sent by hand',
  CLIENTE_SIMULADO: 'the simulated customer',
  SIMULADOR_PAGOS: 'the payment gateway',
  SIMULADOR_TRANSPORTE: 'the carrier',
  SIMULADOR_NOTIFICACIONES: 'the notification service',
};
