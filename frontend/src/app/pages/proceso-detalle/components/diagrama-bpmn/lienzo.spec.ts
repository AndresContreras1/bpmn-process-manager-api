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
