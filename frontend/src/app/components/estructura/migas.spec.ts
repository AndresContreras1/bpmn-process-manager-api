import { ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';

import { migasDe } from './migas';

function ruta(data: Record<string, unknown>, params: Record<string, string>, hija?: ActivatedRouteSnapshot) {
  return { data, paramMap: convertToParamMap(params), firstChild: hija ?? null } as unknown as ActivatedRouteSnapshot;
}

describe('migasDe', () => {
  it('toma las migas de la pantalla mas profunda y pone sus parametros en los enlaces', () => {
    const hoja = ruta(
      {
        migas: [
          { etiqueta: 'Processes', ruta: '/procesos' },
          { etiqueta: 'Process', ruta: '/procesos/:id' },
          { etiqueta: 'Edit' },
        ],
      },
      { id: '42' },
    );
    const raiz = ruta({ migas: [{ etiqueta: 'No se usa' }] }, {}, hoja);

    expect(migasDe(raiz)).toEqual([
      { etiqueta: 'Processes', ruta: '/procesos' },
      { etiqueta: 'Process', ruta: '/procesos/42' },
      { etiqueta: 'Edit', ruta: undefined },
    ]);
  });

  it('no tiene migas una pantalla que no las declara', () => {
    expect(migasDe(ruta({}, {}))).toEqual([]);
  });
});
