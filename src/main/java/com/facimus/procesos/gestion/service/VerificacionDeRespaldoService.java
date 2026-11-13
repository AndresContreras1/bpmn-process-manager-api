package com.facimus.procesos.gestion.service;

import java.util.List;

/**
 * PR 67: lo que se comprueba de una base restaurada antes de fiarse de ella, con el mismo codigo que la escribio: la
 * cadena de la bitacora de cada tienda, contra su ultima ancla, y la huella de cada version publicada, rehecha desde
 * el diagrama que guarda. Las filas de cada tabla las compara el simulacro (ops/respaldo), que las conto al respaldar.
 *
 * <p>Cruza tiendas a proposito: quien la llama lo hace como sistema, igual que la purga o el ancla diaria.</p>
 */
public interface VerificacionDeRespaldoService {

    Informe verificar();

    /** Lo que se reviso y lo que no cuadro. */
    record Informe(int tiendas, long registrosDeBitacora, int versiones, List<Fallo> fallos) {

        /** Sin fallos, la base restaurada dice lo mismo que la que se respaldo. */
        public boolean correcta() {
            return fallos.isEmpty();
        }
    }

    /** Algo de una tienda que no cuadra, dicho para quien opera. */
    record Fallo(Long empresaId, String motivo) {
    }
}
