# v148 — Células Arena (estilo Agar.io) + arreglo de Trivias

## Arreglo de Trivias
El contenedor de juegos mide el contenido con altura infinita y luego lo encoge (`fitToHeight`). Trivias usaba `fillMaxSize` y `weight`, que con altura infinita se reducen a 0: por eso solo se veía la barra «TRIVIA». Ahora toma su altura de `gameAvail()` (igual que Monopoly, Pool y Carreras).

## Células Arena (`app/src/main/java/com/example/agar/`)
Juego propio, escrito desde cero, que reproduce las reglas de Agar.io. Sin imágenes ni código de terceros. Aparece en «Deportes» como «Células Arena».

Reglas implementadas (según lo documentado públicamente y los servidores comunitarios Ogar/MultiOgar):
- masa = tamaño² / 100; comienzas con masa 10; la masa se pierde 0,2 % por segundo.
- Solo comes a otra célula con ≥ 1,25× su masa y cuando su centro queda dentro de tu círculo; los empates no se comen.
- Velocidad ∝ tamaño^-0,439: las grandes son más lentas.
- Comida: cada bolita +1 de masa. Se repone sola.
- Dividir: mínimo 36 de masa, hasta 16 células, se lanzan hacia delante y se reúnen tras 30 s + 0,02 × masa; mientras tanto se empujan.
- Expulsar masa: mínimo 35; pierdes 16 y la bolita vale 12; se puede comer y sirve para alimentar virus.
- Virus: masa 100 (máx. 200). Si lo tocas con ≥ 1,25× su masa explotas en hasta 16 pedazos (con reunión más corta). Alimentarlo 7 veces crea otro virus que sale disparado.
- Cámara con zoom min(64 / suma de tamaños, 1)^0,4; clasificación Top 10 en vivo; 24 bots con IA (huyen de los grandes, cazan, comen, esquivan virus, dividen para atacar).
- Menú con apodo, 12 colores, tema claro/oscuro, cuadrícula y masa visible; pantalla de muerte con estadísticas; modo espectador; mejor masa guardada.
- Controles táctiles: arrastra el dedo (joystick flotante), botón DIVIDIR y botón de expulsar (mantén para repetir).

Cada jugador juega su propia arena en su teléfono (sin servidor). Un modo en línea con varias personas en la misma arena necesitaría un servidor del juego.
Pruebas unitarias: `app/src/test/java/com/example/agar/AgarEngineTest.kt`.
No se compiló en este entorno: verificar en GitHub Actions.
