# KOMBO ARENA v111 (sobre v110)
- Salas: la preparación de Sala 1-4 ya no se traba si la contraseña de la Sala 4 existía a medias o dos pantallas la crean a la vez.
- Los errores de salas dicen en qué paso fallan: [preparar salas], [leer lista de salas], [entrar a Sala X], [crear sala].
- firestore.rules sin cambios: hay que PUBLICARLAS en Firestore (default) del proyecto sini-joss-nuevo.
- Sin compilar: compilar y probar en el teléfono.

# v112
- Renombrado completo a KOMBO ARENA (carpeta, APK KOMBO-ARENA.apk, notificaciones, logs). Se conserva el applicationId por compatibilidad con Firebase.
- Presentación épica de 4 s al abrir (ui/KomboSplash.kt): destello, ondas, rayos, chispas, logo, título letra por letra.
- El error de permisos ahora muestra el ID del proyecto Firebase al que apunta la app.
- Documentos antiguos (D-oAmor) movidos a docs-antiguos/.

## v113 — Chat de sala, llamada grupal, tienda, Pool y Carreras
- Nuevas pestañas en la sala: Cine · Juegos · Chat · Tienda.
- Chat de toda la sala: burbujas con estilo, emojis, efectos de pantalla (confeti, corazones, nieve, estrellas, llamas, fuegos artificiales, tormenta, galaxia), regalos (a todos o a una persona), entradas con efecto, doble toque = corazones.
- Llamada de voz grupal (hasta 6) con silenciar, altavoz y servicio en segundo plano.
- Tienda: tacos y paños de Pool, autos de Carreras, bolas de Bowling, burbujas, colores de nombre y efectos de entrada. Fichas locales en el teléfono + bono diario.
- Pool: vista general fija + repetición de cada bola embocada, hasta 6 humanos + bots. Carreras corregidas, hasta 6 humanos + bots.
- IMPORTANTE: volver a publicar `firestore.rules` (reglas nuevas: chat, gcall, gsig, race).

## v114
- Llamada grupal: el botón vive solo en la pestaña Chat.
- Portada con botón "Ver salas" (las salas ya no aparecen solas).
- Presentación de 8 segundos.
- Cine (YouTube): quien reproduce envía un latido cada 4 s; los demás corrigen el desfase (>2,5 s), igualan play/pausa y los que entran tarde se ubican en el minuto correcto.
- Bowling y Pool: se ve en vivo cómo apunta el jugador con turno (nueva subcolección `live`, reglas nuevas).
- Gráficos: bowling (tablas, barniz, reflejos, pinos y bola con volumen), pool (reflejos en las bolas), mesa de Virus (madera, filete dorado, paño con trama, luz).
- Tienda: +12 regalos, +8 efectos de pantalla, +8 entradas, más burbujas, nombres, autos, tacos y paños.
- Nueva pestaña Ajustes (nombre, efectos, chat, tamaño de texto) y juegos con categorías y botones Reglas / Reiniciar.
- IMPORTANTE: volver a publicar `firestore.rules`.

## v115 — Juegos sin deslizar
- Los juegos de acción (bowling, pool, carreras, dardos, tejo, virus, ludo, truco, uno, póker, ajedrez, etc.) se ajustan solos al alto y ancho de cada teléfono: no hay scroll para tirar o ejecutar. Si el contenido es más alto que la pantalla se reduce de forma uniforme (mínimo 42 %).
- Los juegos de escribir texto o con listas largas (Tutti Frutti, Adivinanzas, Dibujo, Ahorcado, Impostor, Tienda de retos) conservan el scroll.
- Bowling, Pool, Carreras y Póker calculan el tamaño de su escena según el alto disponible.

## v116
- CORREGIDO el error de compilación de GitHub Actions: `RoomChat.kt:107` usaba `LocalContext.current` dentro de `remember {}`.
- Nuevo tema por defecto "KOMBO Arena": fondo negro, blanco y verde fluorescente (#39FF14). Botones con texto oscuro sobre verde para que se lea bien.
- Nuevo fondo animado por defecto "Rejilla neón" (rejilla en perspectiva que avanza, barrido de luz, partículas verdes). Se puede cambiar en Configuración.
- Transiciones: deslizamiento + fundido al cambiar de pestaña en la sala y fundido entre portada y sala. La presentación también toma los colores nuevos.

## v117
- CORREGIDO: `GuestScreens.kt:161 Unresolved reference 'togetherWith'` (la versión de Compose del proyecto no la trae). Las pestañas ahora cambian con fundido cruzado (Crossfade).

## v118
- Llamada grupal: ahora suena un aviso suave (tono de notificación + vibración corta, repetido ~20 s) y aparece notificación "📞 X está llamando" aunque la app esté en segundo plano; banner "Unirme / Ignorar" en cualquier pestaña.
- Carreras: obstáculos (conos, barreras) y pads de turbo iguales para todos, dirección progresiva con inercia en curvas, horizonte de ciudad, coches con más volumen/luces.
- Pool: bolas más grandes y movimiento más ligero. Dardos: se ve al otro apuntar. Truco: mesa real 1v1 (cartas enfrentadas, la 2ª mano encima) y en cruz para 4.
