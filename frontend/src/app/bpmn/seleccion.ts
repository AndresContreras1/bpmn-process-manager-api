/**
 * Si un cambio de seleccion de bpmn-js es alguien eligiendo algo. Al importar un diagrama, bpmn-js vacia una
 * seleccion que ya estaba vacia: eso no es elegir nada, y avisarlo borraria lo que la pagina eligio antes de que el
 * dibujo llegara (un elemento de la direccion, el hilo de un aviso). Ir de algo a nada si lo es: es soltarlo.
 */
export function esEleccion(antes: readonly unknown[] | null | undefined, ahora: readonly unknown[]): boolean {
  return (antes?.length ?? 0) > 0 || ahora.length > 0;
}
