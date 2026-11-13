import { Tono } from './tono.model';

/** PENDIENTE mientras el antivirus lo revisa, DISPONIBLE para bajarlo, BLOQUEADO con su motivo. */
export type EstadoDocumento = 'PENDIENTE' | 'DISPONIBLE' | 'BLOQUEADO';

/** Lo que se dice de cada estado y con que tono se pinta su insignia. */
export const NOMBRE_ESTADO_DOCUMENTO: Record<EstadoDocumento, string> = {
  PENDIENTE: $localize`Being checked`,
  DISPONIBLE: $localize`Available`,
  BLOQUEADO: $localize`Blocked`,
};

export const TONO_ESTADO_DOCUMENTO: Record<EstadoDocumento, Tono> = {
  PENDIENTE: 'aviso',
  DISPONIBLE: 'exito',
  BLOQUEADO: 'peligro',
};

/** El tope de un archivo, el mismo que la API y NGINX: 25 MB. */
export const TAMANO_MAXIMO_DOCUMENTO: number = 25 * 1024 * 1024;

/** Un documento con lo que se sabe de su ultima version (DocumentoResponse de la API). */
export interface Documento {
  id: number;
  procesoId: number;
  /** El elemento al que va pegado; vacio cuando es del proceso entero. */
  bpmnId: string | null;
  nombre: string;
  versionActual: number;
  estado: EstadoDocumento;
  motivo: string | null;
  /** Si un antivirus reviso la ultima version; sin antivirus configurado queda disponible sin revisar. */
  revisado: boolean;
  tipo: string;
  tamano: number;
  version: number;
  fechaModificacion: string;
}

/** Cada archivo subido a un documento (VersionDocumentoResponse). */
export interface VersionDocumento {
  numero: number;
  nombre: string;
  tipo: string;
  tamano: number;
  sha256: string;
  estado: EstadoDocumento;
  motivo: string | null;
  revisado: boolean;
  subidoPor: number;
  fecha: string;
}

/** Un documento y todas sus versiones, la mas nueva primero (DocumentoDetalleResponse). */
export interface DocumentoDetalle {
  documento: Documento;
  versiones: VersionDocumento[];
}

/** Un enlace firmado que vale unos minutos y no necesita sesion (EnlaceDeDescargaResponse). */
export interface EnlaceDeDescarga {
  url: string;
  venceEn: string;
}
