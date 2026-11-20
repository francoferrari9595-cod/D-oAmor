# v149 — Células Arena: células realistas, efectos y modos de juego

## Cómo se juega (menú)
- **Solo** (0 bots) o **contra N bots** (8, 16, 24, 40): cada quien en su teléfono.
- **Arena compartida** (si hay 2+ personas en la sala): el anfitrión pulsa «CREAR ARENA» y los demás «UNIRSE». Todos comparten arena, comida y virus; se pueden sumar bots de relleno.
- **Espectar**: mira a los bots.

## Arena compartida (cómo funciona)
El anfitrión simula todo (mismo motor, mismas reglas) y publica una instantánea compacta cada 0,2 s en `games/agar`; cada jugador envía su dedo (objetivo, dividir, expulsar, reaparecer) a `games/agarIn_<uid>` unas 10 veces por segundo. La comida usa casillas deterministas (posición = función de casilla y generación), así viaja como una cadena corta. Los clientes suavizan lo recibido. Si el anfitrión sale, se vacía la arena y los demás ven «La arena se cerró».
Consumo aproximado: ~10–12 KB/s por jugador conectado. Hay algo de retraso de red (la célula responde con ~0,2–0,4 s de latencia).

## Visual y efectos (se pueden apagar en el menú: «Efectos especiales»)
- Células gelatinosas: relieve con degradado, membrana ondulante, núcleo y gránulos que flotan, brillo especular, sombra, aura en la tuya, estela al dividirse y corona 👑 en el líder.
- Comida como orbes brillantes que laten; virus espinosos con volumen; masa expulsada como gotas.
- Fondo con profundidad (degradado, esporas con parallax), borde de arena con neón, viñeta.
- Efectos: partículas y ondas al comer, explosión de virus con sacudida de pantalla, textos «+masa», destello al devorar, estallido al morir, viñeta roja cuando una célula más grande se acerca, avisos de hitos (100, 250, 500, 1000…).

## Pruebas
`AgarEngineTest.kt`: reglas (comer ≥1,25×, dividir, expulsar, reunión 30 s + 0,02·masa, decaimiento), ida y vuelta de la instantánea de red.
No se compiló ni se probó en dispositivos en este entorno.
