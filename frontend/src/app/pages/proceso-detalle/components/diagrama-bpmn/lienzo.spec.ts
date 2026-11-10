import { Actividad, Diagrama, Lane, Pool } from '../../../../models/diagrama.model';
import { dibujarDiagrama, partirTexto } from './lienzo';

function pool(id: number, tipoParticipante: Pool['tipoParticipante'], orden: number, cajaNegra = false): Pool {
  return {
    id,
    nombre: `Pool ${id}`,
    tipoParticipante,
    cajaNegra,
    integracion: 'NINGUNA',
    orden,
    procesoId: 1,
    version: 0,
  };
}

function lane(id: number, poolId: number, orden: number): Lane {
  return {
    id,
    nombre: `Lane ${id}`,
    orden,
    poolId,
    rolProcesoId: 1,
    rolProcesoNombre: 'Sales',
    version: 0,
  };
}

function actividad(id: number, laneId: number, posicionX: number, posicionY: number): Actividad {
  return {
    id,
    nombre: `Task ${id}`,
    descripcion: null,
    tipoActividad: 'USUARIO',
    posicionX,
    posicionY,
    laneId,
    version: 0,
  };
}

function diagrama(parte: Partial<Diagrama>): Diagrama {
  return {
    proceso: {} as Diagrama['proceso'],
    compartido: false,
    pools: [],
    lanes: [],
    actividades: [],
    gateways: [],
    eventos: [],
    arcos: [],
    mensajes: [],
    correlaciones: [],
    ...parte,
  };
}

describe('partirTexto', () => {
  it('parte en lineas sin cortar palabras', () => {
    expect(partirTexto('Check the payment of the order', 12, 3)).toEqual(['Check the', 'payment of', 'the order']);
  });

  it('corta con "…" una palabra que no cabe y la ultima linea que sobra', () => {
    expect(partirTexto('Supercalifragilistic', 8, 2)).toEqual(['Superca…']);
    expect(partirTexto('one two three four five six', 7, 2)).toEqual(['one two', 'three…']);
  });

  it('no deja lineas vacias por los espacios de mas', () => {
    expect(partirTexto('  Ship   it  ', 20, 2)).toEqual(['Ship it']);
  });
});

describe('dibujarDiagrama', () => {
  it('pone la tienda entre los clientes y los demas, y apila sus lanes sin que se pisen', () => {
    const lienzo = dibujarDiagrama(
      diagrama({
        pools: [pool(3, 'PROVEEDOR', 0, true), pool(1, 'EMPRESA', 0), pool(2, 'CLIENTE', 0, true)],
        lanes: [lane(10, 1, 0), lane(11, 1, 1)],
        actividades: [actividad(100, 10, 0, 0), actividad(101, 11, 200, 40)],
      }),
    );

    expect(lienzo.pools.map((dibujado) => dibujado.id)).toEqual([2, 1, 3]);
    const [arriba, abajo] = lienzo.lanes;
    expect(abajo.y).toBe(arriba.y + arriba.alto);
  });

  it('da a todas las lanes la misma x para la misma posicionX, y deja cada nodo dentro de su lane', () => {
    const lienzo = dibujarDiagrama(
      diagrama({
        pools: [pool(1, 'EMPRESA', 0)],
        lanes: [lane(10, 1, 0), lane(11, 1, 1)],
        actividades: [actividad(100, 10, 150, 0), actividad(101, 11, 150, 300), actividad(102, 11, 450, 0)],
      }),
    );

    const [primera, segunda, tercera] = lienzo.actividades;
    expect(primera.cx).toBe(segunda.cx);
    expect(tercera.cx - segunda.cx).toBe(300);
    expect(primera.cx - lienzo.origenX).toBe(150);
    for (const dibujada of lienzo.actividades) {
      const suLane = lienzo.lanes.find(
        (candidata) => dibujada.y >= candidata.y && dibujada.y + dibujada.alto <= candidata.y + candidata.alto,
      );
      expect(suLane).toBeDefined();
    }
  });

  it('no baja del ancho minimo aunque el diagrama este vacio', () => {
    const lienzo = dibujarDiagrama(diagrama({ pools: [pool(1, 'EMPRESA', 0)], lanes: [lane(10, 1, 0)] }));

    expect(lienzo.ancho).toBeGreaterThanOrEqual(720);
    expect(lienzo.lanes[0].alto).toBeGreaterThan(0);
  });
});

/**
 * PR 52: el diagrama de referencia que la API recalcula en Java (DisposicionBpmnTest) con los mismos numeros. Si uno
 * de los dos cambia la cuenta, falla su prueba: la geometria BPMN DI y el dibujo de la web no pueden separarse.
 */
describe('el diagrama de referencia de la geometria BPMN DI', () => {
  const lienzo = dibujarDiagrama(
    diagrama({
      pools: [pool(1, 'EMPRESA', 0), pool(2, 'CLIENTE', 0, true)],
      lanes: [lane(10, 1, 0), lane(11, 1, 1)],
      actividades: [actividad(101, 10, 180, 0)],
      gateways: [
        { id: 102, nombre: 'Paid?', tipoGateway: 'EXCLUSIVO', posicionX: 360, posicionY: 40, laneId: 11, version: 0 },
      ],
      eventos: [
        { id: 100, nombre: 'Order received', tipoEvento: 'INICIO', posicionX: 0, posicionY: 0, laneId: 10, version: 0 },
        { id: 103, nombre: 'End', tipoEvento: 'FIN', posicionX: 540, posicionY: 40, laneId: 11, version: 0 },
      ],
      arcos: [
        {
          id: 1,
          etiqueta: null,
          condicion: null,
          porDefecto: false,
          orden: 0,
          origenId: 100,
          destinoId: 101,
          poolId: 1,
          version: 0,
        },
        {
          id: 2,
          etiqueta: null,
          condicion: null,
          porDefecto: false,
          orden: 0,
          origenId: 101,
          destinoId: 102,
          poolId: 1,
          version: 0,
        },
        {
          id: 3,
          etiqueta: 'Yes',
          condicion: null,
          porDefecto: false,
          orden: 0,
          origenId: 102,
          destinoId: 103,
          poolId: 1,
          version: 0,
        },
      ] as Diagrama['arcos'],
      mensajes: [
        {
          id: 200,
          nombre: 'Order',
          contenido: '',
          poolOrigenId: 2,
          poolDestinoId: 1,
          nodoOrigenId: null,
          nodoDestinoId: 100,
        },
      ] as unknown as Diagrama['mensajes'],
    }),
  );

  it('ubica los pools, las lanes y los nodos donde los pone la API', () => {
    expect(lienzo.pools.map((p) => [p.id, p.x, p.y, p.ancho, p.alto])).toEqual([
      [2, 16, 16, 824, 56],
      [1, 16, 120, 824, 240],
    ]);
    expect(lienzo.lanes.map((l) => [l.id, l.x, l.y, l.ancho, l.alto])).toEqual([
      [10, 46, 120, 794, 120],
      [11, 46, 240, 794, 120],
    ]);
    expect(lienzo.actividades.map((a) => [a.id, a.x, a.y, a.ancho, a.alto])).toEqual([[101, 304, 144, 128, 56]]);
    expect(lienzo.gateways.map((g) => [g.id, g.cx, g.cy])).toEqual([[102, 548, 292]]);
    expect(lienzo.eventos.map((e) => [e.id, e.cx, e.cy])).toEqual([
      [100, 188, 172],
      [103, 728, 292],
    ]);
  });

  it('traza los flujos y el mensaje por los mismos puntos', () => {
    expect(lienzo.arcos.map((a) => a.ruta)).toEqual([
      'M 206 172 H 304',
      'M 432 172 H 477.5 V 292 H 523',
      'M 573 292 H 710',
    ]);
    expect(lienzo.arcos[2].etiquetaX).toBe(641.5);
    expect(lienzo.arcos[2].etiquetaY).toBe(286);
    expect(lienzo.mensajes.map((m) => m.ruta)).toEqual(['M 188 72 V 154']);
  });
});
