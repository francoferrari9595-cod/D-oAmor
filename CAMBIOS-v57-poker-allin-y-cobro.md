# v57 — Corrección de all-in y liquidación del póker

Base: SINI-JOSS v56. No se recuperaron archivos de versiones anteriores.

## Cambios
- Después de devolver una apuesta no cubierta, se vuelve a calcular si algún jugador sigue realmente all-in. Si la devolución deja fichas disponibles, la mano continúa con el turno correcto en lugar de forzar un showdown.
- Se agrega una guarda local para impedir que recomposiciones de Compose acrediten repetidamente el resultado del póker mientras Firestore confirma la marca `settled`.

## Validación
- Revisión estática de las dos ramas modificadas.
- ZIP verificado con `unzip -t`.
- No se afirma compilación Android: este entorno no tiene Gradle instalado ni el Gradle Wrapper completo, y no se pudo descargar la distribución.

## Limitación conocida
La guarda local reduce duplicados por recomposición, pero una liquidación financiera estrictamente atómica entre dispositivos requiere mover el cobro y la marca `settled` a una transacción/Cloud Function idempotente. No se debe tratar el saldo del cliente como una autoridad segura para dinero real.
