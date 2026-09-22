export interface RegistroEmpresaRequest {
  nombreEmpresa: string;
  nit: string;
  correoContacto: string;
  nombreAdmin: string;
  emailAdmin: string;
  passwordAdmin: string;
}

/** Tienda registrada, con los mismos campos que EmpresaResponse de la API. */
export interface Empresa {
  id: number;
  nombre: string;
  nit: string;
  correoContacto: string;
  fechaRegistro: string;
}
