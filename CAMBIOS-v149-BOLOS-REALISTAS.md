# v149 — Bowling: bolos con física 3D real y brazo que lanza
Solo cambia `ui/screens/BowlingGame.kt`.

## Bolos (cada uno es un sólido individual)
- Cuerpo rígido 3D por bolo: posición, orientación y giro en los 3 ejes, con gravedad.
- Peso propio (1,53–1,64 kg), centro de masa distinto (desgaste), rozamiento y rebote propios: no todos caen igual.
- Forma real (15"): base de 5,7 cm y centro de masa a ~15 cm, así que vuelca al inclinarse ~11° y si no llega, se bambolea y vuelve.
- Colisiones esfera-a-esfera con impulsos, rozamiento y giro: bola→bolo, bolo→bolo, bolo→suelo, paredes y cojín.
- Al ser cónicos, los bolos tumbados ruedan en arco. Pueden volar, rebotar en el cojín, caer a la canaleta o por el borde al foso.
- Cuenta como derribado el que no queda en pie sobre la tarima.
- Se renderizan en 3D real (silueta de revolución con perspectiva, franjas, brillo, sombra según altura).
- Máquina de bolos: barra que empuja la madera caída al foso, los bolos en pie se levantan, se enderezan y se apoyan de nuevo; en frame nuevo cae un rack nuevo.
- Bola 3D: gira al rodar con los agujeros visibles, rebota al soltarse, se hunde en la canaleta.
- Sonidos por impacto (intensidad real de cada choque), pasos, silbido del brazo, barra de la máquina.

## Brazo humano
- Al apretar TIRAR: pasos de aproximación, empuje, vaivén hacia atrás, balanceo acelerado tipo péndulo, soltada a ras de pista y seguimiento. Manga con el color de la bola, brazo, antebrazo y mano con dedos.
- Con más fuerza el brazo va más rápido y sube más atrás.
- Cámara detrás del hombro durante el lanzamiento y luego sigue la bola.
- Lo ven igual todos los jugadores (bots incluidos).

## Técnico
- Determinista (StrictMath, paso fijo 480 Hz): cada teléfono repite exactamente el mismo lanzamiento. La puntuación sigue usando el resultado publicado.
- La simulación corre fuera del hilo principal y se reutiliza en el teléfono de quien tira.
- Nuevo campo de estado `tstay` (si el frame sigue) para animar la máquina de bolos.
