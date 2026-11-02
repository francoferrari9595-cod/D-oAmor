# v77 — cambios sobre la base v76 corregida

## Base y preservación
- Esta entrega parte de `SINI-JOSS-v76-CORREGIDO.zip`.
- No se recuperaron archivos de versiones antiguas ni se revirtieron correcciones anteriores.
- Se conserva la corrección de importación `com.example.R` en `ChessGame.kt`.

## Truco
- Se conserva el historial persistente de cartas jugadas, incluso al pasar de una baza a otra.
- Las cartas históricas se agrupan y se muestran superpuestas con ligera inclinación para reconocer las jugadas anteriores.
- Las cartas de la mesa se archivan después del tiempo de revisión; el historial permanece en el estado compartido de la partida.

## Adivinanzas
- La primera adivinanza se elige al azar si la partida aún no tiene índice guardado.
- Al avanzar, se elige al azar una adivinanza distinta de la actual, evitando repetir inmediatamente la misma.

## Serpientes y escaleras
- Se conserva el juego existente en `TableGames.kt`, incluyendo la lógica del tablero y las posiciones sincronizadas.
- **Limitación de la base entregada:** no se encontraron recursos de imagen de tableros de Serpientes y escaleras en `app/src/main/res`. No se sustituyeron por imágenes inventadas ni se copiaron archivos de versiones anteriores. Para recuperar exactamente las imágenes que ya se habían incorporado, deben estar presentes en la base de trabajo correcta.

## Validación
- No se pudo ejecutar Gradle en este entorno porque no está instalado y el proyecto no incluye `gradlew`/`gradle-wrapper.jar`.
- Ejecutar el workflow de GitHub Actions para confirmar la compilación antes de distribuir la APK.
