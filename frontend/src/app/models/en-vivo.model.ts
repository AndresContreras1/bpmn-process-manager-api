/** Quien esta mirando un proceso y quien lo esta editando. */

export interface Presente {
  usuarioId: number;
  nombre: string;
  desde: string;
}

export interface Bloqueo {
  libre: boolean;
  mio: boolean;
  usuarioId: number | null;
  nombre: string | null;
  desde: string | null;
  vence: string | null;
}

export type EventoEnVivo =
  | { tipo: 'conectado' }
  | { tipo: 'desconectado' }
  | { tipo: 'presencia'; datos: { personas: Presente[] } }
  | { tipo: 'bloqueo'; datos: { usuarioId?: number; nombre?: string } }
  | { tipo: 'control-tomado'; datos: { usuarioId: number; nombre: string; anteriorId: number; anterior: string } }
  | { tipo: 'guardado'; datos: { autor: number | null } }
  | { tipo: 'publicado'; datos: { numero: number; autor: number | null } };
