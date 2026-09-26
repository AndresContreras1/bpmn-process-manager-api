export type RolAcceso = 'ADMINISTRADOR' | 'EDITOR' | 'SOLO_LECTURA';

/** Usuario de una tienda, con los mismos campos que UsuarioResponse de la API. */
export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rolAcceso: RolAcceso;
  activo: boolean;
  empresaId: number;
  version: number;
  /** True mientras entre con una clave temporal: hasta cambiarla no puede hacer nada mas. */
  debeCambiarClave: boolean;
  /**
   * La clave temporal, y solo en la respuesta de crearlo o de restablecerla. No se guarda en ningun sitio: si la
   * pantalla no la ensena en ese momento, se pierde y hay que restablecerla otra vez.
   */
  claveTemporal?: string;
}

/** Alta de un usuario. Sin password la API inventa una temporal y la devuelve una sola vez. */
export interface CrearUsuarioRequest {
  nombre: string;
  email: string;
  password: string | null;
  rolAcceso: RolAcceso;
}

/** Cambiar el rol o dar de baja. Va con la version, como todo lo que se edita. */
export interface ActualizarUsuarioRequest {
  nombre: string | null;
  rolAcceso: RolAcceso | null;
  activo: boolean | null;
  version: number;
}

/** Los roles de proceso que atiende un usuario: la bandeja de tareas sale de aqui. */
export interface RolDeUsuario {
  id: number;
  nombre: string;
  descripcion: string;
}

/** Nombre de cada rol en la interfaz. */
export const NOMBRE_ROL: Record<RolAcceso, string> = {
  ADMINISTRADOR: 'Administrator',
  EDITOR: 'Editor',
  SOLO_LECTURA: 'Read-only',
};
