# v68 — Corrección y validación aislada de pruebas de Truco

Base: SINI-JOSS v67.

## Cambios
- Se corrige una aserción de prueba: el 1 de basto supera al 2; la versión anterior comparaba esa jerarquía al revés.
- Se ejecutó con `kotlinc` el bloque real de funciones puras de mazo, jerarquía y envido, fuera de Android: 8/8 comprobaciones pasaron.

## Validación
- ZIP verificado con `unzip -t`.
- Las pruebas JUnit completas siguen pendientes de ejecutarse mediante Gradle/GitHub Actions.
