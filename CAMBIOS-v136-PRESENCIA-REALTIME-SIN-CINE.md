# v136 – Presencia y llamada grupal SIN latidos (Realtime Database) + Cine retirado

Problema: el plan gratuito de Firestore solo permite 20.000 escrituras por día y los latidos las gastaban.

## Qué cambió
- Presencia de salas (quién está conectado): ahora vive en Realtime Database. Se escribe UNA vez al entrar y el servidor
  borra solo a quien se desconecta (onDisconnect). Ya no hay latido cada 25/60 s.
- Reloj del servidor: se lee de Realtime Database (.info/serverTimeOffset), sin escribir nada.
- Llamada de voz grupal: quién está en la llamada también va por Realtime Database, sin latido de 10/20 s.
- Cine retirado: ya no aparece la pestaña ni el mini reproductor, y su controlador no escucha ni escribe nada.
- Diagnóstico de conexión: los pasos 3-5 ahora prueban Realtime Database.
- Si Realtime Database no estuviera disponible, la app usa el método anterior (Firestore) como respaldo.

## Lo que tenés que hacer UNA vez en Firebase
1. Firebase Console → proyecto sini-joss-nuevo → Compilación → Realtime Database → Crear base de datos
   (cualquier ubicación; elegí modo bloqueado).
2. Arriba de los datos aparece la URL. Si es https://sini-joss-nuevo-default-rtdb.firebaseio.com no tocás nada.
   Si es otra (por ejemplo ...-default-rtdb.europe-west1.firebasedatabase.app) cambiala en
   app/src/main/res/values/rtdb.xml.
3. Pestaña Reglas → pegá el contenido de database.rules.json → Publicar.
   (O corré en GitHub Actions «Publicar reglas Firebase»; ahora publica también estas reglas.)
4. Compilá y probá. En Ajustes → Diagnóstico de conexión los pasos de Realtime Database deben dar ✓.

## Archivos
database.rules.json (nuevo), firebase.json, .github/workflows/firebase-deploy.yml, gradle/libs.versions.toml,
app/build.gradle.kts, res/values/rtdb.xml (nuevo), Lobbies.kt, FirestoreCoupleRepository.kt, CoupleViewModel.kt,
WatchController.kt, GroupCallManager.kt, GuestScreens.kt, MainCoupleScaffold.kt, Economy.kt.
