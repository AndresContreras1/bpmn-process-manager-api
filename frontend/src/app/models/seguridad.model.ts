import { RolAcceso } from './usuario.model';

/** F14: lo que la API dice de la seguridad de una cuenta, con los mismos campos que sus respuestas. */

/** GET /api/v1/auth/segundo-factor. */
export interface EstadoSegundoFactor {
  /** La aplicacion de autenticacion esta confirmada y el login pide su codigo. */
  totp: boolean;
  passkeys: number;
  codigosRestantes: number;
}

/** POST /api/v1/auth/segundo-factor/totp: el secreto, para escribirlo a mano, y la URI que lleva el QR. */
export interface InscripcionTotp {
  secreto: string;
  uri: string;
}

export interface CodigosDeRecuperacion {
  codigos: string[];
}

export interface Passkey {
  id: number;
  etiqueta: string;
  creadaEn: string;
  ultimoUsoEn: string | null;
  /** La guarda su proveedor (iCloud, Google) y llega a los demas dispositivos de la persona. */
  sincronizada: boolean;
}

export interface PasskeyRegistrada {
  passkey: Passkey;
  /** Vacio si la persona ya tenia codigos de recuperacion. */
  codigosDeRecuperacion: string[];
}

/** Las opciones de una ceremonia de WebAuthn, en JSON, y el desafio con que la API la reconoce despues. */
export interface OpcionesWebAuthn {
  desafio: string;
  opciones: Record<string, unknown>;
}

export interface IdentidadExterna {
  id: number;
  proveedor: string;
  email: string;
  vinculadaEn: string;
}

export interface SesionAbierta {
  id: number;
  inicio: string;
  ultimaActividad: string;
  factores: string[];
  actual: boolean;
}

/** Como se confirma quien se es para lo que pide una verificacion reciente. */
export interface Reautenticacion {
  password?: string;
  codigo?: string;
}

export type PoliticaSegundoFactor = 'OPCIONAL' | 'ADMINISTRADORES' | 'TODOS';

export interface Dominio {
  id: number;
  dominio: string;
  rolPorDefecto: RolAcceso;
  registroTxt: string;
  verificado: boolean;
  verificadoEn: string | null;
}

/** Un registro de la bitacora (RegistroDeBitacoraResponse). */
export interface RegistroDeBitacora {
  secuencia: number;
  momento: string;
  actorTipo: string;
  actorId: number | null;
  accion: string;
  recurso: string | null;
  recursoId: number | null;
  exito: boolean;
  ip: string | null;
  userAgent: string | null;
  requestId: string | null;
}

export interface PaginaDeBitacora {
  registros: RegistroDeBitacora[];
  /** La secuencia desde la que pedir la pagina siguiente; null en la ultima. */
  siguiente: number | null;
}

export interface VerificacionDeBitacora {
  integra: boolean;
  registros: number;
  rotaEn: number | null;
  motivo: string | null;
}

export interface FiltrosDeBitacora {
  accion?: string | null;
  desde?: string | null;
  hasta?: string | null;
  antes?: number | null;
}
