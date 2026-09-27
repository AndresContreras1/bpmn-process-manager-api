import { Proceso } from './proceso.model';

/** Una tienda a la que se le comparte un proceso en solo lectura (EmpresaInvitadaResponse). */
export interface EmpresaInvitada {
  empresaId: number;
  nombre: string;
  nit: string;
  fechaCompartido: string;
}

/** Un proceso que otra tienda comparte con la tuya. Solo se ve su version vigente. */
export interface ProcesoRecibido {
  proceso: Proceso;
  empresaPropietariaId: number;
  empresaPropietariaNombre: string;
}
