/** Alta o edicion de un rol de proceso. Al editar va ademas la version. */
export interface RolProcesoRequest {
  nombre: string;
  descripcion: string;
}

export interface EditarRolProcesoRequest extends RolProcesoRequest {
  version: number;
}

/** Un rol de proceso de la tienda: quien atiende el trabajo de una lane (RolProcesoVistaResponse). */
export interface RolProceso {
  id: number;
  nombre: string;
  descripcion: string;
  /** True cuando alguna lane lo usa, asi que no se puede eliminar. */
  enUso: boolean;
  /** Cuantos procesos activos tienen una lane con este rol. */
  procesosQueLoUsan?: number;
  version: number;
  /** HU-20.4: los procesos que lo usan. Solo viene al pedir un rol, no en la lista. */
  procesos?: ProcesoDelRol[];
}

/** Un proceso que usa un rol, con lo justo para enlazarlo (ProcesoDelRolResponse). */
export interface ProcesoDelRol {
  id: number;
  nombre: string;
}
