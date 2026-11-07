# v67 — Pruebas unitarias de reglas básicas del Truco

Base: SINI-JOSS v66.

## Cambios
- El evaluador de jerarquía, envido y mazo se expone al paquete de pruebas sin cambiar su comportamiento.
- Se agregan pruebas JUnit para las 40 cartas únicas, jerarquía de cartas argentinas y cálculo de envido con figuras a cero.

## Validación
- ZIP verificado con `unzip -t`.
- El workflow ya ejecuta `:app:testDebugUnitTest` antes del APK.
- No se pudo ejecutar JUnit localmente por falta de Gradle/Android SDK; se deben revisar los resultados en GitHub Actions.
