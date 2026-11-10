import { Idioma } from './preferencias.model';

export interface RegistroEmpresaRequest {
  nombreEmpresa: string;
  nit: string;
  correoContacto: string;
  nombreAdmin: string;
  emailAdmin: string;
  passwordAdmin: string;
  /** F12: el idioma y la zona horaria con que empieza; sin ellos, la API los deduce del navegador. */
  idioma?: Idioma;
  zonaHoraria?: string;
}

/** Organizacion registrada, con los mismos campos que EmpresaResponse de la API. */
export interface Empresa {
  id: number;
  nombre: string;
  nit: string;
  correoContacto: string;
  fechaRegistro: string;
  /** F14: cuando un administrador pidio darla de baja; null mientras siga abierta. */
  bajaSolicitadaEn?: string | null;
  /** Cuando se borrara todo lo suyo, treinta dias despues; hasta entonces es de solo lectura y la baja se cancela. */
  borradoProgramadoPara?: string | null;
}
