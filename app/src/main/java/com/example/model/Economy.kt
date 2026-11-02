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
  GiftDef("letter", "Carta de amor", "💌", 50, Color(0xFFFF9AA8), Color(0xFF7A1B3A), "Palabras que se quedan para siempre"),
  GiftDef("coffee", "Café de buenos días", "☕", 40, Color(0xFFB5835A), Color(0xFF3B2410), "Un café para empezar juntos el día"),
  GiftDef("pizza", "Pizza para dos", "🍕", 70, Color(0xFFFF8A3D), Color(0xFF8A2B0B), "Cena a distancia, ¡yo invito!"),
  GiftDef("wine", "Brindis", "🍷", 110, Color(0xFFB0124F), Color(0xFF3A0420), "Por nosotros y por lo que viene"),
  GiftDef("fireworks", "Fuegos artificiales", "🎆", 160, Color(0xFF9D6BFF), Color(0xFF0B0830), "Una explosión de colores para ti"),
  GiftDef("unicorn", "Unicornio mágico", "🦄", 260, Color(0xFFFF9AD5), Color(0xFF4B1E7A), "Porque contigo todo es magia"),
  GiftDef("camera", "Cámara de recuerdos", "📷", 200, Color(0xFF90A4AE), Color(0xFF263238), "Para guardar cada momento"),
  GiftDef("guitar", "Guitarra", "🎸", 220, Color(0xFFFFB74D), Color(0xFF5D3A00), "Te dedico una canción"),
  GiftDef("watch", "Reloj de oro", "⌚", 350, Color(0xFFFFD54F), Color(0xFF6B4A00), "Cada minuto contigo vale oro"),
  GiftDef("necklace", "Collar de perlas", "📿", 320, Color(0xFFF5F5F5), Color(0xFF616161), "Elegante como tú"),
  GiftDef("rainbow", "Arcoíris", "🌈", 120, Color(0xFF4FC3F7), Color(0xFF7B1FA2), "Después de la lluvia, siempre tú"),
  GiftDef("car", "Auto deportivo", "🚗", 600, Color(0xFFE53935), Color(0xFF3B0A0A), "Te llevo a donde quieras"),
  GiftDef("trophy", "Trofeo de campeón", "🏆", 250, Color(0xFFFFD60A), Color(0xFF7A5A00), "Para el mejor de la sala"),
  GiftDef("diamond", "Diamante", "💠", 450, Color(0xFF7DF9FF), Color(0xFF0B3A8A), "Brilla más que nadie"),
  GiftDef("rocket", "Cohete", "🚀", 350, Color(0xFFFF7043), Color(0xFF1A0B4E), "¡Directo a la luna!"),
  GiftDef("dragon", "Dragón guardián", "🐲", 700, Color(0xFF66BB6A), Color(0xFF0B3A1B), "Te protege en cada partida"),
  GiftDef("lion", "León valiente", "🦁", 380, Color(0xFFFFB74D), Color(0xFF6B3A00), "Rugí fuerte, campeón"),
  GiftDef("yacht", "Yate de lujo", "🛥️", 900, Color(0xFF4FC3F7), Color(0xFF0B2A6B), "Navegá con estilo"),
  GiftDef("castle", "Castillo", "🏰", 1000, Color(0xFFB388FF), Color(0xFF2A1070), "Un reino entero para vos"),
  GiftDef("gem", "Gema mágica", "🔮", 280, Color(0xFFB388FF), Color(0xFF3D1E8A), "Predice tu victoria"),
  GiftDef("pizza2", "Mega hamburguesa", "🍔", 60, Color(0xFFFFA040), Color(0xFF6B3A00), "Para recuperar energías"),
  GiftDef("icecream", "Helado", "🍦", 45, Color(0xFFFFC1E3), Color(0xFF8A3B6B), "Refresca la partida"),
  GiftDef("mic", "Micrófono de oro", "🎤", 240, Color(0xFFFFD166), Color(0xFF5A3A00), "Cantá para la sala"),
  GiftDef("controller", "Joystick pro", "🎮", 210, Color(0xFF7C4DFF), Color(0xFF1A0B4E), "Para ganar todas"),
  GiftDef("house", "Casita soñada", "🏠", 800, Color(0xFF81C784), Color(0xFF1B5E20), "Nuestro hogar, algún día"),
  // ── 30 regalos nuevos ──
  GiftDef("sunflower", "Girasol", "🌻", 55, Color(0xFFFFD54F), Color(0xFF6B4A00), "Siempre mirando hacia ti"),
  GiftDef("tulip", "Tulipanes", "🌷", 65, Color(0xFFFF80AB), Color(0xFF7A1B3A), "Un ramo para alegrarte el día"),
  GiftDef("bouquet", "Ramo gigante", "💐", 180, Color(0xFFF48FB1), Color(0xFF6A1B4D), "Flores para todo un jardín"),
  GiftDef("cupcake", "Cupcake", "🧁", 50, Color(0xFFFFB6E1), Color(0xFF7A2A6B), "Pequeño, dulce y para ti"),
  GiftDef("donut", "Dona glaseada", "🍩", 45, Color(0xFFF8BBD0), Color(0xFF8A3B5B), "Con granas de colores"),
  GiftDef("champagne", "Champagne", "🍾", 260, Color(0xFFFFE082), Color(0xFF5D4200), "¡Salud por la victoria!"),
  GiftDef("sushi", "Sushi para dos", "🍣", 90, Color(0xFFFF8A65), Color(0xFF6B2A10), "Una cena japonesa a distancia"),
  GiftDef("tacos", "Tacos al pastor", "🌮", 55, Color(0xFFFFB74D), Color(0xFF6B3A00), "¡Para la hora de la cena!"),
  GiftDef("popcorn", "Pochoclos", "🍿", 40, Color(0xFFFFF176), Color(0xFF7A5A00), "Para la peli de esta noche"),
  GiftDef("beer", "Cerveza helada", "🍺", 60, Color(0xFFFFCA28), Color(0xFF6B4A00), "¡Brindemos por esta partida!"),
  GiftDef("mate", "Mate cebado", "🧉", 50, Color(0xFF9CCC65), Color(0xFF2E5A1B), "Un mate para compartir"),
  GiftDef("kiss", "Beso volador", "😘", 35, Color(0xFFFF6B8B), Color(0xFF7A0B3A), "Un beso que cruza la pantalla"),
  GiftDef("angel", "Ángel de la guarda", "😇", 230, Color(0xFFE1F5FE), Color(0xFF4A6A8A), "Te cuida en cada jugada"),
  GiftDef("clover", "Trébol de la suerte", "🍀", 140, Color(0xFF66BB6A), Color(0xFF1B5E20), "Suerte garantizada"),
  GiftDef("dice", "Dados de oro", "🎲", 120, Color(0xFFFFD54F), Color(0xFF7A5A00), "Que salga doble seis"),
  GiftDef("joker", "Comodín", "🃏", 130, Color(0xFF7E57C2), Color(0xFF1A0B4E), "La carta que cambia todo"),
  GiftDef("medal", "Medalla de oro", "🥇", 200, Color(0xFFFFD60A), Color(0xFF7A5A00), "Para el campeón de la noche"),
  GiftDef("fire", "Racha en llamas", "🔥", 100, Color(0xFFFF7043), Color(0xFF7A1A00), "¡Estás on fire!"),
  GiftDef("lightning", "Rayo veloz", "⚡", 150, Color(0xFFFFF176), Color(0xFF3A2A8A), "Rápido como un rayo"),
  GiftDef("saturn", "Planeta anillado", "🪐", 420, Color(0xFFFFB74D), Color(0xFF3A1A6B), "Un mundo entero para ti"),
  GiftDef("comet", "Cometa", "☄️", 300, Color(0xFFFF8A65), Color(0xFF1A0B4E), "Pasa una vez cada mil años"),
  GiftDef("butterfly", "Mariposa", "🦋", 75, Color(0xFF4FC3F7), Color(0xFF6A1B9A), "Revolotea en el estómago"),
  GiftDef("swan", "Cisne real", "🦢", 240, Color(0xFFF5F5F5), Color(0xFF5A6A8A), "Elegancia en el agua"),
  GiftDef("peacock", "Pavo real", "🦚", 330, Color(0xFF26C6DA), Color(0xFF1B5E3A), "Luce todos tus colores"),
  GiftDef("panda", "Panda tierno", "🐼", 160, Color(0xFFECEFF1), Color(0xFF263238), "Un abrazo de oso panda"),
  GiftDef("fox", "Zorro astuto", "🦊", 170, Color(0xFFFF8A3D), Color(0xFF6B2A00), "Más listo que nadie"),
  GiftDef("octopus", "Pulpo goloso", "🐙", 190, Color(0xFFE57373), Color(0xFF6A1B4D), "Ocho brazos para abrazarte"),
  GiftDef("hotair", "Globo aerostático", "🎈", 480, Color(0xFFFF7043), Color(0xFF3A1A8A), "Un paseo por las nubes"),
  GiftDef("train", "Tren expreso", "🚂", 520, Color(0xFF90A4AE), Color(0xFF263238), "Directo a tu estación"),
  GiftDef("motorbike", "Moto deportiva", "🏍️", 560, Color(0xFFEF5350), Color(0xFF3B0A0A), "Acelerá conmigo"),
  GiftDef("island", "Isla privada", "🏝️", 950, Color(0xFF4DD0E1), Color(0xFF0B5A6B), "Un paraíso solo para dos")
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
