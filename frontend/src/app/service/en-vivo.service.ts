import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { ConexionEnVivo, FuenteDeEventos } from '../bpmn/conexion-en-vivo';
import { Bloqueo, EventoEnVivo } from '../models/en-vivo.model';

/** Quien esta en un proceso, quien lo edita y lo que va pasando. */
@Injectable({ providedIn: 'root' })
export class EnVivoService {
  private readonly http: HttpClient = inject(HttpClient);
  private readonly api: string = `${environment.apiUrl}/api/v1/procesos`;

  /** Los eventos de un proceso mientras alguien este suscrito; la conexion se repone sola. */
  eventos(procesoId: number): Observable<EventoEnVivo> {
    return new Observable<EventoEnVivo>((suscriptor) => {
      const conexion = new ConexionEnVivo(
        {
          abrir: () =>
            new EventSource(`${this.api}/${procesoId}/en-vivo`, { withCredentials: true }) as FuenteDeEventos,
          esperar: (accion, ms) => setTimeout(accion, ms),
          cancelar: (marca) => clearTimeout(marca as ReturnType<typeof setTimeout>),
          azar: () => Math.random(),
        },
        (evento) => suscriptor.next(evento),
      );
      conexion.iniciar();
      return () => conexion.detener();
    });
  }

  estado(procesoId: number): Observable<Bloqueo> {
    return this.http.get<Bloqueo>(`${this.api}/${procesoId}/bloqueo`);
  }

  adquirir(procesoId: number): Observable<Bloqueo> {
    return this.http.post<Bloqueo>(`${this.api}/${procesoId}/bloqueo`, null);
  }

  latido(procesoId: number): Observable<Bloqueo> {
    return this.http.post<Bloqueo>(`${this.api}/${procesoId}/bloqueo/latido`, null);
  }

  soltar(procesoId: number): Observable<Bloqueo> {
    return this.http.post<Bloqueo>(`${this.api}/${procesoId}/bloqueo/soltar`, null);
  }

  tomarControl(procesoId: number): Observable<Bloqueo> {
    return this.http.post<Bloqueo>(`${this.api}/${procesoId}/bloqueo/tomar-control`, null);
  }
}
