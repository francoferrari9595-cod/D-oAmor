# v137 – Chat y juegos multijugador pasan a Realtime Database

Problema: Firestore (plan gratuito) limita a 20.000 escrituras/día y 50.000 lecturas/día. Cada jugada, cada mensaje
y cada posición de carrera era una escritura, y además una lectura por cada jugador conectado.

## Qué se movió a Realtime Database
| Antes (Firestore) | Ahora (Realtime Database) |
|---|---|
| `rooms/{sala}` campo `games.*` (estado de todos los juegos, walkie de sala, cine) | `rooms/{sala}/games/...` |
| `rooms/{sala}/chat` | `rooms/{sala}/chat` (últimos 80 mensajes) |
| `rooms/{sala}/live/{uid}` (puntería, selección) | `rooms/{sala}/live/{uid}` |
| `rooms/{sala}/race/{uid}` (carreras) | `rooms/{sala}/race/{uid}` |
| `rooms/{sala}/gsig` (señales de la llamada grupal) | `rooms/{sala}/gsig/{uid destino}/{id}` |
| presencia y quién está en la llamada | ya estaba en v136 |

## Lo que sigue en Firestore (casi no se escribe)
Documento de la pareja (mensajes 1 a 1, calendario, billetera, perfiles), lista de salas públicas, invitaciones de
invitados, contraseñas de salas, nombres de jugadores en la sala, walkie de la pareja, llamadas 1 a 1.

## Cambios de código
- `FirestoreCoupleRepository`: `updateRoomFields` manda los campos `games.*` a Realtime y el resto a Firestore;
  nuevos `observeRoomGames`, chat/live/race/señales por Realtime. Si Realtime no estuviera disponible, todo vuelve
  al método anterior (Firestore).
- `GameOps` (nuevo): reemplaza `FieldValue.increment/arrayUnion` en los juegos (Realtime no tiene FieldValue).
  Cambiado en Ruleta, Póker, Adivinanzas/Dibujo (MoreGames) y Lotería (CasinoShop).
- `CoupleViewModel`: escucha los juegos desde Realtime (`applyGames`); `applyRoom` solo maneja nombres/invitados.
- `database.rules.json`: reglas para `rooms/*` (publicarlas: GitHub Actions «Publicar reglas Firebase» o pegarlas en
  Firebase Console → Realtime Database → Reglas).

## Qué probar
1. Dos teléfonos en la misma sala: chat, un juego de cartas, ruleta, carrera, dibujo y llamada grupal.
2. Si algo dice «Permission denied»: las reglas nuevas no están publicadas.
3. Los datos viejos de Firestore no se copian (las partidas son efímeras); las salas empiezan limpias.

## Límites del plan gratuito de Realtime Database
1 GB almacenado, 10 GB/mes descargados, 100 conexiones simultáneas. El walkie de sala guarda audio en base64
dentro de `games/walkie`: es lo que más descarga consume.
