
---
## Actualización (v13.1)
- Zumbador: ahora suena el TONO DE LLAMADA real del teléfono (en bucle, volumen de alarma) y vibra con patrón de llamada usando atributos de alarma (Android bloquea la vibración en segundo plano si no se usa). Canal de notificación nuevo y silencioso para que no suene el sonido de notificación.
- Juegos: el estado ahora se guarda en el documento de la pareja (campo games.<id>), igual que el chat/zumbador, sin depender de reglas extra. Si una jugada no se puede enviar, aparece un aviso rojo con el motivo.
- Efectos: fichas que rebotan, Conecta 4 con caída, cartas de memoria que giran en 3D, línea ganadora resaltada, tarjeta de turno que palpita, confeti y trofeo al ganar.
- Nuevo: Ludo (tablero completo, dado animado, capturas, casillas seguras, 6 = turno extra) con 0, 1 o 2 bots. Los bots los mueve el teléfono del jugador que tiene el menor identificador (anfitrión): mientras esa app esté abierta, los bots juegan.

---
## v14
- NUEVO Cine juntos (pestaña "Cine"): ventana estilo cine con YouTube sincronizado (play, pausa, ±10 s y barra de tiempo para los dos) y walkie-talkie: mantén el botón, suelta y a tu pareja le suena tu voz sola.
- Fichas: cada usuario inicia con 1000. Tragamonedas, Lotería, Póker (1 contra 1), Roba 2, Hundir la flota, Adivinanzas, Adivina el dibujo y Ludo con bots.
- Tienda: raspadas de retos (Básica 30 / Premium 80 / Épica 200, con ~90 retos en 9 categorías), regalos animados (12) y bandeja de retos recibidos (+40 fichas al cumplirlos).
- Perfil: nombre y foto editables en Ajustes; tu pareja los ve en la barra superior y el chat.
- Calendario y fecha de inicio: ahora se sincronizan por el documento de la pareja.
- Sonidos: en Ajustes eliges sonido de mensajes, notas de voz y llamadas (selector del teléfono) y activas/desactivas cada aviso. Con la app en segundo plano se notifica con ese sonido.
- Chat: "Ubicación" ahora envía tu ubicación real (antes mandaba siempre unas coordenadas fijas) y al tocarla abre el mapa.
- Si Firebase bloquea algo, aparece un aviso rojo arriba con el motivo.

IMPORTANTE: publica el archivo firestore.rules en Firebase Console → Firestore → Reglas → Publicar. Las llamadas, los audios y las fotos dependen de esas reglas; sin publicarlas Firebase las rechaza.

---
## v15
- Marca: la app ahora se llama SINI&JOSS (nombre, ícono nuevo con corazón y "S&J", título con degradado y brillo) y hay una pantalla de ingreso nueva con corazón 3D animado.
- UNO real (reemplaza a "Roba 2"): 7 cartas, color/número/símbolo, +2, +4, salto, reversa, comodines, botón ¡UNO! con penalización de 2 cartas y marcador hasta 500 puntos.
- Ludo en 3D: tablero inclinado con grosor y sombra, 6 estilos (Clásico, Madera, Neón, Cristal, Espacio, Caramelo) y fichas sombreadas con 7 formas a elegir (peón, cono, corazón, estrella, corona, diamante, esfera). Tu pareja ve la forma que elegiste.
- Chat: ahora hay "Cámara" para tomar la foto en el momento (además de "Galería"); la foto se corrige por orientación.
- Temas nuevos con negro puro: Negro + Rosa, Negro + Cian, Negro + Lima.
- Transición suave al cambiar de pestaña.
- Llamadas: si no se puede iniciar (sin pareja vinculada o sin sesión), ahora lo dice en pantalla en vez de no hacer nada.

---
## v16
- firestore.rules: ahora cualquier cosa que cuelga de la pareja (mensajes, llamadas, candidatos ICE, calendario, juegos...) la pueden leer y escribir solo las dos personas vinculadas. Se quitaron las validaciones de campos por documento que rechazaban las llamadas. Publicar en Firebase → Firestore → Reglas.
- Cine: el video se abre desde la página oficial de YouTube enviando el encabezado Referer (la causa de los errores 152/153) y se controla el video directamente. Pantalla arriba, mini chat del cine, el link queda oculto abajo ("🔗 Cargar video"). Si YouTube bloquea un video, aparece "Abrir en YouTube".
- Walkie-talkie: botón permanente en la barra superior y al final de cada juego.
- Ludo: modos Clásico, Rápido, Salida libre y Pacífico; dado 3D que cae, rebota y gira sobre la mesa; selectores de tablero y ficha movidos debajo para que lo principal quepa en pantalla.
- Cartas (UNO excluido): los números y palos de las cartas de póker/21 ahora se ven (antes el texto salía blanco sobre blanco).

---
## v17
- Fotos de perfil intercambiadas: corregido. Los perfiles se calculaban antes de que Firebase terminara de iniciar sesión y asignaban la foto a la persona equivocada; ahora se recalculan cuando se conoce tu usuario.
- Calendario: cada cita tiene "✅ Concluida / ↩︎ Reabrir" y "🗑️ Borrar", y los cambios se sincronizan con tu pareja.
- Sistema de fichas retirado: tragamonedas, lotería, tienda de retos y regalos son gratis y por diversión; sin saldos ni apuestas.
- Póker: mesa 3D inclinada con fieltro y borde de madera, jugadores sentados con su foto, cartas comunitarias al centro.
- Ahorcado 3D (nuevo): horca con madera y figura sombreadas, 7 categorías, modo juntos por turnos o palabra secreta propia, teclado con aciertos y errores.
- Adivina el dibujo: categorías (Mixto, Animales, Comida, Objetos, Lugares, Acciones, Amor) y tiempo por dibujo (sin límite, 45 s, 90 s, 2:30) con cuenta regresiva.
- Hundir la flota: mar azul, barcos de acero, explosiones y gotas. Adivinanzas: botón de pista.
- Ludo: fichas con pedestal, brillo y reflejo.

---
## v18
- Cine: el reproductor ahora es único y persistente. Play, pausa y saltos se aplican siempre en el otro teléfono (se converge al estado compartido cada medio segundo, no solo al pulsar). Corregida la imagen negra: ya no se recorta ni se anima la capa del video. Mini reproductor flotante con ▶/⏸ y ⛶ mientras juegas o chateas.
- Ludo: debajo del dado aparecen botones "Sacar ficha N" / "Mover ficha N" (el toque directo sobre el tablero inclinado podía fallar).
- Adivina el dibujo: los intentos equivocados se ven en ambos teléfonos.
- Ruleta europea 3D (nuevo) con 0, 1 o 2 bots y marcador de puntos.
- Tragamonedas: carretes con inclinación 3D y giro en tambor.
- Llamadas: si la conexión falla, aparece el motivo en pantalla.
