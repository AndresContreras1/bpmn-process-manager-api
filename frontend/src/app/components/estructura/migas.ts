import { ActivatedRouteSnapshot } from '@angular/router';

/** Un paso de las migas: lo que dice y, salvo el ultimo, a donde lleva. Una ruta puede nombrar sus parametros (:id). */
export interface Miga {
  etiqueta: string;
  ruta?: string;
}

/**
 * Las migas de la pantalla abierta: las que declara su ruta en data.migas, con los parametros de la ruta puestos en
 * sus enlaces. La pantalla mas profunda es la que manda.
 */
export function migasDe(raiz: ActivatedRouteSnapshot): Miga[] {
  let ruta: ActivatedRouteSnapshot = raiz;
  while (ruta.firstChild) {
    ruta = ruta.firstChild;
  }
  const declaradas: Miga[] = (ruta.data['migas'] as Miga[] | undefined) ?? [];
  return declaradas.map((miga: Miga) => ({
    etiqueta: miga.etiqueta,
    ruta: miga.ruta?.replace(/:(\w+)/g, (_todo: string, parametro: string) => ruta.paramMap.get(parametro) ?? ''),
  }));
}
