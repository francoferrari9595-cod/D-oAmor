# KOMBO ARENA v125 — corrección de compilación y confirmación de ingreso

Base acumulativa: v124-portal-seleccionable.

## Cambios
- `RaceGame.kt`: se movió la lectura de `carSel` debajo de su declaración Compose. Corrige los dos `Unresolved reference 'carSel'` de la compilación sin cambiar la selección visual ni el color equipado.
- `CoupleViewModel.kt`: el ingreso a una sala pública ahora espera la confirmación real de Firestore. Si no llega dentro de 6 segundos, informa el problema y reinicia la conexión; ya no considera conectado al usuario solo porque la escritura quedó en cola. Si la escritura termina después de un timeout o cancelación, elimina esa presencia tardía.

## Capacidad y límites de verificación
- El objetivo solicitado es de 20 usuarios conectados a la aplicación, distribuidos entre distintas salas; no se impone un límite artificial de 20 por sala ni se cambia la lógica de los juegos.
- La aplicación usa Firestore y documentos separados por sala; las carreras usan documentos separados por piloto. Esto es una base razonable para ese volumen, pero no demuestra por sí solo una experiencia perfecta.
- No se puede certificar carga real ni conexión Firebase desde este entorno: hace falta compilar en GitHub Actions con la configuración Firebase auténtica y ejecutar una prueba con 20 cuentas/dispositivos conectados simultáneamente, distribuidos entre salas, verificando ingreso, latidos, cambios de pantalla, juegos y desconexiones.
- No se modificaron reglas de Firestore ni se publicaron cambios en Firebase.
