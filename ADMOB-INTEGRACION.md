# Integración de Google AdMob en D-oAmor

## Identificadores configurados

- ID de aplicación: `ca-app-pub-2515520610901543~4524186367`
- Unidad de anuncios Banner: `ca-app-pub-2515520610901543/2103083339`
- Tipo: Banner

Los identificadores se tomaron de la captura proporcionada por el usuario y quedaron configurados en el proyecto. El banner se muestra en las pantallas del lobby y no durante las partidas.

## Antes de publicar

1. Verificar en AdMob que la aplicación esté registrada con el paquete Android `com.aistudio.duoamor.wkqz`.
2. Completar los datos de pagos, verificación y políticas en AdMob.
3. Configurar consentimiento y privacidad según los países donde se distribuirá la app.
4. Probar la aplicación con los anuncios de forma segura. No hacer clic en anuncios propios.
5. Esperar la revisión/activación de AdMob cuando corresponda.

Los anuncios nuevos pueden tardar un tiempo en empezar a servirse. Crear la unidad no garantiza que ya haya anuncios disponibles ni ingresos inmediatos.

La integración conserva Google Mobile Ads SDK Android (Legacy) 25.5.0; conviene revisar la migración al SDK Next-Gen antes de una publicación a largo plazo.

## Anuncio intersticial bonificado por coins

- Unidad de anuncios Rewarded Interstitial: `ca-app-pub-2515520610901543/79462698608`.
- En el lobby se ofrece ver el anuncio para recibir **50 coins**.
- Las coins se acreditan únicamente desde el callback `OnUserEarnedRewardListener` de Google Mobile Ads, después de que AdMob confirma la recompensa.
- Al cerrar el anuncio, la aplicación solicita cargar el siguiente. Si no hay inventario o falla la carga, se muestra un mensaje y se puede reintentar.
- No se muestra automáticamente ni interrumpe partidas.
