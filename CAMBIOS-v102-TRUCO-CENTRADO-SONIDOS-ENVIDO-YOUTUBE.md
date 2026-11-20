# v102 — Truco, envido y visualización de YouTube

Base: `SINI-JOSS-v101-DADOS-3D-SIN-CONTROL-VOLUMEN.zip`.

- El marcador del Truco muestra los nombres de los jugadores de cada lado en vez de las etiquetas genéricas «Equipo 1» y «Equipo 2».
- Las cartas jugadas y las cartas de la mano ajustan su ancho al espacio disponible y la mano se centra horizontalmente.
- Se quitaron los sonidos automáticos de cuenta regresiva y de cantos. Se conserva únicamente el sonido al jugar una carta.
- La respuesta al envido se habilita para cualquier jugador humano del lado al que le toca responder, y el canto inicial prioriza un rival humano si existe.
- Se permite que cualquier compañero del lado que responde proponga subir el envido.
- Se ajustó el CSS del reproductor embebido de YouTube para que el iframe ocupe explícitamente todo el contenedor, buscando corregir la reproducción con audio pero sin imagen.

## Pendientes verificados
- El ZIP base no contiene cinco imágenes identificables de tableros de serpientes y escaleras. La pantalla actual los dibuja por código. No se inventaron ni se sustituyeron por imágenes similares; para integrarlas exactamente se necesitan esos cinco archivos dentro del proyecto o en otro ZIP de referencia.
- No se pudo ejecutar `assembleDebug`: el ZIP no contiene `gradlew` ni `gradle-wrapper.jar`, y el entorno no tiene el comando `gradle` instalado. La compilación y la comprobación visual en dispositivo quedan pendientes.
