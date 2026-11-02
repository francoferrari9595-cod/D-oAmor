# D-oAmor v13 — qué cambió

## Diseño (prioridad 1)
- Tema "Aurora Neón": fondo degradado animado, tarjetas de cristal, botones con degradado, barra inferior flotante. 5 temas: Aurora Neón, Rosa Pasión, Noche Dorada, Ultravioleta, Océano Neón (Ajustes > Estilo y Temas).
- Pantallas nuevas: principal (barra superior + navegación flotante), zumbador, juegos, vinculación. Chat con burbujas redondeadas. Resto de pantallas heredan los colores.

## Zumbador
- Causa del cierre: el sonido se creaba fuera de un try/catch en un hilo secundario; cualquier fallo de audio cerraba la app. Corregido.
- Ahora el teléfono de quien presiona solo hace un toque suave; suena y vibra el teléfono de la PAREJA mientras se mantenga presionado.
- Pide permiso: diálogo la primera vez + interruptor "Permitir zumbidos de mi pareja" en la pestaña Zumbador. Sin permiso no suena nada.
- El servicio escucha Firestore directamente (funciona con app en segundo plano sin Cloud Functions). Seguridad: se corta solo a los 60 s y se ignora un zumbido viejo.

## Audios y fotos
- Ya no dependen de Firebase Storage (que exige plan Blaze): la foto (reducida a ~1024 px) y el audio (AAC 32 kbps, máx. 90 s) viajan dentro del mensaje en Firestore.

## Llamadas
- Agregados servidores TURN (necesarios entre redes distintas), no se corta por micro-desconexiones, ignora llamadas viejas "fantasma", pide cámara/micrófono al inicio, y avisa llamada entrante con notificación a pantalla completa aunque la app esté en segundo plano.
- Importante: el TURN incluido es público/gratuito (openrelay). Para vender la app usa uno propio (Metered, Twilio, Cloudflare Calls) y cámbialo en WebRtcCallEngine.kt.

## Juegos en vivo (por turnos, los dos ven todo al instante)
Tres en raya, Conecta 4, Memoria, Piedra-papel-tijera, Preguntas de pareja, Ruleta (ambos la ven girar), Verdad o reto y Cupones.

## Pasos
1. Publicar firestore.rules (sin cambios obligatorios) y compilar con GitHub Actions como siempre.
2. Instalar en AMBOS teléfonos, vincular y aceptar los permisos.
3. No compilé ni probé en teléfonos reales: si Actions marca un error, pásamelo y lo corrijo.
