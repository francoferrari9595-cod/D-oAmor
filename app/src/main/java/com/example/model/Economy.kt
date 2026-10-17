package com.example.model

import androidx.compose.ui.graphics.Color

/** Regalo virtual que se compra con fichas y se envía a la pareja. */
data class GiftDef(val id: String, val name: String, val emoji: String, val price: Long, val c1: Color, val c2: Color, val note: String)

val GIFTS = listOf(
  GiftDef("rose", "Rosa eterna", "🌹", 60, Color(0xFFFF2E63), Color(0xFF7A0B3A), "Una rosa que nunca se marchita"),
  GiftDef("heart", "Corazón de cristal", "💎", 120, Color(0xFF37E6FF), Color(0xFF3A1F8E), "Frágil, brillante y solo tuyo"),
  GiftDef("bear", "Osito abrazador", "🧸", 90, Color(0xFFFFB46B), Color(0xFF8A4B14), "Para abrazarte cuando yo no pueda"),
  GiftDef("choc", "Caja de bombones", "🍫", 80, Color(0xFFB5651D), Color(0xFF3B1D0A), "Dulce como tú"),
  GiftDef("cake", "Pastel sorpresa", "🎂", 150, Color(0xFFFF8AD8), Color(0xFF7A1F6B), "Celebremos aunque estemos lejos"),
  GiftDef("star", "Estrella para ti", "🌟", 100, Color(0xFFFFE066), Color(0xFF9A6B00), "Te bajé una estrella"),
  GiftDef("balloons", "Globos del amor", "🎈", 70, Color(0xFFFF6B8B), Color(0xFF5B1B7A), "Para llenarte el día de colores"),
  GiftDef("ring", "Anillo de promesa", "💍", 400, Color(0xFFFFD166), Color(0xFF6B4A00), "Una promesa a la distancia"),
  GiftDef("crown", "Corona de reina/rey", "👑", 300, Color(0xFFFFC83D), Color(0xFF7A3B00), "Eres la realeza de mi vida"),
  GiftDef("moon", "Luna compartida", "🌙", 110, Color(0xFF9D6BFF), Color(0xFF1A0B4E), "Miremos la misma luna esta noche"),
  GiftDef("plane", "Pasaje a mis brazos", "✈️", 500, Color(0xFF3DA9FF), Color(0xFF0B2A6B), "Un viaje directo a ti"),
  GiftDef("letter", "Carta de amor", "💌", 50, Color(0xFFFF9AA8), Color(0xFF7A1B3A), "Palabras que se quedan para siempre")
)

data class GiftEvent(val giftId: String, val from: String, val t: Long)

data class RetoItem(val id: String, val text: String, val from: String)

/** Retos para parejas a distancia. */
val RETO_CATEGORIAS: Map<String, List<String>> = mapOf(
  "💖 Románticos" to listOf(
    "Escríbele una carta de amor de 5 líneas y mándala por audio.",
    "Dile 5 cosas que te enamoraron de él/ella la primera vez.",
    "Cuéntale tu recuerdo favorito juntos con todo detalle.",
    "Dedícale una canción y explica por qué te recuerda a él/ella.",
    "Haz una promesa romántica para cuando se vean.",
    "Describe cómo será el abrazo cuando se reencuentren.",
    "Dile qué es lo primero que harás al verle en persona.",
    "Cuéntale un sueño que tuviste con él/ella.",
    "Escribe un poema corto improvisado para tu pareja.",
    "Manda un audio diciéndole 'te amo' de 3 maneras distintas."
  ),
  "😂 Divertidos" to listOf(
    "Imita a tu pareja en un audio de 15 segundos.",
    "Manda una foto haciendo la cara más graciosa que puedas.",
    "Inventa un apodo ridículo y úsalo toda la tarde.",
    "Cuenta un chiste malo y que tu pareja califique del 1 al 10.",
    "Habla como robot durante 3 mensajes seguidos.",
    "Baila 20 segundos en videollamada sin música.",
    "Describe tu día como si fuera una película de terror.",
    "Manda un sticker o emoji que resuma tu mañana y explícalo.",
    "Cántale el cumpleaños feliz con voz de ópera.",
    "Haz un anuncio publicitario de tu pareja como si fuera un producto."
  ),
  "📞 A distancia" to listOf(
    "Miren el mismo atardecer y compartan una foto del cielo de cada uno.",
    "Pongan la misma canción a la vez y escúchenla juntos en llamada.",
    "Cenen juntos por videollamada, cada uno con su comida favorita.",
    "Vean un episodio o una película juntos en el Cine de la app.",
    "Envíale una foto de lo que ves desde tu ventana ahora mismo.",
    "Cuéntale cómo es tu rutina de hoy hora por hora.",
    "Elige una hora para dormirse juntos en llamada.",
    "Planeen el primer viaje cuando se reencuentren: destino y 3 actividades.",
    "Escríbele qué es lo que más extrañas de estar a su lado.",
    "Pónganse una alarma a la misma hora para pensar el uno en el otro un minuto."
  ),
  "🎨 Creativos" to listOf(
    "Dibuja a tu pareja en 60 segundos y mándale la foto.",
    "Haz un collage de 5 fotos de ustedes y describe cada una.",
    "Inventa el título y la sinopsis de la película de su historia de amor.",
    "Crea un lema para ustedes como pareja.",
    "Escribe la letra de una canción de 4 versos para ella/él.",
    "Diseña la casa de sus sueños y descríbela.",
    "Crea un cupón de amor con una promesa y envíalo.",
    "Haz un origami o dibujo de un corazón y mándalo.",
    "Escribe su historia como un cuento corto de 6 frases.",
    "Elige un color, una flor y un animal que representen a tu pareja."
  ),
  "🍳 Cocina y comida" to listOf(
    "Cocinen la misma receta cada uno en su casa y comparen resultados.",
    "Mándale una foto de tu desayuno y dile qué le prepararías.",
    "Elige el menú de la primera cena cuando se vean.",
    "Describe tu postre favorito y por qué te recuerda a él/ella.",
    "Prepara un café o té y brinda por videollamada.",
    "Inventen una receta con 3 ingredientes que tengan en casa.",
    "Dile qué plato cocinarías para una cena romántica.",
    "Haz una lista de 5 restaurantes que quieren visitar juntos."
  ),
  "🎵 Música" to listOf(
    "Mándale 3 canciones que te recuerden a él/ella.",
    "Canta 20 segundos de tu canción favorita por audio.",
    "Armen una playlist compartida de 10 canciones de su historia.",
    "Adivina una canción que te tararee por audio.",
    "Elige la canción que sonaría en su boda imaginaria.",
    "Baila una canción lenta por videollamada.",
    "Cuenta qué canción te hace llorar y por qué.",
    "Dedícale la canción de hoy con un mensaje."
  ),
  "📸 Foto y video" to listOf(
    "Manda una selfie con el mejor outfit que tengas puesto.",
    "Graba un video de 10 segundos diciendo buenos días de la forma más tierna.",
    "Mándale una foto de tu lugar favorito de tu ciudad.",
    "Hagan la misma pose en foto cada uno y júntenlas.",
    "Toma una foto de algo que te recuerde a tu pareja hoy.",
    "Manda una foto de tu mano haciendo un corazón con el fondo que prefieras.",
    "Graba un video mostrando tu casa como un tour.",
    "Mándale una foto tuya de niño/a y cuenta la historia."
  ),
  "🔥 Atrevidos" to listOf(
    "Dile con voz suave tres cosas que te encantan de su físico.",
    "Cuéntale tu fantasía de cita perfecta sin límites.",
    "Mándale un audio susurrándole lo que harías en un beso largo.",
    "Dile cuál es tu rincón favorito para recibir mimos.",
    "Describe el beso que le darías al verlo/a en persona.",
    "Reto de miradas: 1 minuto en videollamada sin reírse.",
    "Cuéntale qué prenda suya te encanta cómo le queda.",
    "Confiesa algo que nunca te atreviste a decirle."
  ),
  "🧘 Bienestar" to listOf(
    "Hagan 5 minutos de respiración profunda juntos en llamada.",
    "Dile 3 cualidades que admiras y que él/ella suele olvidar.",
    "Compartan qué los preocupa hoy y apóyense mutuamente.",
    "Salgan a caminar 15 minutos en llamada cada uno en su ciudad.",
    "Escribe un mensaje de ánimo para el día difícil que viene.",
    "Elige una meta de esta semana y comprométanse a apoyarse.",
    "Hagan un estiramiento de 5 minutos juntos por videollamada.",
    "Cuenta tres cosas por las que estás agradecido/a de tenerle."
  )
)
