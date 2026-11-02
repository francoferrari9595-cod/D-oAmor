# KOMBO ARENA v119

## Carreras 3D (RaceRender3D.kt)
- Autos como mallas 3D reales (carrocería, cabina, cristales, ruedas, alerón, luces) con perspectiva, luz por cara y brillo.
- Cámara de persecución, FOV que se abre con nitro, inclinación al girar, vibración en choques.
- 4 zonas: bosque, ciudad nocturna, desierto y costa. Guardarraíles, pórticos, edificios con ventanas, pinos, palmeras, cactus, farolas, carteles, mar.
- Humo de derrape, chispas, polvo, brasas de nitro, líneas de velocidad, viñeta.
- HUD: velocímetro con marcha y mini mapa circular.

## Bowling
- Sala con paredes, neón, luces de techo; bolos con más volumen y reflejos; bola con brillo y reflejo en la pista.

## Truco
- Sentido antihorario (yo → derecha → compañero enfrente → izquierda). Cartas en cruz en el centro:
  mía abajo, rival de la derecha un poco más arriba, compañero enfrente, otro rival a la izquierda. 1v1: rival enfrente.
- Se ven dadas vuelta las cartas que le quedan a cada uno.

## Temas visuales (Configuración → Temas visuales)
- 12 escenas animadas de fondo: Ciudad neón, Atardecer retro, Océano, Bosque de luciérnagas, Montañas nevadas, Sakura, Lluvia, Desierto, Espacio, Volcán, Aurora boreal, Casino Vegas.

## Tienda
- +30 regalos y +30 efectos de entrada.
- Tocar la foto en el chat: en la sala abre el regalo ya dirigido a esa persona; en el chat de pareja abre la ventana de regalos.
- Al llegarte un regalo suena una campanilla muy suave (SoftChime).

## Monopoly (MonopolyEngine.kt / MonopolyGame.kt)
- Tablero y billetes EXACTOS de tus archivos (res/drawable-nodpi/monopoly_*).
- Reglas oficiales: dados y dobles, 3 dobles = cárcel, GO $200, compra/subasta, alquileres, estaciones, servicios,
  casas y hoteles parejos (32/12), hipotecas (+10 %), cárcel (pagar, carta, dobles), Suerte y Arca comunal (16+16),
  impuestos, intercambios, quiebra ante banco o jugador. Opción: Parada libre acumula dinero.
- 2 a 8 jugadores (humanos en línea + bots). Bots compran, subastan, construyen, comercian y quiebran.
- Fichas: tus 8 imágenes (sombrero, auto, perro, dedal, gato, barco, pingüino, patito) en res/drawable-nodpi/monopoly_ficha_N.png.

## Salas
- Las salas ahora son cuadrados (2 por fila), cada uno con una ilustración animada de un juego de fondo (bowling, carreras, Monopoly, truco, dardos, pool, casino, ajedrez), contador de conectados y candado.
- Corrección del congelamiento al entrar: antes se esperaba la confirmación del servidor sin límite. Ahora hay aviso «Entrando…» con Cancelar, entrada automática a los 6 s en salas libres, reinicio de la conexión de Firestore si se traba, y mensajes de error claros.
- Las escrituras «en vivo» de los juegos (apuntar, etc.) se limitan a ~4 por segundo para no saturar la cola.

## Eliminar salas (administración)
- Mantené apretada una sala 5 segundos (aparece una barra roja) → escribí el código 600613 → Eliminar.
- Las reglas de Firestore verifican el código por su hash, así que HAY QUE PUBLICAR firestore.rules de nuevo en Firebase.
- Si eliminás una de las Sala 1-4, no se vuelve a crear sola.

## Compilación
- Se agregó SizeCompat.kt: define IntSize.minDimension (usado dentro de pointerInput) para evitar el error
  «Unresolved reference 'minDimension'» en DartsGame.kt:396 y TejoGame.kt:312.

## Monopoly sin deslizar
- Todo el juego entra en una sola pantalla: el tablero se ajusta al alto disponible de cada teléfono.
- Barra inferior con botones: 🏠 Props (construir/hipotecar), 🤝 Trato, 💵 Plata (billetes) y 📜 Log.
- Las cartas de Suerte / Arca comunal aparecen sobre el tablero unos segundos.
