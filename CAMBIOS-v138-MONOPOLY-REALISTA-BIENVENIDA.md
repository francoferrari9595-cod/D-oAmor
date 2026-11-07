# v138 – Monopoly más realista, contador de conectados y sonido de bienvenida

(Incluye todo lo de v137: chat y juegos en Realtime Database.)

## Salas: contador de conectados
- Antes el número salía de la lista de salas de Firestore: si esa lista tardaba o fallaba, se quedaba en «Conectando con el
  servidor…» y todas las salas mostraban 0 aunque hubiera gente. Ahora el contador sale directo de la presencia en
  Realtime Database y se ve aunque la lista no haya cargado.
- En cada sala dice «N conectados» en vez de solo un número.

## Sonido de bienvenida
- `res/raw/bienvenida.mp3` suena una sola vez al abrir la plataforma (no se repite al girar la pantalla ni al volver a las salas).

## Monopoly
- Tablero legible en el celular:
  - Pellizcar = zoom (hasta ×4) y arrastrar para moverse; doble toque = acercar/alejar; botón 🔍 = acercar a tu ficha.
  - Mantener apretado sobre el tablero = lupa ×3 que sigue al dedo (aparece arriba del dedo para no taparse).
  - Tocar un casillero = ver su escritura (igual que antes).
- Fichas: se deslizan casillero por casillero dando un saltito, con sombra; el movimiento largo (cartas, cárcel) es un
  deslizamiento más lento. Esperan a que los dados se asienten antes de moverse.
- Dados: ruedan ~3 segundos girando y volteándose, rebotan 4 veces sobre el tapete con sombra, las caras cambian cada vez
  más despacio y quedan quietos con una leve inclinación.
- Los bots esperan a que terminen los dados y las fichas antes de jugar.
- Billetes: los nuevos (1, 5, 10, 20, 50, 100, 500) recortados y con esquinas redondeadas; en «Plata» se muestran más
  grandes y apilados.
