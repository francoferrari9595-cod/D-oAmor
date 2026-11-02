SINI-JOSS v35 — Cambios realizados sobre SINI-JOSS-v34-ajedrez-corregido

1. Cine / YouTube
- Se amplió el área visible del reproductor a 380 dp para dar más espacio a la página móvil de YouTube y sus verificaciones.
- Se habilitó el control de pantalla completa de YouTube.
- Se implementaron los callbacks nativos de WebChromeClient para mostrar el video en pantalla completa y volver correctamente a la app.
- Se habilitó la barra de desplazamiento vertical del WebView.

Nota: YouTube puede exigir CAPTCHA/verificación por sus propios controles; la app no intenta eludir esas verificaciones. El usuario puede desplazarse y completarlas dentro del WebView.

2. Llamadas
- Se añadió un botón arriba a la derecha para minimizar la llamada.
- Al minimizar, la llamada continúa activa y aparece una barra compacta con opción de volver o finalizar.

3. Walkie-talkie
- Se eliminó el bloqueo local que impedía grabar cuando el otro teléfono estaba hablando o reproduciendo un mensaje.
- Cada usuario publica su mensaje en un campo separado msg_<uid> tanto en la sala como en el documento de walkie, reduciendo colisiones si ambos terminan la grabación casi al mismo tiempo.
- Se conserva compatibilidad de lectura con mensajes antiguos que usaban los campos id/by/data.
- Se limpia el archivo temporal de audio después de procesarlo.

Base conservada: SINI-JOSS-v34-ajedrez-corregido.zip. No se recuperaron archivos de versiones anteriores.
