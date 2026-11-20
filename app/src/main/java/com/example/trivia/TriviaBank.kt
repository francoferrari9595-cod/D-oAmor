package com.example.trivia

/** Categorías del concurso: código interno, emoji y nombre visible. */
internal data class TCat(val id: String, val emoji: String, val name: String)

internal val T_CATS = listOf(
  TCat("hist", "🏛️", "Historia"), TCat("geo", "🌍", "Geografía"), TCat("sci", "🔬", "Ciencia"),
  TCat("dep", "⚽", "Deportes"), TCat("mus", "🎵", "Música"), TCat("cin", "🎬", "Cine"),
  TCat("tec", "💻", "Tecnología"), TCat("cul", "🎓", "Cultura general"), TCat("mat", "➗", "Matemáticas"),
  TCat("lit", "📚", "Literatura"), TCat("ent", "🎮", "Entretenimiento")
)

internal fun tCat(id: String): TCat = T_CATS.firstOrNull { it.id == id } ?: T_CATS[7]

/** Pregunta: [diff] 1 = fácil, 2 = media, 3 = difícil. [alt] son otras formas válidas de la respuesta. */
internal data class TQ(val id: Int, val cat: String, val diff: Int, val q: String, val a: String, val alt: List<String> = emptyList(), val custom: Boolean = false)

internal fun tDiffName(d: Int) = when (d) { 1 -> "Fácil"; 2 -> "Media"; else -> "Difícil" }

/** Formato por línea: categoría|dificultad|pregunta|respuesta|alias1;alias2 */
private const val RAW = """
hist|1|¿En qué año llegó Cristóbal Colón a América?|1492|mil cuatrocientos noventa y dos
hist|1|¿Quién fue el primer presidente de los Estados Unidos?|George Washington|Washington
hist|1|¿Qué civilización construyó Machu Picchu?|Inca|los incas;imperio inca;civilización inca
hist|2|¿En qué año cayó el Muro de Berlín?|1989|mil novecientos ochenta y nueve
hist|2|¿Qué libertador nació en Caracas en 1783?|Simón Bolívar|Bolivar
hist|2|¿En qué año comenzó la Primera Guerra Mundial?|1914|mil novecientos catorce
hist|2|¿Qué faraón tiene una famosa máscara funeraria de oro hallada en 1922?|Tutankamón|Tutankamon;Tutankhamon
hist|3|¿Qué tratado de 1494 repartió el mundo entre España y Portugal?|Tratado de Tordesillas|Tordesillas
hist|3|¿Cómo se llamaba el último emperador azteca, capturado por Hernán Cortés?|Cuauhtémoc|Cuauhtemoc;Guatemuz
hist|3|¿En qué año se firmó la Declaración de Independencia de los Estados Unidos?|1776|mil setecientos setenta y seis
geo|1|¿Cuál es la capital de Francia?|París|Paris
geo|1|¿Cuál es la capital de Colombia?|Bogotá|Bogota
geo|1|¿Cuál es el océano más grande del planeta?|Océano Pacífico|Pacífico;Pacifico
geo|2|¿Cuál es la capital de Australia?|Canberra
geo|2|¿Cuál es el país más grande de África por superficie?|Argelia|Algeria
geo|2|¿En qué continente está el desierto del Sahara?|África|Africa
geo|2|¿Cuál es el río más caudaloso del mundo?|Amazonas|río Amazonas;rio amazonas
geo|3|¿Cuál es la capital de Kazajistán?|Astaná|Astana;Nur-Sultán;Nursultan
geo|3|¿Cómo se llama el lago navegable más alto del mundo, entre Perú y Bolivia?|Titicaca|lago Titicaca
geo|3|¿Cuál es el río más largo de Europa?|Volga|río Volga
sci|1|¿Cuál es el planeta más cercano al Sol?|Mercurio
sci|1|¿Qué gas necesitamos respirar para vivir?|Oxígeno|Oxigeno
sci|1|¿Cuántos huesos tiene un cuerpo humano adulto?|206|doscientos seis
sci|2|¿Cuál es el símbolo químico del oro?|Au
sci|2|¿Qué científico formuló la teoría de la relatividad?|Albert Einstein|Einstein
sci|2|¿Cuál es el órgano más grande del cuerpo humano?|La piel|piel
sci|3|¿Cuál es el elemento químico más abundante en el universo?|Hidrógeno|Hidrogeno
sci|3|¿Cómo se llama la partícula subatómica con carga eléctrica negativa?|Electrón|Electron
sci|3|¿Cuál es la velocidad aproximada de la luz en el vacío, en kilómetros por segundo?|300000|300.000;299792;299.792;trescientos mil
dep|1|¿Cuántos jugadores de un equipo están en la cancha en un partido de fútbol?|11|once
dep|1|¿En qué deporte se juega el torneo de Wimbledon?|Tenis
dep|1|¿Cada cuántos años se celebran los Juegos Olímpicos de verano?|4|cuatro
dep|2|¿Qué país ganó el Mundial de Fútbol de 2010?|España|Espana
dep|2|¿Cuántos puntos vale un tiro de tres en baloncesto?|3|tres
dep|2|¿Qué país ganó el Mundial de Fútbol de Qatar 2022?|Argentina
dep|3|¿Qué jugador argentino marcó el gol de la «mano de Dios» en el Mundial de 1986?|Diego Maradona|Maradona
dep|3|¿Cuántos torneos de Grand Slam ganó Roger Federer?|20|veinte
dep|3|¿En qué ciudad se celebraron los Juegos Olímpicos de 2016?|Río de Janeiro|Rio de Janeiro;Rio
mus|1|¿Cuántas cuerdas tiene una guitarra estándar?|6|seis
mus|1|¿Qué banda británica cantó «Hey Jude»?|The Beatles|Beatles;Los Beatles
mus|1|¿Quién es conocido como el «Rey del Pop»?|Michael Jackson
mus|2|¿Qué compositor, ya sordo, escribió la Novena Sinfonía?|Ludwig van Beethoven|Beethoven
mus|2|¿Qué cantante colombiana grabó «Hips Don't Lie»?|Shakira
mus|2|¿De qué región es originario el tango?|Río de la Plata|Argentina;Uruguay;Buenos Aires
mus|3|¿Qué banda lanzó el álbum «The Dark Side of the Moon»?|Pink Floyd
mus|3|¿Cómo se llama la ópera de Bizet cuya protagonista es una gitana?|Carmen
mus|3|¿Qué instrumento tocaba el músico Jimi Hendrix?|Guitarra|guitarra eléctrica;guitarra electrica
cin|1|¿Cómo se llama el vaquero de juguete de Toy Story?|Woody
cin|1|¿Qué película animada de Disney tiene como protagonista al león Simba?|El Rey León|Rey Leon;The Lion King
cin|1|¿Cómo se llama el niño mago de la cicatriz en la frente creado por J. K. Rowling?|Harry Potter
cin|2|¿Quién dirigió la película Titanic de 1997?|James Cameron|Cameron
cin|2|¿Qué actor interpreta a Jack Sparrow en Piratas del Caribe?|Johnny Depp|Depp
cin|2|¿Qué película surcoreana ganó el Óscar a mejor película en 2020?|Parásitos|Parasitos;Parasite
cin|3|¿Quién dirigió «El Padrino» (1972)?|Francis Ford Coppola|Coppola
cin|3|¿Quién dirigió la película «Psicosis» (1960)?|Alfred Hitchcock|Hitchcock
cin|3|¿Qué director mexicano ganó el Óscar a mejor director por «La forma del agua»?|Guillermo del Toro|del Toro
tec|1|¿Qué empresa creó el iPhone?|Apple
tec|1|¿Cómo se llama el sistema operativo móvil de Google?|Android
tec|1|¿Cuál es el buscador web más usado del mundo?|Google
tec|2|¿Quién fundó Microsoft junto a Paul Allen?|Bill Gates|Gates
tec|2|¿Qué significan las siglas USB?|Universal Serial Bus|Bus Universal en Serie
tec|2|¿Qué lenguaje de programación creó Guido van Rossum?|Python
tec|3|¿En qué año se lanzó el primer iPhone?|2007|dos mil siete
tec|3|¿Quién creó el núcleo del sistema operativo Linux?|Linus Torvalds|Torvalds
tec|3|¿Qué significan las siglas HTML?|HyperText Markup Language|Lenguaje de marcado de hipertexto;Lenguaje de Marcas de Hipertexto
cul|1|¿Cuántos días tiene un año bisiesto?|366|trescientos sesenta y seis
cul|1|¿Cuántos colores tiene el arcoíris?|7|siete
cul|2|¿Cuál es la moneda oficial de Japón?|Yen|el yen
cul|2|¿Cuál es la moneda oficial del Reino Unido?|Libra esterlina|Libra
cul|2|¿Cuál es el idioma más hablado del mundo como lengua materna?|Chino mandarín|Mandarín;Chino;Mandarin
cul|3|¿Qué país europeo tiene forma de bota?|Italia
cul|3|¿Cuál es el museo más visitado del mundo?|Louvre|Museo del Louvre;El Louvre
cul|3|¿Cómo se llama la disciplina que estudia los insectos?|Entomología|Entomologia
mat|1|¿Cuánto es 7 por 8?|56|cincuenta y seis
mat|1|¿Cuántos lados tiene un hexágono?|6|seis
mat|1|¿Cuál es la raíz cuadrada de 81?|9|nueve
mat|2|¿Cuánto es el 15 % de 200?|30|treinta
mat|2|¿Cuánto suman los ángulos internos de un triángulo, en grados?|180|ciento ochenta
mat|2|¿Cuál es el valor de pi redondeado a dos decimales?|3.14|3,14
mat|3|¿Cuál es el siguiente número primo después del 23?|29|veintinueve
mat|3|¿Cuánto es 2 elevado a la décima potencia?|1024|mil veinticuatro
mat|3|¿Cómo se llama el teorema que relaciona los lados de un triángulo rectángulo?|Teorema de Pitágoras|Pitágoras;Pitagoras
lit|1|¿Quién escribió «Don Quijote de la Mancha»?|Miguel de Cervantes|Cervantes
lit|1|¿Quién escribió «Cien años de soledad»?|Gabriel García Márquez|García Márquez;Garcia Marquez;Gabo
lit|2|¿Quién escribió «Romeo y Julieta»?|William Shakespeare|Shakespeare
lit|2|¿Quién escribió el cuento «El Aleph»?|Jorge Luis Borges|Borges
lit|2|¿A qué autor se le atribuye «La Odisea»?|Homero
lit|3|¿Quién escribió la novela «Rayuela»?|Julio Cortázar|Cortazar
lit|3|¿Quién escribió «Pedro Páramo»?|Juan Rulfo|Rulfo
lit|3|¿Quién escribió «Crimen y castigo»?|Fiódor Dostoievski|Dostoievski;Dostoyevski;Dostoyevsky
ent|1|¿Cómo se llama el fontanero rojo más famoso de Nintendo?|Mario|Super Mario;Mario Bros
ent|1|¿Cómo se llama el ratón más famoso de Disney?|Mickey Mouse|Mickey
ent|2|¿En qué saga de videojuegos aparece el héroe Link?|The Legend of Zelda|Zelda;La Leyenda de Zelda
ent|2|¿Cómo se llama el pueblo donde viven Los Simpson?|Springfield
ent|2|¿Cuántos Pokémon iniciales se podían elegir en la primera generación?|3|tres
ent|3|¿Qué serie de HBO enfrenta a las casas Stark y Lannister?|Juego de Tronos|Game of Thrones
ent|3|¿Quién creó el videojuego Minecraft?|Markus Persson|Notch
ent|3|¿Qué consola lanzó Sony en 1994?|PlayStation|PlayStation 1;PS1;PSX
hist|1|¿Qué imperio construyó el Coliseo de Roma?|Imperio Romano|Roma;los romanos;romanos
hist|1|¿En qué país están las pirámides de Giza?|Egipto
hist|1|¿Quién lideró la independencia de la India con la no violencia?|Mahatma Gandhi|Gandhi
hist|2|¿En qué año llegó el ser humano a la Luna por primera vez?|1969|mil novecientos sesenta y nueve
hist|2|¿Qué país fue gobernado por Napoleón Bonaparte como emperador?|Francia
hist|2|¿Cómo se llamaba el barco en el que viajó Charles Darwin en su famoso viaje científico?|Beagle|HMS Beagle
hist|3|¿En qué año cayó Constantinopla en manos de los otomanos?|1453|mil cuatrocientos cincuenta y tres
hist|3|¿Qué batalla de 1815 supuso la derrota final de Napoleón?|Waterloo|Batalla de Waterloo
hist|3|¿Qué reina gobernaba Inglaterra cuando fue derrotada la Armada Invencible en 1588?|Isabel I|Isabel Primera;Elizabeth I;Elizabeth Primera
geo|1|¿Cuál es el país más grande del mundo por superficie?|Rusia
geo|1|¿Cuál es la capital de España?|Madrid
geo|1|¿Qué océano baña la costa este de Estados Unidos?|Océano Atlántico|Atlántico;Atlantico
geo|2|¿Cuál es la montaña más alta del mundo sobre el nivel del mar?|Everest|Monte Everest
geo|2|¿Cuál es la capital de Canadá?|Ottawa
geo|2|¿Qué río atraviesa la ciudad de París?|Sena|río Sena;Seine
geo|3|¿Cuál es la capital de Turquía?|Ankara
geo|3|¿Cuál es el desierto no polar más seco del mundo?|Atacama|Desierto de Atacama
geo|3|¿Qué estrecho separa España de Marruecos?|Estrecho de Gibraltar|Gibraltar
sci|1|¿Cuál es el planeta más grande del sistema solar?|Júpiter|Jupiter
sci|1|¿En qué estado de la materia se encuentra el hielo?|Sólido|Solido;estado sólido
sci|1|¿Cuántos planetas tiene el sistema solar?|8|ocho
sci|2|¿Cuál es el símbolo químico del sodio?|Na
sci|2|¿Qué orgánulo de la célula produce la mayor parte de su energía?|Mitocondria|Mitocondrias;La mitocondria
sci|2|¿Cómo se llama la fuerza que nos mantiene pegados a la Tierra?|Gravedad|Fuerza de gravedad;La gravedad
sci|3|¿Cuál es el número atómico del carbono?|6|seis
sci|3|¿Qué científico formuló la ley de la gravitación universal?|Isaac Newton|Newton
sci|3|¿Qué molécula lleva la información genética de los seres vivos?|ADN|DNA;ácido desoxirribonucleico;acido desoxirribonucleico
dep|1|¿Cuántos jugadores de un equipo de baloncesto están a la vez en la cancha?|5|cinco
dep|1|¿En qué deporte se anotan «touchdowns»?|Fútbol americano|Futbol americano
dep|1|¿Qué deporte practica Rafael Nadal?|Tenis
dep|2|¿Qué país ha ganado más Copas del Mundo de fútbol masculino?|Brasil
dep|2|¿Cuántos jugadores tiene un equipo de rugby en el campo?|15|quince
dep|2|¿En qué deporte destaca el jamaiquino Usain Bolt?|Atletismo|Velocidad;Carreras;100 metros
dep|3|¿Qué país organizó el Mundial de Fútbol de 2014?|Brasil
dep|3|¿Cuántos aros tiene la bandera olímpica?|5|cinco
dep|3|¿Qué piloto alemán ganó siete títulos de Fórmula 1 con Benetton y Ferrari?|Michael Schumacher|Schumacher
mus|1|¿Cuántas teclas tiene un piano estándar?|88|ochenta y ocho
mus|1|¿Cómo se llama el instrumento de teclas blancas y negras con cuerdas y martillos?|Piano
mus|1|¿Cuántas notas forman la escala musical básica (do, re, mi…)?|7|siete
mus|2|¿Qué grupo sueco cantó «Dancing Queen»?|ABBA|Abba
mus|2|¿Qué cantante es conocida como la «Reina del Pop»?|Madonna
mus|2|¿Qué compositor austriaco escribió «La flauta mágica»?|Wolfgang Amadeus Mozart|Mozart
mus|3|¿Qué banda liderada por Freddie Mercury cantó «Bohemian Rhapsody»?|Queen
mus|3|¿Qué compositor escribió «Las cuatro estaciones»?|Antonio Vivaldi|Vivaldi
mus|3|¿Cómo se llama el instrumento de viento de bolsa típico de Escocia?|Gaita|Gaita escocesa
cin|1|¿Cómo se llama el robot de Pixar que limpia la Tierra?|WALL-E|Wall-E;Wall E
cin|1|¿Qué película animada tiene como protagonista a un pez payaso llamado Nemo?|Buscando a Nemo|Nemo;Finding Nemo
cin|1|¿Cómo se llama el ogro verde protagonista de una famosa saga animada?|Shrek
cin|2|¿Quién dirigió «Parque Jurásico» (1993)?|Steven Spielberg|Spielberg
cin|2|¿Qué actor protagonizó «Forrest Gump»?|Tom Hanks|Hanks
cin|2|¿Cómo se llama el mago de «El Señor de los Anillos» interpretado por Ian McKellen?|Gandalf
cin|2|¿Qué actor interpreta a Iron Man en el universo cinematográfico de Marvel?|Robert Downey Jr.|Downey;Robert Downey Junior;Robert Downey
cin|3|¿Quién dirigió «Pulp Fiction»?|Quentin Tarantino|Tarantino
cin|3|¿Qué película ganó 11 premios Óscar en 1998?|Titanic
cin|3|¿Quién dirigió «El laberinto del fauno»?|Guillermo del Toro|del Toro
tec|1|¿Cómo se llamaba la red social del pajarito azul antes de ser «X»?|Twitter
tec|1|¿Qué dispositivo se usa para mover el cursor y hacer clic en una computadora?|Ratón|Raton;Mouse
tec|1|¿Cómo se llama el asistente de voz de Apple?|Siri
tec|2|¿Qué empresa fundó Jeff Bezos?|Amazon
tec|2|¿Qué significan las siglas «PDF»?|Portable Document Format|Formato de documento portátil;Formato de Documento Portable
tec|2|¿Quién cofundó Apple junto a Steve Wozniak?|Steve Jobs|Jobs
tec|3|¿Qué significan las siglas «CPU»?|Unidad Central de Procesamiento|Central Processing Unit;Unidad Central de Proceso
tec|3|¿En qué año se fundó Google?|1998|mil novecientos noventa y ocho
tec|3|¿Qué lenguaje de programación creó Brendan Eich en 1995 para la web?|JavaScript|Java Script
cul|1|¿Cuántos días tiene febrero en un año no bisiesto?|28|veintiocho
cul|1|¿Cuántas horas tiene un día?|24|veinticuatro
cul|1|¿Cuál es el animal terrestre más grande del mundo?|Elefante|Elefante africano;El elefante
cul|2|¿Qué idioma se habla oficialmente en Brasil?|Portugués|Portugues
cul|2|¿De qué fruta se obtiene el vino?|Uva|La uva;Uvas
cul|2|¿Cómo se llama la ciencia que estudia los fósiles?|Paleontología|Paleontologia
cul|3|¿Cuál es el hueso más largo del cuerpo humano?|Fémur|Femur
cul|3|¿Qué país tiene una bandera blanca con un círculo rojo en el centro?|Japón|Japon
cul|3|¿Cómo se llama el sistema de lectura y escritura táctil para personas ciegas?|Braille|Sistema Braille
mat|1|¿Cuánto es 9 más 6?|15|quince
mat|1|¿Cuánto es 12 dividido entre 3?|4|cuatro
mat|1|¿Cuántos grados mide un ángulo recto?|90|noventa
mat|2|¿Cuánto es 25 al cuadrado?|625|seiscientos veinticinco
mat|2|¿Cuántos lados tiene un octágono?|8|ocho
mat|2|¿Cómo se llama el polígono de tres lados?|Triángulo|Triangulo
mat|3|¿Cuánto es la raíz cuadrada de 144?|12|doce
mat|3|¿Cuánto es 7 factorial dividido entre 6 factorial?|7|siete
mat|3|¿Cuántos grados suman los ángulos internos de un cuadrilátero?|360|trescientos sesenta
lit|1|¿Quién escribió «El principito»?|Antoine de Saint-Exupéry|Saint-Exupery;Saint Exupery;Saint-Exupéry
lit|1|¿Cómo se llama el famoso detective creado por Arthur Conan Doyle?|Sherlock Holmes|Holmes
lit|1|¿Quién escribió «Las aventuras de Tom Sawyer»?|Mark Twain|Twain
lit|2|¿Quién escribió «Veinte mil leguas de viaje submarino»?|Julio Verne|Jules Verne;Verne
lit|2|¿Quién escribió «1984»?|George Orwell|Orwell
lit|2|¿Quién escribió «Orgullo y prejuicio»?|Jane Austen|Austen
lit|3|¿Quién escribió «La casa de los espíritus»?|Isabel Allende|Allende
lit|3|¿Quién escribió «La Divina Comedia»?|Dante Alighieri|Dante
lit|3|¿Quién escribió «Los miserables»?|Victor Hugo|Víctor Hugo;Hugo
ent|1|¿Cómo se llama el erizo azul más veloz de los videojuegos?|Sonic|Sonic the Hedgehog
ent|1|¿Cómo se llama el protagonista de «Dragon Ball»?|Goku|Son Goku
ent|1|¿Qué personaje de Disney pierde un zapato de cristal en un baile?|Cenicienta|Cinderella
ent|2|¿Cómo se llama el reino de «Frozen» donde vive Elsa?|Arendelle
ent|2|¿Cómo se llama la princesa a la que Mario rescata?|Peach|Princesa Peach
ent|2|¿Qué serie animada tiene a un niño llamado Bart?|Los Simpson|Simpson;The Simpsons
ent|3|¿Cómo se llama el villano de Star Wars que viste de negro y respira con ayuda mecánica?|Darth Vader|Anakin Skywalker;Vader
ent|3|¿Cómo se llama la ciudad ficticia donde vive Batman?|Gotham|Ciudad Gótica;Gotham City;Ciudad Gotica
ent|3|¿Qué estudio de animación creó «Toy Story»?|Pixar
"""

internal val T_BANK: List<TQ> by lazy {
  RAW.trim().lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
    val p = line.split("|")
    TQ(i + 1, p[0], p[1].toInt(), p[2], p[3], p.getOrNull(4)?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList())
  }
}
