export type EstadoProceso = 'BORRADOR' | 'PUBLICADO';
export type Clasificacion = 'INTERNA' | 'CONFIDENCIAL' | 'RESTRINGIDA';

export const CLASIFICACIONES: Clasificacion[] = ['INTERNA', 'CONFIDENCIAL', 'RESTRINGIDA'];

export const NOMBRE_CLASIFICACION: Record<Clasificacion, string> = {
  INTERNA: $localize`Internal`,
  CONFIDENCIAL: $localize`Confidential`,
  RESTRINGIDA: $localize`Restricted`,
};

/** Cuerpo de PUT /procesos/{id}/ficha. */
export interface FichaRequest {
  carpetaId: number | null;
  duenoId: number | null;
  proximaRevision: string | null;
  clasificacion: Clasificacion;
  etiquetas: string[];
  version: number;
}

/** Proceso de una tienda, con los mismos campos que ProcesoResponse de la API. */
export interface Proceso {
  modo?: 'DOCUMENTAL' | 'EJECUTABLE';
  carpetaId?: number | null;
  duenoId?: number | null;
  proximaRevision?: string | null;
  clasificacion?: Clasificacion;
  etiquetas?: string[];
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
  /** Numero de la version vigente; vacio mientras no se haya publicado nada. */
  versionPublicada: number | null;
  /**
   * True cuando el modelo de hoy ya no es el que se publico. Viene vacio en los listados, porque calcularlo
   * costaria leer un diagrama entero por fila, y para una invitada, que solo ve lo publicado.
   */
  borradorPendiente: boolean | null;
  /** PR 64: cada cuantos meses se revisa; confirmar que sigue vigente mueve la proxima revision esos meses. */
  mesesEntreRevisiones?: number | null;
  /** PR 64: la fecha de revision paso sin que nadie confirmara que sigue vigente; lo decide la API con su reloj. */
  revisionVencida?: boolean | null;
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
  /** F22: el nombre del dueno, que quien solo lee no podria buscar; null si no tiene. */
  duenoNombre?: string | null;
}

/** Campos por los que la API deja ordenar la lista de procesos. */
export type CampoOrden = 'nombre' | 'categoria' | 'estado' | 'fechaModificacion' | 'proximaRevision' | 'clasificacion';

/** Los campos de orden, para leerlos de la direccion sin confiar en lo que traiga. */
export const CAMPOS_ORDEN: CampoOrden[] = [
  'nombre',
  'categoria',
  'estado',
  'fechaModificacion',
  'proximaRevision',
  'clasificacion',
];
export type Direccion = 'asc' | 'desc';

/** Filtros de la lista: el nombre es parcial, la categoria exacta, y los vacios no se envian. */
export interface FiltrosProceso {
  nombre: string;
  estado: EstadoProceso | '';
  categoria: string;
  carpetaId?: number | null;
  duenoId?: number | null;
  etiqueta?: string;
  clasificacion?: Clasificacion | '';
  revisionAntesDe?: string;
  orden: CampoOrden;
  direccion: Direccion;
  pagina: number;
}

/**
 * La ficha de un proceso con otra carpeta y lo demas como estaba: mover un proceso es archivarlo en otro sitio, y la
 * API recibe la ficha entera, asi que lo que no se mueve se reenvia tal cual llego.
 */
export function fichaConCarpeta(proceso: Proceso, carpetaId: number | null): FichaRequest {
  return {
    carpetaId,
    duenoId: proceso.duenoId ?? null,
    proximaRevision: proceso.proximaRevision ?? null,
    clasificacion: proceso.clasificacion ?? 'INTERNA',
    etiquetas: proceso.etiquetas ?? [],
    version: proceso.version,
  };
}

/** Lo que el formulario de crear deja archivar de entrada: el dueno solo lo elige un administrador. */
export interface ArchivoInicial {
  carpetaId: number | null;
  duenoId: number | null;
  proximaRevision: string;
  clasificacion: Clasificacion;
  etiquetas: string;
}

/** Los mismos limites que valida la API en PUT /procesos/{id}/ficha. */
export const MAXIMO_DE_ETIQUETAS = 10;
export const LARGO_MAXIMO_DE_ETIQUETA = 40;

/** Las etiquetas escritas separadas por comas, sin las vacias ni las repetidas. */
export function leerEtiquetas(texto: string): string[] {
  const etiquetas = texto
    .split(',')
    .map((etiqueta) => etiqueta.trim())
    .filter((etiqueta) => etiqueta);
  return [...new Set(etiquetas)];
}

/** Que tienen de mas las etiquetas escritas: null si la API las va a aceptar. */
export type FaltaEnEtiquetas = 'demasiadas' | 'larga';

/**
 * La regla de la API dicha antes de enviar: como mucho diez etiquetas y de cuarenta caracteres cada una. Al crear,
 * la ficha va en una segunda peticion, y un 400 ahi dejaria el proceso creado sin archivar.
 */
export function faltaEnEtiquetas(texto: string): FaltaEnEtiquetas | null {
  const etiquetas = leerEtiquetas(texto);
  if (etiquetas.length > MAXIMO_DE_ETIQUETAS) {
    return 'demasiadas';
  }
  return etiquetas.some((etiqueta) => etiqueta.length > LARGO_MAXIMO_DE_ETIQUETA) ? 'larga' : null;
}

/**
 * La ficha de un proceso recien creado, o null si no hay nada que archivar: sin carpeta, sin dueno, sin fecha de
 * revision, interno y sin etiquetas es como nace cualquier proceso, y entonces no hace falta una segunda peticion.
 */
export function fichaInicial(archivo: ArchivoInicial, proceso: Proceso): FichaRequest | null {
  const etiquetas = leerEtiquetas(archivo.etiquetas);
  const proximaRevision = archivo.proximaRevision.trim() || null;
  if (
    archivo.carpetaId === null &&
    archivo.duenoId === null &&
    proximaRevision === null &&
    archivo.clasificacion === 'INTERNA' &&
    etiquetas.length === 0
  ) {
    return null;
  }
  return {
    carpetaId: archivo.carpetaId,
    duenoId: archivo.duenoId ?? proceso.duenoId ?? null,
    proximaRevision,
    clasificacion: archivo.clasificacion,
    etiquetas,
    version: proceso.version,
  };
}

/** Lo que la pagina de un proceso dice al abrirse, de lo que le paso la pantalla que la abrio. */
export interface AvisosAlAbrir {
  error: string | null;
  aviso: string | null;
}

/**
 * Los avisos que trae la direccion al llegar del formulario: creado, editado, o creado sin la ficha porque la segunda
 * peticion fallo. Basta con el has de ParamMap, y asi se prueba sin el router.
 */
export function avisosAlAbrir(consulta: { has(nombre: string): boolean }): AvisosAlAbrir {
  if (consulta.has('creado')) {
    return {
      aviso: $localize`Process created as a draft.`,
      error: consulta.has('sinArchivar')
        ? $localize`The process was created as a draft, but its filing could not be saved. Set it below.`
        : null,
    };
  }
  return { aviso: consulta.has('editado') ? $localize`Changes saved.` : null, error: null };
}

/** Nombre de cada estado en la interfaz. */
export const NOMBRE_ESTADO: Record<EstadoProceso, string> = {
  BORRADOR: $localize`Draft`,
  PUBLICADO: $localize`Published`,
};

// La API registra el historial en espanol. Estos textos son fijos...
const CAMBIOS: Partial<Record<string, string>> = {
  'Proceso creado.': $localize`Process created.`,
  'Proceso editado.': $localize`Name, description or category edited.`,
  'Ficha del repositorio editada.': $localize`Filing data edited.`,
  'Proceso publicado.': $localize`Process published.`,
  'Estado del proceso actualizado.': $localize`State updated.`,
  'Proceso eliminado (baja logica).': $localize`Process deleted.`,
};

// ...y estos terminan con el nombre de la otra tienda
const CAMBIOS_CON_TIENDA: [string, string][] = [
  ['Proceso compartido en solo lectura con ', $localize`Shared read-only with `],
  ['Se dejó de compartir el proceso con ', $localize`Stopped sharing with `],
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
