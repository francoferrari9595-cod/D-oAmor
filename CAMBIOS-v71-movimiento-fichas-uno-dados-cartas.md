# SINI-JOSS v71 — animaciones de mesa y UNO multijugador

Base: SINI-JOSS v70. No se recuperaron archivos de versiones anteriores.

## Cambios
- Ludo: las fichas recorren casilla por casilla con animación suave en vez de desplazarse directamente al destino.
- Ajedrez: la pieza movida se desliza visualmente desde la casilla de origen a la de destino.
- UNO: el motor pasa de dos manos fijas a seis posibles puestos (`h0`…`h5`), permite 1 humano contra bots o hasta 6 humanos, y guarda turno, color, penalizaciones, banderas UNO y puntuaciones por participante.
- UNO: se puede configurar bots para completar hasta seis puestos; el anfitrión es el único que ejecuta las jugadas automáticas de bots.
- Diez Mil: los dados del jugador activo se revelan uno por uno con pausas para dar tensión antes de publicar el resultado final.
- Cartas reutilizables (incluidas las del póker): entrada animada al aparecer o revelarse.
- La sala conserva el número de humanos elegido al comenzar.

## Verificación
- El ZIP final se somete a prueba de integridad.
- No hay Gradle Wrapper ni Android SDK/Gradle ejecutable en el entorno actual, por lo que la compilación Android completa y las pruebas con varios dispositivos aún deben confirmarse en GitHub Actions.
- Las imágenes del truco no se reemplazaron; se esperan las imágenes del usuario para no usar cartas equivocadas.
