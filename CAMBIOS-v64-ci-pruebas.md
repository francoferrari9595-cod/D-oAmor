# v64 — Pruebas unitarias automáticas en CI

Base: SINI-JOSS v63.

## Cambios
- El workflow de GitHub Actions ejecuta `:app:testDebugUnitTest` antes de compilar el APK. Así, los tests unitarios añadidos para el evaluador de póker no quedan sin ejecutarse cuando se use el pipeline.
- Se realizó además una prueba aislada con `kotlinc` del mismo bloque de evaluación de póker: escalera de color, póker frente a full, rueda frente a escalera de seis alta y mejor mano de siete cartas. Resultado: 4/4 comprobaciones pasaron.

## Validación
- Prueba aislada de lógica pura ejecutada correctamente.
- ZIP verificado con `unzip -t`.
- El workflow Android completo aún debe ejecutarse en GitHub; no se pudo compilar la aplicación aquí por falta de Gradle, Gradle Wrapper y Android SDK local.
