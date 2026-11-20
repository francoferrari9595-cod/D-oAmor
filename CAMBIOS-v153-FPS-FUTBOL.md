# v153 — Arena FPS y Fútbol Pro

Dos juegos nuevos en el hub (categoría «Deportes»), ambos con bots, uno contra uno y multijugador en línea con la gente de la sala (el anfitrión simula y los demás envían su control).

## Arena FPS (`fps/`)
- Primera persona con raycasting: paredes con 4 tipos de material, luz y niebla por distancia, juntas de ladrillo, techo y suelo con degradado, sprites con oclusión (soldados, objetos), minimapa.
- 4 armas con cargador, recarga y retroceso: Pistola, Fusil (automático), Escopeta (8 perdigones con caída de daño) y Francotirador (mira telescópica con zoom, daño ×2 a la cabeza). Disparo hitscan con altura (apuntar arriba/abajo importa: cabeza ×2).
- Pickups: salud, armadura, munición, escopeta y francotirador (reaparecen).
- 3 mapas: Almacén, Cruce y Laberinto. 3 modos: Todos contra todos (15 bajas), Equipos (30) y Duelo 1v1 (10).
- Bots con 4 niveles: navegación por la cuadrícula, ven con campo de visión y línea de tiro, reaccionan, se desplazan lateralmente, cambian de arma según la distancia, recogen salud/munición y recargan.
- Efectos: fogonazo, trazadoras, marcador de impacto, viñeta roja al recibir daño y pulso con poca vida, sacudida, parte de bajas, cuenta de reaparición, arma que se balancea al caminar.
- Controles: joystick izquierdo (moverte), arrastrar a la derecha (apuntar), disparo, recarga, cambio de arma y zoom.
- En línea: tu posición es local (respuesta inmediata); el anfitrión decide disparos, daño, bajas y munición.

## Fútbol Pro (`soccer/`)
- Cancha reglamentaria (105 × 68 m), porterías con red, estadio con gradas y público animado, focos, vallas LED, césped con franjas, líneas oficiales, cámara 3D que sigue el balón (zoom y sacudida en los goles), radar.
- Equipos de 1 a 4 jugadores de campo + portero (IA). Regate, pase inteligente al compañero, tiro con carga de potencia, entrada (puede fallar y dejarte aturdido), carrera con resistencia, cambio de jugador (automático si eres el único humano del equipo), porteros que se desplazan, se lanzan, atajan o rechazan.
- Reglas: saque inicial, banda, esquina y saque de meta, postes y travesaño, goles con celebración (confeti), dos tiempos configurables (1:00 / 1:30 / 2:00), medio tiempo y marcador final.
- Bots con 4 niveles (velocidad, presión, tiros y entradas).
- En línea: los humanos se reparten entre azul y rojo; los puestos libres los ocupan bots.

## Pruebas
`FpsEngineTest`, `SoccerEngineTest` (más las anteriores). No se compiló ni se probó en dispositivos en este entorno.
