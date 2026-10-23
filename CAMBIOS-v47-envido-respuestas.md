# SINI-JOSS v47 — Respuestas de envido

Base: SINI-JOSS v46. Esta versión parte de la versión inmediatamente anterior; no recupera archivos de versiones antiguas.

## Cambios
- El Truco ahora separa la llamada de envido de su resolución: el rival puede querer o no querer.
- Se agregan llamadas de Envido, Real Envido y Falta Envido antes de jugar la primera carta.
- Si se acepta, compara el mejor puntaje de envido de cada equipo y asigna la apuesta correspondiente (2, 3 o los tantos restantes hasta 30 para falta envido).
- Si se rechaza, asigna un tanto al equipo que llamó.
- Los bots pueden responder al envido, sin depender de que su asiento sea el del jugador humano.
- Se actualiza la descripción del juego para reflejar estas opciones.

## Limitaciones conocidas
- No se implementó todavía toda la cadena de retruco de envido ni las reglas de flor.
- Falta validar todas las reglas de desempate y los casos límite contra una implementación de referencia.
- No se pudo certificar la compilación Android en este entorno; el proyecto base carece de `gradlew` y `gradle-wrapper.jar`.
