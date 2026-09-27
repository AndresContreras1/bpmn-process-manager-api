export type EstadoProceso = 'BORRADOR' | 'PUBLICADO';

/** Proceso de una tienda, con los mismos campos que ProcesoResponse de la API. */
export interface Proceso {
  id: number;
  nombre: string;
  descripcion: string;
  categoria: string;
  estado: EstadoProceso;
  activo: boolean;
  fechaCreacion: string;
  fechaModificacion: string;
  /** Version leida en el ultimo GET. Se reenvia al editar y al publicar; con una vieja la API responde 409. */
  version: number;
}

/** Cuerpo para crear un proceso (ProcesoRequest de la API). */
export interface ProcesoRequest {
  nombre: string;
  descripcion: string;
  categoria: string;
}

/** Cuerpo para editar: los mismos campos y la version, que la API exige (EditarProcesoRequest). */
export interface EditarProcesoRequest extends ProcesoRequest {
  version: number;
}

export interface HistorialCambio {
  id: number;
  fechaCambio: string;
  descripcionCambio: string;
  autorNombre: string;
}

/** Detalle de un proceso: el proceso y su historial de cambios (ProcesoDetalleResponse). */
export interface ProcesoDetalle {
  proceso: Proceso;
  historial: HistorialCambio[];
}

/** Campos por los que la API deja ordenar la lista de procesos. */
export type CampoOrden = 'nombre' | 'categoria' | 'estado' | 'fechaModificacion';
export type Direccion = 'asc' | 'desc';

/** Filtros de la lista: el nombre es parcial, la categoria exacta, y los vacios no se envian. */
export interface FiltrosProceso {
  nombre: string;
  estado: EstadoProceso | '';
  categoria: string;
  orden: CampoOrden;
  direccion: Direccion;
  pagina: number;
}

/** Nombre de cada estado en la interfaz. */
export const NOMBRE_ESTADO: Record<EstadoProceso, string> = {
  BORRADOR: 'Draft',
  PUBLICADO: 'Published',
};

// La API registra el historial en espanol. Estos textos son fijos...
const CAMBIOS: Partial<Record<string, string>> = {
  'Proceso creado.': 'Process created.',
  'Proceso editado.': 'Name, description or category edited.',
  'Proceso publicado.': 'Process published.',
  'Estado del proceso actualizado.': 'State updated.',
  'Proceso eliminado (baja logica).': 'Process deleted.',
};

// ...y estos terminan con el nombre de la otra tienda
const CAMBIOS_CON_TIENDA: [string, string][] = [
  ['Proceso compartido en solo lectura con ', 'Shared read-only with '],
  ['Se dejó de compartir el proceso con ', 'Stopped sharing with '],
];

/** Un cambio del historial en ingles; si la API trae un texto que no esta aqui, se muestra el original. */
export function cambioEnIngles(descripcion: string): string {
  const fijo: string | undefined = CAMBIOS[descripcion];
  if (fijo) {
    return fijo;
  }
  for (const [espanol, ingles] of CAMBIOS_CON_TIENDA) {
    if (descripcion.startsWith(espanol)) {
      return ingles + descripcion.slice(espanol.length);
    }
  }
  return descripcion;
}
