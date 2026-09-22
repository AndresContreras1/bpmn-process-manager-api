import { Diagrama, Mensaje, NOMBRE_PARTICIPANTE, Pool, TipoGateway, TipoParticipante } from '../../../../models/diagrama.model';

// Medidas del dibujo, en las mismas unidades de posicionX y posicionY
const MARGEN = 16;
const FRANJA = 30; // ancho de la franja con el nombre de un pool o de un lane
const ANCHO_TAREA = 128;
const ALTO_TAREA = 56;
const LADO_GATEWAY = 50;
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
  claves: string[];
  x: number;
  y1: number;
  y2: number;
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
  const posicionesX: number[] = [...diagrama.actividades, ...diagrama.gateways].map((nodo) => nodo.posicionX);
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
        const posicionesY: number[] = [...actividades, ...gateways].map((nodo) => nodo.posicionY);
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

  lienzo.mensajes = dibujarMensajes(diagrama, cajasDePool, anchoPool);
  return lienzo;
}

/**
 * Los mensajes se reparten a lo ancho del lienzo, uno por columna, y bajan (o suben) del borde del pool de origen al
 * del destino. Cada uno lleva un numero que remite a la lista de mensajes debajo del dibujo.
 */
function dibujarMensajes(diagrama: Diagrama, cajas: Map<number, PoolDibujo>, anchoPool: number): MensajeDibujo[] {
  const mensajes: Mensaje[] = [...diagrama.mensajes]
    .filter((mensaje) => {
      const origen = cajas.get(mensaje.poolOrigenId);
      const destino = cajas.get(mensaje.poolDestinoId);
      return origen !== undefined && destino !== undefined && origen !== destino;
    })
    .sort((a, b) => a.id - b.id);
  const inicio: number = MARGEN + 2 * FRANJA + 24;
  const paso: number = (anchoPool - 2 * FRANJA - 48) / Math.max(mensajes.length, 1);

  return mensajes.map((mensaje: Mensaje, indice: number) => {
    const origen = cajas.get(mensaje.poolOrigenId) as PoolDibujo;
    const destino = cajas.get(mensaje.poolDestinoId) as PoolDibujo;
    const baja: boolean = destino.y > origen.y;
    const y1: number = baja ? origen.y + origen.alto : origen.y;
    return {
      id: mensaje.id,
      numero: indice + 1,
      nombre: mensaje.nombre,
      contenido: mensaje.contenido,
      origen: origen.nombre,
      destino: destino.nombre,
      claves: diagrama.correlaciones
        .filter((correlacion) => correlacion.mensajeId === mensaje.id)
        .map((correlacion) => correlacion.criterio),
      x: inicio + paso * (indice + 0.5),
      y1,
      y2: baja ? destino.y : destino.y + destino.alto,
      numeroY: y1 + (baja ? SEPARACION : -SEPARACION) / 2,
    };
  });
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
