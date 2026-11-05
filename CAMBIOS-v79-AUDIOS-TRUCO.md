# v79 — Audios de cantos del Truco

- Base utilizada: SINI-JOSS-v78-VIRUS-SELECCION-CORREGIDA.
- Se añadieron a `app/src/main/res/raw` los audios MP3 de cantos y respuestas (sin los archivos de categoría).
- `TrucoGame.kt` ahora asocia sonidos a cambios de mensaje de la partida: Truco, Retruco, Vale cuatro, Envido, Real envido, Falta envido, Quiero, No quiero y respuestas de envido.
- Los sonidos se disparan por cambios de estado producidos por acciones del juego, no por abrir la pantalla. El mensaje inicial se usa para inicializar el detector y no dispara audio.
- Se conserva el juego de Serpientes y Escaleras existente en `TableGames.kt` sin modificar su lógica.

## Validación pendiente
Esta entrega se modificó a nivel de código y recursos, pero no se ejecutó compilación ni prueba en dispositivo. En esta base se encontró una implementación de Serpientes y Escaleras con un tablero dibujado en código y selección de cantidad de bots; no se encontraron tres diseños/tableros seleccionables. Por lo tanto, no se afirma que la función de tres tableros esté presente en esta base y debe verificarse contra la versión que los incluía antes de distribuir.
