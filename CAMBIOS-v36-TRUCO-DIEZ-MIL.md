# SINI-JOSS v36 — Truco argentino y Diez Mil

Base: `SINI-JOSS-v35-cine-llamadas-walkie.zip`. Esta versión se construye encima de v35 y conserva sus archivos y correcciones.

## Cambios

- Se agregó el juego **Truco argentino** al catálogo de minijuegos, con baraja española de 40 cartas, jerarquía de cartas, envido, truco/retruco/vale cuatro, marcador a 30 tantos y opción de bots.
- Se rediseñó **Diez Mil** para usar cinco dados, una presentación de vaso y controles de tirar, guardar dados puntuables y plantarse.
- Puntajes de Diez Mil: cada 1 = 100, cada 5 = 50, tres 1 = 1.000 y tres iguales del 2 al 6 = cara × 100. La primera plantada requiere al menos 750 puntos acumulados en ese turno.
- Diez Mil permite seleccionar hasta tres bots, además de los jugadores conectados, hasta cuatro asientos.
- Se registró que la baraja de Virus ya usa las cantidades del mazo base de 68 cartas (21 órganos, 17 virus, 20 medicinas y 10 tratamientos). Los gráficos actuales son ilustraciones propias con iconos; pueden sustituirse por diseños originales de cartas más realistas sin copiar escaneos del arte comercial.

## Validación

No se pudo ejecutar una compilación Android completa en este entorno porque el ZIP no incluye `gradlew` y no hay instalación local de Gradle. Se hizo una comprobación estática de sintaxis de los nuevos archivos, pero la compilación final debe confirmarse con GitHub Actions.
