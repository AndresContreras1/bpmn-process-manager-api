import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * F14: el token de un enlace del correo, que va despues de # (/restablecer-clave#token=...): el navegador no manda
 * esa parte a ningun servidor. Se lee una vez y se quita de la direccion, para que no quede en el historial ni en una
 * captura de pantalla. Se llama desde el constructor o un campo de una pantalla.
 */
export function tokenDelEnlace(): string | null {
  const documento: Document = inject(DOCUMENT);
  const fragmento: string | null = inject(ActivatedRoute).snapshot.fragment;
  const token: string | null = new URLSearchParams(fragmento ?? '').get('token');
  if (token && documento.defaultView?.history) {
    const { pathname, search } = documento.location;
    documento.defaultView.history.replaceState(documento.defaultView.history.state, '', pathname + search);
  }
  return token;
}
