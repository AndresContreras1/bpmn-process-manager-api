import { Datos } from './caso.model';
import { ModoSimulacion } from './configuracion.model';
import { Integracion } from './diagrama.model';

/** Cuantos mensajes espera una clase de socio. */
export interface PendientesPorSocio {
  socio: Integracion;
  cantidad: number;
}

/** Donde va la simulacion de la tienda (PanelDeSimulacionResponse). */
export interface PanelDeSimulacion {
  /** El reloj de la tienda, en ticks. Solo sube. */
  reloj: number;
  modo: ModoSimulacion;
  /** Mensajes mandados que todavia no se han entregado. */
  salientesPendientes: number;
  porSocio: PendientesPorSocio[];
  /** Mensajes que llegaron antes de que nadie los esperara. */
  entrantesEnEspera: number;
}

/** Cuanto mover el reloj: entre 1 y 100 ticks por vez. */
export interface TickRequest {
  ticks: number;
}

/** Un lote de pedidos del cliente simulado. El proceso tiene que empezar con un mensaje. */
export interface PedidosSimuladosRequest {
  procesoId: number;
  cantidad: number;
  /** Lo que todos los pedidos llevan igual; lo que traiga gana sobre lo que invente el cliente simulado. */
  plantilla: Datos;
}

/** Lo que el proceso hizo con el lote (PedidosSimuladosResponse). */
export interface PedidosSimulados {
  /** El mensaje por el que entraron, que es el que abre un caso de este proceso. */
  mensaje: string;
  pedidos: number;
  casosNuevos: number;
  referencias: string[];
}
