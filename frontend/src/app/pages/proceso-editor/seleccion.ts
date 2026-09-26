import { Diagrama } from '../../models/diagrama.model';
import { TipoElemento } from '../../models/diagnostico.model';

/** Lo que esta elegido en el editor. El tipo hace falta porque los ids no son unicos entre tablas. */
export interface Seleccion {
  tipo: TipoElemento;
  id: number;
}

/**
 * El tipo que el diagnostico pone delante del nombre: `Actividad "Pick and pack"`. Es el mismo texto que usa el
 * historial de cambios, asi que se lee igual aqui, y es lo unico que permite saltar al elemento de un hallazgo:
 * el id por si solo no basta, porque un pool y una actividad pueden tener los dos el id 1.
 */
const CLASES: Partial<Record<string, TipoElemento>> = {
  Pool: 'POOL',
  Lane: 'LANE',
  Actividad: 'ACTIVIDAD',
  Gateway: 'GATEWAY',
  Evento: 'EVENTO',
  Arco: 'ARCO',
  Mensaje: 'MENSAJE',
};

/** Convierte el elemento de un hallazgo en algo que se pueda seleccionar; null si el hallazgo es del proceso. */
export function seleccionDelHallazgo(elemento: string | null, elementoId: number | null): Seleccion | null {
  if (elemento === null || elementoId === null) {
    return null;
  }
  const tipo: TipoElemento | undefined = CLASES[elemento.split(' ')[0]];
  return tipo ? { tipo, id: elementoId } : null;
}

/** El nombre de lo que esta elegido, para el encabezado del panel. */
export function nombreDeSeleccion(diagrama: Diagrama, seleccion: Seleccion): string {
  const { tipo, id } = seleccion;
  const buscar = <T extends { id: number; nombre?: string }>(lista: T[]): T | undefined =>
    lista.find((elemento: T) => elemento.id === id);
  switch (tipo) {
    case 'POOL':
      return buscar(diagrama.pools)?.nombre ?? '';
    case 'LANE':
      return buscar(diagrama.lanes)?.nombre ?? '';
    case 'ACTIVIDAD':
      return buscar(diagrama.actividades)?.nombre ?? '';
    case 'GATEWAY':
      return buscar(diagrama.gateways)?.nombre ?? '';
    case 'EVENTO':
      return buscar(diagrama.eventos)?.nombre ?? '';
    case 'MENSAJE':
      return buscar(diagrama.mensajes)?.nombre ?? '';
    case 'ARCO': {
      // Un arco no tiene nombre; lo que lo identifica es que une: "Receive order -> Check stock"
      const arco = diagrama.arcos.find((candidato) => candidato.id === id);
      if (!arco) {
        return '';
      }
      const nodos = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos];
      const nombre = (nodoId: number): string =>
        nodos.find((candidato) => candidato.id === nodoId)?.nombre ?? '?';
      return `${nombre(arco.origenId)} → ${nombre(arco.destinoId)}`;
    }
  }
}

/**
 * El nombre del elemento de un hallazgo, con su clase en ingles: `Actividad "Pick and pack"` se lee
 * `Task "Pick and pack"`. Solo se cambia la clase, que es una palabra fija. El problema y la sugerencia se
 * muestran como los manda la API, porque llevan datos dentro —cuantos flujos, que condicion, que version— y
 * traducirlos aqui significaria volver a escribir el catalogo entero y perderlos por el camino.
 */
export function elementoEnIngles(elemento: string | null): string | null {
  if (elemento === null) {
    return null;
  }
  const clase: string = elemento.split(' ')[0];
  const tipo: TipoElemento | undefined = CLASES[clase];
  return tipo ? NOMBRE_ELEMENTO[tipo] + elemento.slice(clase.length) : elemento;
}

/** Como se llama cada tipo de elemento en la interfaz. */
export const NOMBRE_ELEMENTO: Record<TipoElemento, string> = {
  POOL: 'Participant',
  LANE: 'Lane',
  ACTIVIDAD: 'Task',
  GATEWAY: 'Gateway',
  EVENTO: 'Event',
  ARCO: 'Sequence flow',
  MENSAJE: 'Message flow',
};

/** Los tres tipos que viven dentro de un lane y comparten numeracion de ids. */
export function esNodo(tipo: TipoElemento): boolean {
  return tipo === 'ACTIVIDAD' || tipo === 'GATEWAY' || tipo === 'EVENTO';
}

/** De que tipo es un nodo del lienzo, que emite solo su id. */
export function tipoDelNodo(diagrama: Diagrama, id: number): TipoElemento {
  if (diagrama.actividades.some((actividad) => actividad.id === id)) {
    return 'ACTIVIDAD';
  }
  return diagrama.gateways.some((gateway) => gateway.id === id) ? 'GATEWAY' : 'EVENTO';
}
