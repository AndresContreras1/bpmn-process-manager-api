import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { FiltrosProceso } from '../models/proceso.model';
import { direccionPorDefecto, filtrosDesdeUrl, filtrosEnUrl, filtrosIniciales } from './filtros-en-url';

function leer(parametros: Record<string, string>): FiltrosProceso {
  return filtrosDesdeUrl(convertToParamMap(parametros));
}

describe('filtrosEnUrl y filtrosDesdeUrl', () => {
  it('la lista sin filtros no pone nada en la direccion', () => {
    expect(filtrosEnUrl(filtrosIniciales())).toEqual({});
    expect(leer({})).toEqual(filtrosIniciales());
  });

  it('cada filtro va y vuelve igual, que es lo que deja compartir una lista filtrada', () => {
    const filtros: FiltrosProceso = {
      nombre: 'credit approval',
      estado: 'PUBLICADO',
      categoria: 'Sales',
      carpetaId: 12,
      duenoId: 4,
      etiqueta: 'gdpr',
      clasificacion: 'CONFIDENCIAL',
      revisionAntesDe: '2027-03-01',
      orden: 'proximaRevision',
      direccion: 'desc',
      pagina: 2,
    };

    const url = filtrosEnUrl(filtros);

    expect(url).toEqual({
      nombre: 'credit approval',
      estado: 'PUBLICADO',
      categoria: 'Sales',
      carpeta: '12',
      dueno: '4',
      etiqueta: 'gdpr',
      clasificacion: 'CONFIDENCIAL',
      revision: '2027-03-01',
      orden: 'proximaRevision,desc',
      pagina: '3',
    });
    expect(leer(url)).toEqual(filtros);
  });

  it('el orden de siempre no se escribe, y uno distinto solo en su direccion tambien se escribe', () => {
    expect(filtrosEnUrl({ ...filtrosIniciales(), orden: 'fechaModificacion', direccion: 'asc' })).toEqual({
      orden: 'fechaModificacion,asc',
    });
    expect(filtrosEnUrl({ ...filtrosIniciales(), orden: 'nombre', direccion: 'desc' })).toEqual({
      orden: 'nombre,desc',
    });
  });

  it('el nombre viaja como se escribio, pero en blanco no viaja', () => {
    expect(filtrosEnUrl({ ...filtrosIniciales(), nombre: 'credit ' })).toEqual({ nombre: 'credit ' });
    expect(filtrosEnUrl({ ...filtrosIniciales(), nombre: '   ' })).toEqual({});
  });

  it('la primera pagina no se escribe y la segunda es pagina=2', () => {
    expect(filtrosEnUrl({ ...filtrosIniciales(), pagina: 0 })).toEqual({});
    expect(filtrosEnUrl({ ...filtrosIniciales(), pagina: 1 })).toEqual({ pagina: '2' });
    expect(leer({ pagina: '2' }).pagina).toBe(1);
  });
});

describe('filtrosDesdeUrl con una direccion escrita a mano', () => {
  it('un estado, una clasificacion o un orden que no existen valen lo de siempre', () => {
    const filtros = leer({ estado: 'ARCHIVADO', clasificacion: 'SECRETA', orden: 'precio,asc' });

    expect(filtros.estado).toBe('');
    expect(filtros.clasificacion).toBe('');
    expect(filtros.orden).toBe('fechaModificacion');
    expect(filtros.direccion).toBe('desc');
  });

  it('un orden sin direccion, o con una que no existe, toma la de su campo', () => {
    expect(leer({ orden: 'nombre' })).toEqual(expect.objectContaining({ orden: 'nombre', direccion: 'asc' }));
    expect(leer({ orden: 'clasificacion,arriba' })).toEqual(
      expect.objectContaining({ orden: 'clasificacion', direccion: 'asc' }),
    );
    expect(leer({ orden: 'fechaModificacion' }).direccion).toBe('desc');
  });

  it('un id que no es un numero entero positivo no filtra', () => {
    expect(leer({ carpeta: 'abc' }).carpetaId).toBeNull();
    expect(leer({ carpeta: '0' }).carpetaId).toBeNull();
    expect(leer({ carpeta: '-3' }).carpetaId).toBeNull();
    expect(leer({ carpeta: '1.5' }).carpetaId).toBeNull();
    expect(leer({ dueno: '07' }).duenoId).toBeNull();
    expect(leer({ carpeta: '9' }).carpetaId).toBe(9);
  });

  it('un dia mal escrito no filtra', () => {
    expect(leer({ revision: 'manana' }).revisionAntesDe).toBe('');
    expect(leer({ revision: '2027-3-1' }).revisionAntesDe).toBe('');
    expect(leer({ revision: '2027-03-01' }).revisionAntesDe).toBe('2027-03-01');
  });

  it('una pagina que no es un numero, o cero, es la primera', () => {
    expect(leer({ pagina: 'x' }).pagina).toBe(0);
    expect(leer({ pagina: '0' }).pagina).toBe(0);
    expect(leer({ pagina: '-2' }).pagina).toBe(0);
  });
});

describe('direccionPorDefecto', () => {
  it('las fechas de la mas reciente, lo demas de la A a la Z', () => {
    expect(direccionPorDefecto('fechaModificacion')).toBe('desc');
    expect(direccionPorDefecto('proximaRevision')).toBe('asc');
    expect(direccionPorDefecto('nombre')).toBe('asc');
  });
});
