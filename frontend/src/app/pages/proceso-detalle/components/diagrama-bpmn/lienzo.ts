import {
  Diagrama,
  Evento,
  Mensaje,
  NOMBRE_EVENTO,
  NOMBRE_PARTICIPANTE,
  Pool,
  TipoEvento,
  TipoGateway,
  TipoParticipante,
  terminaElProceso,
} from '../../../../models/diagrama.model';

// Medidas del dibujo, en las mismas unidades de posicionX y posicionY
const MARGEN = 16;
const FRANJA = 30; // ancho de la franja con el nombre de un pool o de un lane
const ANCHO_TAREA = 128;
const ALTO_TAREA = 56;
const LADO_GATEWAY = 50;
const RADIO_EVENTO = 18;
const RELLENO_X = 48; // espacio entre las franjas y el primer nodo, y despues del ultimo
const RELLENO_ARRIBA = 24;
const RELLENO_ABAJO = 40; // mas espacio abajo: el nombre de un gateway va debajo del rombo
const ALTO_CAJA_NEGRA = 56;
const ALTO_VACIO = 96;
const SEPARACION = 48; // entre pools, por donde bajan los mensajes
const ANCHO_MINIMO = 720;
const ALTO_LINEA = 14;

// Arriba los clientes, en medio la tienda con sus lanes y abajo los demas participantes
const FILA: Record<TipoParticipante, number> = { CLIENTE: 0, EMPRESA: 1, PROVEEDOR: 2, SISTEMA_EXTERNO: 2 };

/** Texto en varias lineas: la x donde se ancla y la linea base de la primera linea. */
export interface TextoDibujo {
  lineas: string[];
  x: number;
  y: number;
}

export interface PoolDibujo {
  id: number;
  nombre: string;
  tipo: string;
  titulo: string;
  cajaNegra: boolean;
  sinLanes: boolean;
  x: number;
  y: number;
  ancho: number;
  alto: number;
  etiqueta: string;
}

export interface LaneDibujo {
  id: number;
  nombre: string;
  rol: string;
  x: number;
  y: number;
  ancho: number;
  alto: number;
  etiqueta: string;
}

export interface ActividadDibujo {
  id: number;
  nombre: string;
  x: number;
  y: number;
  ancho: number;
  alto: number;
  texto: TextoDibujo;
}

export interface GatewayDibujo {
  id: number;
  nombre: string;
  tipo: TipoGateway;
  puntos: string;
  marca: string;
  texto: TextoDibujo;
}

/**
 * Un evento, con la notacion de BPMN: circulo fino si arranca el proceso, doble si espera un mensaje en mitad del
 * flujo y grueso si termina un camino. Los tres que llevan mensaje muestran un sobre, relleno solo cuando lo manda.
 */
export interface EventoDibujo {
  id: number;
  nombre: string;
  tipo: TipoEvento;
  titulo: string;
  /** Que circulo le toca: uno fino, uno doble o uno grueso. */
  clase: 'inicio' | 'intermedio' | 'fin';
  cx: number;
  cy: number;
  radio: number;
  doble: boolean;
  grueso: boolean;
  sobre: string | null;
  sobreRelleno: boolean;
  texto: TextoDibujo;
}

export interface ArcoDibujo {
  id: number;
  ruta: string;
  etiqueta: string | null;
  etiquetaX: number;
  etiquetaY: number;
  anclaje: 'start' | 'middle';
}

export interface MensajeDibujo {
  id: number;
  numero: number;
  nombre: string;
  contenido: string;
  origen: string;
  destino: string;
  /** Nodo del que sale y al que entra; vacios cuando ese lado es una caja negra. */
  nodoOrigenId: number | null;
  nodoDestinoId: number | null;
  /** Nombre de esos nodos, para el titulo del dibujo. */
  desde: string | null;
  hasta: string | null;
  claves: string[];
  ruta: string;
  salidaX: number;
  salidaY: number;
  numeroX: number;
  numeroY: number;
}

/** Todo lo que el visor dibuja, ya ubicado en el lienzo. */
export interface Lienzo {
  ancho: number;
  alto: number;
  anchoMinimo: number;
  pools: PoolDibujo[];
  lanes: LaneDibujo[];
  actividades: ActividadDibujo[];
  gateways: GatewayDibujo[];
  eventos: EventoDibujo[];
  arcos: ArcoDibujo[];
  mensajes: MensajeDibujo[];
}

/** Centro y medio tamano de un nodo, para trazar los arcos que entran y salen de el. */
interface NodoUbicado {
  cx: number;
  cy: number;
  medioAncho: number;
  medioAlto: number;
  esGateway: boolean;
}

/**
 * Ubica cada elemento del diagrama. La x de cada nodo sale de posicionX y es comun a todos los lanes, asi el flujo se
 * lee de izquierda a derecha; la y sale de posicionY, pero relativa a su lane: cada lane crece lo que necesiten sus
 * nodos y ningun nodo se dibuja fuera de su lane.
 */
export function dibujarDiagrama(diagrama: Diagrama): Lienzo {
  // Los eventos entran en la cuenta: el de inicio suele ser el nodo mas a la izquierda de todo el diagrama
  const posicionesX: number[] = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].map(
    (nodo) => nodo.posicionX,
  );
  const minX: number = posicionesX.length ? Math.min(...posicionesX) : 0;
  const maxX: number = posicionesX.length ? Math.max(...posicionesX) : 0;
  const anchoPool: number = Math.max(2 * FRANJA + maxX - minX + ANCHO_TAREA + 2 * RELLENO_X, ANCHO_MINIMO);
  const xDe = (posicionX: number): number => MARGEN + 2 * FRANJA + RELLENO_X + ANCHO_TAREA / 2 + posicionX - minX;

  const lienzo: Lienzo = {
    ancho: anchoPool + 2 * MARGEN,
    alto: 0,
    anchoMinimo: 0,
    pools: [],
    lanes: [],
    actividades: [],
    gateways: [],
    eventos: [],
    arcos: [],
    mensajes: [],
  };
  const ubicados = new Map<number, NodoUbicado>();
  const cajasDePool = new Map<number, PoolDibujo>();

  const pools: Pool[] = [...diagrama.pools].sort(
    (a: Pool, b: Pool) => FILA[a.tipoParticipante] - FILA[b.tipoParticipante] || a.orden - b.orden || a.id - b.id,
  );
  let y: number = MARGEN;
  for (const pool of pools) {
    const lanes = diagrama.lanes
      .filter((lane) => lane.poolId === pool.id)
      .sort((a, b) => a.orden - b.orden || a.id - b.id);
    let yLane: number = y;
    if (!pool.cajaNegra) {
      for (const lane of lanes) {
        const actividades = diagrama.actividades.filter((actividad) => actividad.laneId === lane.id);
        const gateways = diagrama.gateways.filter((gateway) => gateway.laneId === lane.id);
        const eventos = diagrama.eventos.filter((evento) => evento.laneId === lane.id);
        const posicionesY: number[] = [...actividades, ...gateways, ...eventos].map((nodo) => nodo.posicionY);
        const minY: number = posicionesY.length ? Math.min(...posicionesY) : 0;
        const alto: number = posicionesY.length
          ? RELLENO_ARRIBA + Math.max(...posicionesY) - minY + ALTO_TAREA + RELLENO_ABAJO
          : ALTO_VACIO;
        const yDe = (posicionY: number): number => yLane + RELLENO_ARRIBA + ALTO_TAREA / 2 + posicionY - minY;

        lienzo.lanes.push({
          id: lane.id,
          nombre: lane.nombre,
          rol: lane.rolProcesoNombre,
          x: MARGEN + FRANJA,
          y: yLane,
          ancho: anchoPool - FRANJA,
          alto,
          etiqueta: recortar(lane.nombre, caracteresEnFranja(alto)),
        });
        for (const actividad of actividades) {
          const cx: number = xDe(actividad.posicionX);
          const cy: number = yDe(actividad.posicionY);
          const lineas: string[] = partirTexto(actividad.nombre, 18, 3);
          lienzo.actividades.push({
            id: actividad.id,
            nombre: actividad.nombre,
            x: cx - ANCHO_TAREA / 2,
            y: cy - ALTO_TAREA / 2,
            ancho: ANCHO_TAREA,
            alto: ALTO_TAREA,
            texto: { lineas, x: cx, y: cy - ((lineas.length - 1) * ALTO_LINEA) / 2 + 4 },
          });
          ubicados.set(actividad.id, { cx, cy, medioAncho: ANCHO_TAREA / 2, medioAlto: ALTO_TAREA / 2, esGateway: false });
        }
        for (const gateway of gateways) {
          const cx: number = xDe(gateway.posicionX);
          const cy: number = yDe(gateway.posicionY);
          const medio: number = LADO_GATEWAY / 2;
          lienzo.gateways.push({
            id: gateway.id,
            nombre: gateway.nombre,
            tipo: gateway.tipoGateway,
            puntos: `${cx},${cy - medio} ${cx + medio},${cy} ${cx},${cy + medio} ${cx - medio},${cy}`,
            marca: marcaDeGateway(gateway.tipoGateway, cx, cy),
            // El nombre va abajo a la izquierda del rombo: la salida de abajo no lo cruza
            texto: { lineas: partirTexto(gateway.nombre, 22, 2), x: cx - 6, y: cy + medio + 12 },
          });
          ubicados.set(gateway.id, { cx, cy, medioAncho: medio, medioAlto: medio, esGateway: true });
        }
        for (const evento of eventos) {
          const cx: number = xDe(evento.posicionX);
          const cy: number = yDe(evento.posicionY);
          lienzo.eventos.push(dibujarEvento(evento, cx, cy));
          ubicados.set(evento.id, {
            cx,
            cy,
            medioAncho: RADIO_EVENTO,
            medioAlto: RADIO_EVENTO,
            esGateway: false,
          });
        }
        yLane += alto;
      }
    }
    // Una caja negra no muestra su interior; un pool sin lanes deja el espacio de uno vacio
    const alto: number = pool.cajaNegra ? ALTO_CAJA_NEGRA : lanes.length ? yLane - y : ALTO_VACIO;
    // El tipo solo se muestra si no repite el nombre, como en un pool "Customer" de tipo cliente
    const tipo: string = NOMBRE_PARTICIPANTE[pool.tipoParticipante];
    const repiteNombre: boolean = pool.nombre.toLowerCase() === tipo;
    const dibujo: PoolDibujo = {
      id: pool.id,
      nombre: pool.nombre,
      tipo: repiteNombre ? '' : tipo,
      titulo: repiteNombre ? pool.nombre : `${pool.nombre} (${tipo})`,
      cajaNegra: pool.cajaNegra,
      sinLanes: !pool.cajaNegra && lanes.length === 0,
      x: MARGEN,
      y,
      ancho: anchoPool,
      alto,
      etiqueta: recortar(pool.nombre, caracteresEnFranja(alto)),
    };
    lienzo.pools.push(dibujo);
    cajasDePool.set(pool.id, dibujo);
    y += alto + SEPARACION;
  }
  lienzo.alto = Math.max(y - SEPARACION + MARGEN, 2 * MARGEN);
  lienzo.anchoMinimo = Math.min(lienzo.ancho, 640);

  for (const arco of diagrama.arcos) {
    const origen: NodoUbicado | undefined = ubicados.get(arco.origenId);
    const destino: NodoUbicado | undefined = ubicados.get(arco.destinoId);
    if (origen && destino) {
      lienzo.arcos.push({ id: arco.id, etiqueta: arco.etiqueta, ...trazarArco(origen, destino) });
    }
  }

  lienzo.mensajes = dibujarMensajes(diagrama, cajasDePool, ubicados, anchoPool);
  return lienzo;
}

/**
 * Cada mensaje sale del nodo al que esta anclado y entra en el nodo que lo espera, cruzando el hueco entre los pools.
 * Un extremo en una caja negra no tiene nodo, y entonces ese lado arranca (o termina) en el borde del pool: los
 * mensajes que no tienen ningun nodo se reparten a lo ancho, como antes, para que no se monten unos sobre otros.
 * Cada uno lleva un numero que remite a la lista de mensajes debajo del dibujo.
 */
function dibujarMensajes(
  diagrama: Diagrama,
  cajas: Map<number, PoolDibujo>,
  ubicados: Map<number, NodoUbicado>,
  anchoPool: number,
): MensajeDibujo[] {
  const mensajes: Mensaje[] = [...diagrama.mensajes]
    .filter((mensaje) => {
      const origen = cajas.get(mensaje.poolOrigenId);
      const destino = cajas.get(mensaje.poolDestinoId);
      return origen !== undefined && destino !== undefined && origen !== destino;
    })
    .sort((a, b) => a.id - b.id);

  // Las columnas solo hacen falta para los mensajes sin ningun nodo anclado, y se reparten entre ellos
  const sueltos: number[] = mensajes
    .filter((mensaje) => !ubicados.has(mensaje.nodoOrigenId ?? -1) && !ubicados.has(mensaje.nodoDestinoId ?? -1))
    .map((mensaje) => mensaje.id);
  const inicio: number = MARGEN + 2 * FRANJA + 24;
  const paso: number = (anchoPool - 2 * FRANJA - 48) / Math.max(sueltos.length, 1);

  const dibujos: MensajeDibujo[] = mensajes.map((mensaje: Mensaje, indice: number) => {
    const origen = cajas.get(mensaje.poolOrigenId) as PoolDibujo;
    const destino = cajas.get(mensaje.poolDestinoId) as PoolDibujo;
    const baja: boolean = destino.y > origen.y;
    const hacia: number = baja ? 1 : -1;
    const columna: number = inicio + paso * (sueltos.indexOf(mensaje.id) + 0.5);
    const desde: NodoUbicado | undefined = ubicados.get(mensaje.nodoOrigenId ?? -1);
    const hasta: NodoUbicado | undefined = ubicados.get(mensaje.nodoDestinoId ?? -1);

    // El hueco que hay justo despues del pool de origen: por ahi corre el tramo horizontal y va el numero
    const yHueco: number = baja
      ? origen.y + origen.alto + SEPARACION / 2
      : origen.y - SEPARACION / 2;
    const x1: number = desde ? desde.cx : hasta ? hasta.cx : columna;
    const x2: number = hasta ? hasta.cx : x1;
    const y1: number = desde ? desde.cy + hacia * desde.medioAlto : baja ? origen.y + origen.alto : origen.y;
    const y2: number = hasta ? hasta.cy - hacia * hasta.medioAlto : baja ? destino.y : destino.y + destino.alto;

    return {
      id: mensaje.id,
      numero: indice + 1,
      nombre: mensaje.nombre,
      contenido: mensaje.contenido,
      origen: origen.nombre,
      destino: destino.nombre,
      nodoOrigenId: mensaje.nodoOrigenId,
      nodoDestinoId: mensaje.nodoDestinoId,
      desde: nombreDeNodo(diagrama, mensaje.nodoOrigenId),
      hasta: nombreDeNodo(diagrama, mensaje.nodoDestinoId),
      claves: diagrama.correlaciones
        .filter((correlacion) => correlacion.mensajeId === mensaje.id)
        .map((correlacion) => correlacion.criterio),
      ruta: x1 === x2 ? `M ${x1} ${y1} V ${y2}` : `M ${x1} ${y1} V ${yHueco} H ${x2} V ${y2}`,
      salidaX: x1,
      salidaY: y1,
      numeroX: (x1 + x2) / 2,
      numeroY: yHueco,
    };
  });
  return separarMensajesQueSeMontan(dibujos);
}

/**
 * Dos mensajes que salen del mismo nodo al mismo nodo caerian encima uno del otro. Se abren en abanico moviendo su
 * numero, que es lo que hay que poder leer; la ruta se queda donde esta, porque nace y muere en un nodo concreto.
 */
function separarMensajesQueSeMontan(dibujos: MensajeDibujo[]): MensajeDibujo[] {
  const porColumna = new Map<string, MensajeDibujo[]>();
  for (const dibujo of dibujos) {
    const clave: string = `${Math.round(dibujo.numeroX)}|${Math.round(dibujo.numeroY)}`;
    porColumna.set(clave, [...(porColumna.get(clave) ?? []), dibujo]);
  }
  for (const juntos of porColumna.values()) {
    if (juntos.length > 1) {
      juntos.forEach((dibujo: MensajeDibujo, indice: number) => {
        dibujo.numeroX += (indice - (juntos.length - 1) / 2) * 24;
      });
    }
  }
  return dibujos;
}

/** El nombre de una tarea, un gateway o un evento por su id; los tres comparten numeracion. */
function nombreDeNodo(diagrama: Diagrama, id: number | null): string | null {
  if (id === null) {
    return null;
  }
  const nodo = [...diagrama.actividades, ...diagrama.gateways, ...diagrama.eventos].find(
    (candidato) => candidato.id === id,
  );
  return nodo?.nombre ?? null;
}

/** Un evento ya ubicado: el circulo que le toca por su tipo, su sobre si lleva mensaje y su nombre debajo. */
function dibujarEvento(evento: Evento, cx: number, cy: number): EventoDibujo {
  const conMensaje: boolean = evento.tipoEvento !== 'INICIO' && evento.tipoEvento !== 'FIN';
  return {
    id: evento.id,
    nombre: evento.nombre,
    tipo: evento.tipoEvento,
    titulo: `${evento.nombre} · ${NOMBRE_EVENTO[evento.tipoEvento]}`,
    clase: evento.tipoEvento === 'MENSAJE_INTERMEDIO' ? 'intermedio' : terminaElProceso(evento.tipoEvento) ? 'fin' : 'inicio',
    cx,
    cy,
    radio: RADIO_EVENTO,
    doble: evento.tipoEvento === 'MENSAJE_INTERMEDIO',
    grueso: terminaElProceso(evento.tipoEvento),
    sobre: conMensaje ? sobre(cx, cy) : null,
    // En BPMN el sobre relleno es el que manda el mensaje, y el hueco el que lo espera
    sobreRelleno: evento.tipoEvento === 'MENSAJE_FIN',
    // El nombre va debajo del circulo, centrado: es lo unico que no cabe dentro
    texto: { lineas: partirTexto(evento.nombre, 20, 2), x: cx, y: cy + RADIO_EVENTO + 14 },
  };
}

/** Un sobre dentro del circulo: el rectangulo y la solapa. */
function sobre(cx: number, cy: number): string {
  return `M ${cx - 8} ${cy - 5} h 16 v 10 h -16 z M ${cx - 8} ${cy - 5} L ${cx} ${cy + 1} L ${cx + 8} ${cy - 5}`;
}

/** Ruta en angulo recto de un nodo a otro, y donde va su etiqueta. */
function trazarArco(origen: NodoUbicado, destino: NodoUbicado): Omit<ArcoDibujo, 'id' | 'etiqueta'> {
  const dx: number = destino.cx - origen.cx;
  const dy: number = destino.cy - origen.cy;
  const haciaX: number = dx >= 0 ? 1 : -1;
  const haciaY: number = dy >= 0 ? 1 : -1;

  // Uno encima del otro: sale por abajo (o por arriba) y entra por el borde opuesto
  if (Math.abs(dx) < origen.medioAncho + destino.medioAncho + 16) {
    const y1: number = origen.cy + haciaY * origen.medioAlto;
    const y2: number = destino.cy - haciaY * destino.medioAlto;
    const yMedio: number = (y1 + y2) / 2;
    return {
      ruta: `M ${origen.cx} ${y1} V ${yMedio} H ${destino.cx} V ${y2}`,
      etiquetaX: origen.cx + 6,
      etiquetaY: y1 + haciaY * ALTO_LINEA,
      anclaje: 'start',
    };
  }
  const x2: number = destino.cx - haciaX * destino.medioAncho;
  if (Math.abs(dy) < 2) {
    const x1: number = origen.cx + haciaX * origen.medioAncho;
    return { ruta: `M ${x1} ${origen.cy} H ${x2}`, etiquetaX: (x1 + x2) / 2, etiquetaY: origen.cy - 6, anclaje: 'middle' };
  }
  // De un gateway, cada salida parte de su vertice de arriba o de abajo y dobla hacia el destino
  if (origen.esGateway) {
    const y1: number = origen.cy + haciaY * origen.medioAlto;
    return {
      ruta: `M ${origen.cx} ${y1} V ${destino.cy} H ${x2}`,
      etiquetaX: origen.cx + 8,
      etiquetaY: destino.cy - 6,
      anclaje: 'start',
    };
  }
  const x1: number = origen.cx + haciaX * origen.medioAncho;
  const xMedio: number = (x1 + x2) / 2;
  return {
    ruta: `M ${x1} ${origen.cy} H ${xMedio} V ${destino.cy} H ${x2}`,
    etiquetaX: (x1 + xMedio) / 2,
    etiquetaY: origen.cy - 6,
    anclaje: 'middle',
  };
}

/** El simbolo dentro del rombo: una X si es exclusivo, un + si es paralelo y un circulo si es inclusivo. */
function marcaDeGateway(tipo: TipoGateway, cx: number, cy: number): string {
  if (tipo === 'PARALELO') {
    return `M ${cx} ${cy - 11} V ${cy + 11} M ${cx - 11} ${cy} H ${cx + 11}`;
  }
  if (tipo === 'INCLUSIVO') {
    return `M ${cx - 10} ${cy} a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0`;
  }
  return `M ${cx - 8} ${cy - 8} L ${cx + 8} ${cy + 8} M ${cx + 8} ${cy - 8} L ${cx - 8} ${cy + 8}`;
}

/** Parte un texto en lineas de hasta maxCaracteres, sin cortar palabras; si no cabe, la ultima termina en "…". */
export function partirTexto(texto: string, maxCaracteres: number, maxLineas: number): string[] {
  const lineas: string[] = [];
  let actual: string = '';
  for (const palabra of texto.trim().split(/\s+/)) {
    const conPalabra: string = actual ? `${actual} ${palabra}` : palabra;
    if (conPalabra.length <= maxCaracteres) {
      actual = conPalabra;
    } else {
      if (actual) {
        lineas.push(actual);
      }
      actual = recortar(palabra, maxCaracteres);
    }
  }
  if (actual) {
    lineas.push(actual);
  }
  if (lineas.length > maxLineas) {
    const visibles: string[] = lineas.slice(0, maxLineas);
    visibles[maxLineas - 1] = recortar(`${visibles[maxLineas - 1]}…`, maxCaracteres);
    return visibles;
  }
  return lineas;
}

function recortar(texto: string, maxCaracteres: number): string {
  return texto.length <= maxCaracteres ? texto : `${texto.slice(0, maxCaracteres - 1)}…`;
}

/** Cuantas letras caben en una franja vertical de ese alto, con el texto girado. */
function caracteresEnFranja(alto: number): number {
  return Math.max(4, Math.floor((alto - 16) / 7));
}
