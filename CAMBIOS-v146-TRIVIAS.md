# v146 — Módulo TRIVIAS

Base: v145. Módulo nuevo e independiente en `app/src/main/java/com/example/trivia/`; se integra como un juego más del hub (`MinigamesScreen`, categoría «Palabras»), con el lobby, los invitados y la sincronización que ya existían.

## Cómo funciona
- **Pulsador decidido por el servidor.** Todos pulsan a la vez; cada pulsación es una *transacción* sobre `rooms/<sala>/games/trivia` en Realtime Database (con respaldo en Firestore). Solo puede confirmarse una (la primera que el servidor registra); las demás ven que el pulsador ya tiene dueño y quedan bloqueadas. La hora del pulsador es `ServerValue.TIMESTAMP`, no el reloj del teléfono.
- **Reloj.** Las horas del estado son del servidor; cada teléfono las corrige con `.info/serverTimeOffset`. El reloj de la pregunta se detiene mientras alguien responde y se reanuda con el tiempo restante tras un error (rebote).
- **Anfitrión.** Dirige los tiempos y los veredictos con escrituras «comparar y cambiar» (campo `ev`), así dos escrituras simultáneas nunca se pisan.
- **Respuestas** por texto o voz (reconocimiento de voz del sistema; la música de fondo se pausa mientras se escucha).
- **Verificación:** 1) juez local (tildes, mayúsculas, cifras/letras, faltas leves, respuesta dentro de una frase); 2) si no es claramente correcta, el servidor la evalúa con IA y búsqueda web (Cloud Function `verifyTriviaAnswer`); 3) si sigue ambigua, revisión manual del anfitrión (aceptar, rechazar o «segunda evaluación»). La pantalla indica siempre cómo se verificó: *comparación local*, *IA sin web* o *consulta web (n fuentes)*. «Web» solo se muestra si el servidor ejecutó búsquedas reales; si no hubo conexión, lo dice.
- **Puntuación configurable:** acierto (+50/100/200), penalización (0/-25/-50), bonificación por velocidad (hasta +50 % según el tiempo que quedaba), rebotes (0–5).
- **Administración:** categorías, dificultad, nº de preguntas, tiempo por pregunta y para responder, puntos, gestión de preguntas (activar/desactivar, añadir propias, borrar), estadísticas e historial (guardados en el dispositivo).
- Las respuestas correctas de la partida solo se guardan en el teléfono del anfitrión (no viajan en la base compartida) y se publican al revelar.

## Archivos
- Nuevos: `trivia/*.kt`, `functions/index.js` (función `verifyTriviaAnswer`), `app/src/test/.../TriviaRulesTest.kt`.
- Cambios de integración (mínimos): `MinigamesScreen.kt` (registro del juego), `GameOps.kt` (`ServerTime`), `FirestoreCoupleRepository.kt` (`triviaTransact`, `observeServerOffset`), `CoupleViewModel.kt` (dos funciones), `libs.versions.toml` y `app/build.gradle.kts` (dependencia `firebase-functions`, ya gestionada por el BOM).

## Configuración necesaria para la verificación con IA y web (una sola vez)
```
cd functions && npm install
firebase functions:secrets:set ANTHROPIC_API_KEY     # la clave vive solo en el servidor
firebase deploy --only functions:verifyTriviaAnswer
```
(Opcional: variable `TRIVIA_MODEL` para cambiar de modelo.) Sin esto el juego funciona igual: usa el juez local y, si hay dudas, la revisión manual; la pantalla avisa que no se consultó Internet.
Las reglas de Realtime Database ya permiten escribir en `games` a usuarios autenticados: no hay cambios en reglas.

## Validación
No se compiló ni se ejecutó en este entorno (sin Gradle ni red): las pruebas unitarias nuevas y la compilación deben comprobarse en GitHub Actions. Las pruebas multijugador reales (pulsador simultáneo en varios teléfonos, voz, función en la nube) requieren dispositivos y el despliegue de la función.

## v147 — Preguntas y limpieza
- Trivias: +100 preguntas (ahora 198; 9 por categoría más, en tres dificultades cada una).
- Publicidad: la app no contenía SDK, anuncios, banners publicitarios ni permisos de publicidad; solo se quitó el reto «Haz un anuncio publicitario…» de la lista de retos.
