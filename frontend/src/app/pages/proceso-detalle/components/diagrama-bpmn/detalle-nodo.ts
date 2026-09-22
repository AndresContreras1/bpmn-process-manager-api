import { Actividad, Arco, Diagrama, Gateway, Lane, NOMBRE_GATEWAY, Pool } from '../../../../models/diagrama.model';

/** Un flujo que entra a un nodo o sale de el, con el nombre del nodo del otro extremo. */
export interface FlujoDelNodo {
  nodo: string;
  etiqueta: string | null;
  condicion: string | null;
}

/** Lo que el panel muestra de la tarea o el gateway elegido en el diagrama. */
export interface DetalleNodo {
  id: number;
  tipo: string;
  nombre: string;
  descripcion: string | null;
  pool: string;
  lane: string;
  rol: string;
  entrantes: FlujoDelNodo[];
  salientes: FlujoDelNodo[];
}

export function detallarNodo(diagrama: Diagrama, id: number): DetalleNodo | null {
  const actividad: Actividad | undefined = diagrama.actividades.find((candidata) => candidata.id === id);
  const gateway: Gateway | undefined = diagrama.gateways.find((candidato) => candidato.id === id);
  const nodo: Actividad | Gateway | undefined = actividad ?? gateway;
  if (!nodo) {
    return null;
  }
  const lane: Lane | undefined = diagrama.lanes.find((candidata) => candidata.id === nodo.laneId);
  const pool: Pool | undefined = diagrama.pools.find((candidato) => candidato.id === lane?.poolId);
  const nombreDelNodo = (nodoId: number): string =>
    [...diagrama.actividades, ...diagrama.gateways].find((candidato) => candidato.id === nodoId)?.nombre ?? 'Unknown';
  const flujo = (arco: Arco, otroExtremo: number): FlujoDelNodo => ({
    nodo: nombreDelNodo(otroExtremo),
    etiqueta: arco.etiqueta,
    condicion: arco.condicion,
  });

  return {
    id,
    tipo: gateway ? NOMBRE_GATEWAY[gateway.tipoGateway] : 'Task',
    nombre: nodo.nombre,
    descripcion: actividad?.descripcion ?? null,
    pool: pool?.nombre ?? '',
    lane: lane?.nombre ?? '',
    rol: lane?.rolProcesoNombre ?? '',
    entrantes: diagrama.arcos.filter((arco) => arco.destinoId === id).map((arco) => flujo(arco, arco.origenId)),
    salientes: diagrama.arcos.filter((arco) => arco.origenId === id).map((arco) => flujo(arco, arco.destinoId)),
  };
}
