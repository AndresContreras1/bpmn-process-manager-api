/** Una version retirada ya no corre, pero se sigue pudiendo mirar: los casos que la usaron siguen ahi. */
export type EstadoVersion = 'VIGENTE' | 'RETIRADA';

/** Una version publicada de un proceso: el diagrama congelado el dia que se publico (VersionResponse). */
export interface Version {
  id: number;
  procesoId: number;
  numero: number;
  estado: EstadoVersion;
  fechaPublicacion: string;
  publicadoPor: number | null;
  /** SHA-256 del diagrama: dos versiones con la misma huella dibujarian lo mismo. */
  huella: string;
}
