import {
  Actividad,
  Arco,
  Correlacion,
  Diagrama,
  Evento,
  Gateway,
  Lane,
  Mensaje,
  NOMBRE_DESTINO,
  NOMBRE_EVENTO,
  NOMBRE_GATEWAY,
  NOMBRE_SIN_CASO,
  NOMBRE_SI_FALLA,
  NOMBRE_TIPO_DATO,
  Pool,
} from '../models/diagrama.model';

/** Un flujo que entra a un nodo o sale de el, con el nombre del nodo del otro extremo. */
export interface FlujoDelNodo {
  nodo: string;
  etiqueta: string | null;
  condicion: string | null;
}

/** Un mensaje del diagrama con su numero, el de la lista debajo del dibujo. */
export interface MensajeDibujo {
  id: number;
  numero: number;
  nombre: string;
  contenido: string;
  origen: string;
  destino: string;
  nodoOrigenId: number | null;
  nodoDestinoId: number | null;
  desde: string | null;
  hasta: string | null;
  claves: string[];
}

/** Un mensaje visto desde el nodo que lo manda o lo espera. El numero es el mismo del dibujo. */
export interface MensajeDelNodo {
  numero: number;
  nombre: string;
  contenido: string;
  participante: string;
  comoViaja: string | null;
  siFalla: string | null;
  campos: string[];
  variable: string | null;
  correlacion: string | null;
}

/** Lo que el panel muestra del nodo elegido en el diagrama. */
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
  envia: MensajeDelNodo[];
  espera: MensajeDelNodo[];
}

/**
 * Arma el panel de una tarea, un gateway o un evento. Los tres comparten numeracion de ids, asi que el id basta
 * para saber cual es. Los mensajes llegan ya dibujados porque de ahi sale su numero, el mismo que se ve en el
 * diagrama: el panel y el dibujo tienen que decir lo mismo.
 */
export function detallarNodo(diagrama: Diagrama, id: number, dibujados: MensajeDibujo[]): DetalleNodo | null {
  const actividad: Actividad | undefined = diagrama.actividades.find((candidata) => candidata.id === id);
  const gateway: Gateway | undefined = diagrama.gateways.find((candidato) => candidato.id === id);
  const evento: Evento | undefined = diagrama.eventos.find((candidato) => candidato.id === id);
  const nodo: Actividad | Gateway | Evento | undefined = actividad ?? gateway ?? evento;
  if (!nodo) {
    return null;
  }
  const lane: Lane | undefined = diagrama.lanes.find((candidata) => candidata.id === nodo.laneId);
  const pool: Pool | undefined = diagrama.pools.find((candidato) => candidato.id === lane?.poolId);
  const flujo = (arco: Arco, otroExtremo: number): FlujoDelNodo => ({
    nodo: nombreDeNodo(diagrama, otroExtremo),
    etiqueta: arco.etiqueta,
    condicion: arco.condicion,
  });

  return {
    id,
    tipo: evento ? NOMBRE_EVENTO[evento.tipoEvento] : gateway ? NOMBRE_GATEWAY[gateway.tipoGateway] : $localize`Task`,
    nombre: nodo.nombre,
    descripcion: actividad?.descripcion ?? null,
    pool: pool?.nombre ?? '',
    lane: lane?.nombre ?? '',
    rol: lane?.rolProcesoNombre ?? '',
    entrantes: diagrama.arcos.filter((arco) => arco.destinoId === id).map((arco) => flujo(arco, arco.origenId)),
    salientes: diagrama.arcos.filter((arco) => arco.origenId === id).map((arco) => flujo(arco, arco.destinoId)),
    envia: dibujados
      .filter((dibujo: MensajeDibujo) => dibujo.nodoOrigenId === id)
      .map((dibujo: MensajeDibujo) => detallarMensaje(diagrama, dibujo, dibujo.destino)),
    espera: dibujados
      .filter((dibujo: MensajeDibujo) => dibujo.nodoDestinoId === id)
      .map((dibujo: MensajeDibujo) => detallarMensaje(diagrama, dibujo, dibujo.origen)),
  };
}

/** El mensaje con lo que solo se puede contar con palabras: como viaja, que pasa si falla y como se correlaciona. */
function detallarMensaje(diagrama: Diagrama, dibujo: MensajeDibujo, participante: string): MensajeDelNodo {
  const mensaje: Mensaje | undefined = diagrama.mensajes.find((candidato) => candidato.id === dibujo.id);
  const correlacion: Correlacion | undefined = diagrama.correlaciones.find(
    (candidata) => candidata.mensajeId === dibujo.id,
  );
  return {
    numero: dibujo.numero,
    nombre: dibujo.nombre,
    contenido: dibujo.contenido,
    participante,
    comoViaja: mensaje?.tipoDestino ? NOMBRE_DESTINO[mensaje.tipoDestino] : null,
    siFalla: mensaje?.siFalla ? NOMBRE_SI_FALLA[mensaje.siFalla] : null,
    campos: (mensaje?.campos ?? []).map((campo) => `${campo.nombre} (${NOMBRE_TIPO_DATO[campo.tipo]})`),
    variable: mensaje?.variable ?? null,
    correlacion: correlacion
      ? $localize`${correlacion.criterio}, from the field ${correlacion.campo}; with no open case, ${NOMBRE_SIN_CASO[correlacion.sinCaso]}`
      : null,
  };
}

/** El nombre de una tarea, un gateway o un evento por su id; los tres comparten numeracion. */
function nombreDeNodo(diagrama: Diagrama, id: number): string {
  const nodo = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].find(
    (candidato) => candidato.id === id,
  );
  return nodo?.nombre ?? $localize`Unknown`;
}

/** Los mensajes del diagrama, numerados por su id: el panel y la lista tienen que decir lo mismo. */
export function numerarMensajes(diagrama: Diagrama): MensajeDibujo[] {
  const pool = (id: number): string => diagrama.pools.find((uno) => uno.id === id)?.nombre ?? '';
  const nodo = (id: number | null): string | null => (id === null ? null : nombreDeNodo(diagrama, id));
  return [...diagrama.mensajes]
    .sort((uno, otro) => uno.id - otro.id)
    .map((mensaje, indice) => ({
      id: mensaje.id,
      numero: indice + 1,
      nombre: mensaje.nombre,
      contenido: mensaje.contenido,
      origen: pool(mensaje.poolOrigenId),
      destino: pool(mensaje.poolDestinoId),
      nodoOrigenId: mensaje.nodoOrigenId,
      nodoDestinoId: mensaje.nodoDestinoId,
      desde: nodo(mensaje.nodoOrigenId),
      hasta: nodo(mensaje.nodoDestinoId),
      claves: diagrama.correlaciones
        .filter((clave) => clave.mensajeId === mensaje.id)
        .map((clave) => clave.campo ?? clave.criterio),
    }));
}

/** El id en el dibujo de cada tarea, gateway y evento. Una version anterior a los bpmnId se escribio con su tipo y su id. */
export function bpmnIdsDeNodos(diagrama: Diagrama): Map<number, string> {
  const ids = new Map<number, string>();
  diagrama.actividades.forEach((uno) => ids.set(uno.id, uno.bpmnId ?? `Activity_${uno.id}`));
  diagrama.gateways.forEach((uno) => ids.set(uno.id, uno.bpmnId ?? `Gateway_${uno.id}`));
  diagrama.eventos.forEach((uno) => ids.set(uno.id, uno.bpmnId ?? `Event_${uno.id}`));
  return ids;
}
