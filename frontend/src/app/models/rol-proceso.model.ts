/** Un rol de proceso de la tienda: quien atiende el trabajo de una lane (RolProcesoVistaResponse). */
export interface RolProceso {
  id: number;
  nombre: string;
  descripcion: string;
  /** True cuando alguna lane lo usa, asi que no se puede eliminar. */
  enUso: boolean;
  version: number;
}
