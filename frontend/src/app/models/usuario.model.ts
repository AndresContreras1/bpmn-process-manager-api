export type RolAcceso = 'ADMINISTRADOR' | 'EDITOR' | 'SOLO_LECTURA';

/** Usuario de una tienda, con los mismos campos que UsuarioResponse de la API. */
export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rolAcceso: RolAcceso;
  activo: boolean;
  empresaId: number;
}

/** Nombre de cada rol en la interfaz. */
export const NOMBRE_ROL: Record<RolAcceso, string> = {
  ADMINISTRADOR: 'Administrator',
  EDITOR: 'Editor',
  SOLO_LECTURA: 'Read-only',
};
