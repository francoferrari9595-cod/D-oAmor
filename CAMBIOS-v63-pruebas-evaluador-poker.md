# v63 — Pruebas unitarias del evaluador de póker

Base: SINI-JOSS v62.

## Cambios
- El evaluador puro de cinco cartas y siete cartas queda accesible al paquete de pruebas.
- Se agregan pruebas JUnit para escalera de color, póker frente a full, rueda A-2-3-4-5, escalera de seis alta y búsqueda de la mejor mano entre siete cartas.

## Validación
- Se verificó la estructura del test y el ZIP con `unzip -t`.
- No fue posible ejecutar `testDebugUnitTest` porque no hay Gradle/Wrapper instalado en el entorno. Las pruebas quedan incluidas para que CI las ejecute.
