CAMBIOS v72 — CARTAS HD DEL TRUCO

- Base: SINI-JOSS-v71-animaciones-uno6.zip. No se recuperaron archivos de versiones anteriores.
- Se reemplazó únicamente la representación visual de las cartas dentro de TrucoGame.kt para mostrar imágenes reales de la baraja española.
- Se integraron 40 cartas HD (oros, copas, espadas y bastos; valores 1–7, 10–12) en app/src/main/res/drawable-nodpi.
- Las cartas de la mano y las cartas jugadas usan ahora las imágenes, manteniendo el identificador y la lógica actual de juego.
- Las cartas 8 y 9 no se incluyen porque el truco argentino utiliza una baraja española de 40 cartas.
- No se modificaron las reglas, salas, apuestas ni otros juegos.

Verificación: cambios de recursos y referencias revisados estáticamente. No se pudo ejecutar Gradle en este entorno porque el ZIP no contiene gradlew ni wrapper de Gradle; la compilación debe confirmarse en CI.
