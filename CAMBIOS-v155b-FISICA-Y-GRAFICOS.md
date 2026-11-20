# v155b — Ping Pong, Fútbol, Truco, Arena FPS y Carrera

> Editado SIN poder compilar en el entorno: compilar y probar en el teléfono (los errores de sintaxis básicos se revisaron a mano).

## Ping Pong 3D
- Causa del "pica dos veces": la pala solo podía golpear en una franja fija al fondo, así que las pelotas cortas nunca se alcanzaban.
- Ahora el dedo mueve la pala a los lados y ADELANTE/ATRÁS (dedo arriba = pala cerca de la red). La altura es automática.
- El golpe es por alcance 3D (radio ~30 cm) y solo cuenta si la pelota ya botó en tu lado (no hay faltas de volea absurdas).
- Sin botones: deslizar rápido hacia adelante = más potencia; deslizar hacia un costado al pegar = la pelota va hacia ese lado y sale con efecto lateral.
- La IA también avanza hacia la red con pelotas cortas.
- Gráficos: parqué, vallas publicitarias, público, mesa con grosor, patas metálicas, líneas reglamentarias, brillo de focos, pelota blanca.

## Fútbol
- Pase y tiro apuntados con el joystick (flecha amarilla en el campo). PASE y TIRO: mantener apretado = más potencia.
- Con el joystick apuntando, el pase solo va al compañero que está en esa dirección; si no hay nadie, sale recto con la potencia elegida.
- Tiro con efecto cuando apuntas distinto a hacia donde corres.
- Cámara más abierta + marcadores en el borde para jugadores fuera de pantalla (antes parecían "desaparecer").
- Selecciones (60) y clubes (34, incluidos Boca Juniors y River Plate) con sus camisetas, shorts y medias. Menú con selector para tu equipo y el rival; si las camisetas se parecen, el rival usa la alternativa.

## Truco
- Vista en primera persona: estás sentado a la mesa, los demás te miran y sostienen sus cartas, paño verde en el centro, tus brazos sostienen tu mano abajo.

## Arena FPS
- Rasterizador nuevo: paredes con textura (ladrillo, cajas, metal, cartel de peligro), piso y techo con textura, lámparas y niebla.
- Joystick fijo a la izquierda, disparo grande a la derecha (se quitó el segundo botón de disparo).

## Carrera
- Ciudad nocturna con torres, ventanas, carteles de neón y balizas; lluvia, reflejos de neón en el asfalto mojado y conos de faros.

## Ronda 2
- **Arena FPS eliminado** (código, tests y entrada del menú).
- **Música de fondo al 10 %** (`BackgroundMusic.VOLUME = 0.1f`), desde que arranca.
- **Fútbol – pases:** el receptor ahora corre a buscar la pelota y puede controlarla aunque venga rápida; el control pasa solo al compañero que recibe; la pelota se dirige a donde va a estar el receptor; apuntar con el joystick elige al compañero más alineado (con una segunda pasada más abierta si no hay nadie en línea); pases algo más lentos y precisos.
- **Fútbol – sprint:** el botón 💨 ahora se activa con un toque (queda encendido) y además se corre llevando el joystick a fondo; más velocidad al correr y la resistencia dura más.
- **Truco con 4 jugadores:** rivales de los costados completos (ya no cortados), con brazos hacia sus cartas y cartas en abanico natural.

## Ronda 3
- **Truco 100 % 3D** (`TrucoScene3D.kt`): cámara en perspectiva propia sobre Canvas; mesa de madera con paño, alfombra y luz; jugadores completos (torso, cabeza con cara, brazos) sentados en su lugar, ya no se cortan; cartas como planos 3D con la imagen real (de espaldas en la mano de los rivales, se dan vuelta al caer); cada mano toma la carta, la lanza en arco, cae con sombra, onda y rebote, y la mano vuelve a su sitio (tuya y de los rivales). Tocás las cartas de tu mano para jugarlas.
- **Sala de espera:** se deshizo la invitación automática y el bloqueo de «Comenzar»: se puede empezar solo y las invitaciones solo se envían cuando el anfitrión las manda. Se mantiene el botón «Unirme a la partida» para quien ya fue invitado.

## Ronda 4 — Células (Agar) en la Arena
- **Causa:** la arena decidía quién estaba conectado y quién era el «servidor» comparando relojes entre celulares (con el desfase del servidor). Si un reloj estaba adelantado o el desfase aún no había cargado, el anfitrión veía al otro como desconectado y nunca lo metía a jugar (quedaba mirando), o el invitado le sacaba el control de la arena al anfitrión.
- **Arreglo:** la presencia de cada jugador se mide con el reloj del propio dispositivo (llegó una señal nueva = conectado) y el «latido» del servidor de la arena también; ya no se comparan relojes entre celulares.

## Ronda 5 — Fútbol 3D en horizontal y Trivia
- **Pantalla completa y horizontal:** al jugar fútbol se abre una ventana a pantalla completa y se pone la pantalla en horizontal (al salir vuelve a como estaba). Se agregó `configChanges` en el manifest para que girar no reinicie el partido.
- **Todo en 3D:** cámara que sigue el balón a lo largo y a lo ANCHO de la cancha, más cerca y baja (jugadores mucho más grandes); jugadores 3D con cabeza, torso con el diseño de la camiseta, brazos y piernas que se mueven al correr, patear, barrerse y atajar; balón 3D con manchas que giran según rueda y el efecto; porterías con red y postes, torres de focos, banderines, gradas con público.
- **Efectos:** ondas en el césped al golpear, tiro fuerte con estela de fuego, chispas, sacudida y golpe de zoom; red que se mueve en el gol; estrellas al aturdir; marcador sobre el jugador controlado.
- **Controles resumidos:** joystick (llevarlo al borde = correr), TIRO, PASE (mantener = más fuerte; al máximo sale por alto), ROBAR (mantener = corre al rival con la pelota y se la quita directo; toque = barrida) y 🔁 cambio. Se quitaron ALTO, FILTRO y el botón de sprint.
- **Trivia:** relojes más chicos junto a la categoría, ventana de la pregunta mucho más grande con letra que se ajusta al largo del texto, pulsador rojo más chico y marcadores de jugadores en una sola fila.
- **Truco:** manos más reales (palma, cuatro dedos con dos falanges y pulgar) en los rivales y en las tuyas; cartas ~45 % más grandes (en tu mano, en la mano de los rivales y sobre el paño).
- **Trivia – pulsador:** borde de neón que gira y late, halo rosa/cian, ondas de neón permanentes, explosión de luz con chispas al apretarlo y sonido de corneta (generado por código, sin archivos de audio).
- **Arreglo de compilación:** al deshacer la invitación automática de la sala se había borrado `val ready = me != null` en `MinigamesScreen.kt` (5 errores «Unresolved reference 'ready'»). Restaurado y verificado con diff contra el original. Además se quitó un test nuevo de ping pong (bola corta) que no se podía ejecutar acá.
