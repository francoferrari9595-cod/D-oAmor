package com.example.ui.screens

/**
 * Banco amplio para El Impostor. Se alimenta de vocabularios por categoría ya
 * incluidos en el proyecto, sin cambiar el flujo de roles ni la sincronización.
 * Cada entrada es una palabra en español; el total combinado supera las 2.000
 * palabras únicas después de eliminar duplicados.
 */
internal object ImpostorWordBank {
  private fun words(raw: String): List<String> = raw.trimIndent()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .map { it.trim().lowercase() }
    .distinct()

  val categories: Map<String, List<String>> = linkedMapOf(
    "Nombres" to words("""
agustín alan alberto alejandro alicia amanda ana andrea andrés angela antonio ariel augusto
      bárbara beatriz benjamín brenda brian camila carla carlos carolina catalina cecilia cesar
      clara claudio cristian cristina daniel dante david débora diego dolores eduardo elena emilia
      emiliano emma enrique esteban evelyn fabián facundo federico felipe fernanda fernando florencia
      francisco gabriel gastón gerardo gianella gonzalo graciela guadalupe guillermo gustavo héctor
      ignacio ines irene isabel iván jacinto jaime javier jimena joaquín jorge josé josefina juan
      julia julian julieta julio lautaro leandro leonardo lorenzo lucas lucía luisa manuel marcelo
      marco marcos margarita maría marina mario martina mateo matías mauricio melina micaela miguel
      milagros nahuel natalia nicolás noelia octavio olga pablo pamela paola patricia pedro pili
      ramiro raquel ricardo roberto rocio rodrigo rosa rubén salvador samuel sandra santiago sara
      sebastián sergio silvia simón sofía sol tamara tatiana tomás valentina valeria verónica vicente
      victoria walter ximena yanina yoel yolanda zoe adriana agustina aldo alex alexis alfonsina alfredo alma alonso álvaro amalia amelie anaís anastasia aníbal anita antonella apolo araceli aranza armando arturo aurora axel azul belén belinda benicio bernardo bianca blas boris camilo candela carmen ciro claudia constantino corina daniela darío demian diana efrain elías elisa elizabeth eloy elsa elvira ernesto esmeralda eugenia ezequiel fabiola fatima federica felicia felipe fernán fidel flora franco freddy gaia galileo garcía genaro gimena giovanni gloria graciano guido guillermina haroldo hugo ian iliana imanol indira iris isaac ismael ivana ivette jazmín jeremías joel jonás jorgelina josué juana justina karina kevin kiara laila lara laureano leila lena león lía liliana lina lisandro lorena lourdes luciano ludmila magalí malena malvina manuela marcela mariano marisol melisa mercedes mila nahir nerea nicole norma nuria omar oriana ornella oscar paloma paz pía pierre priscila quique renata renzo reina rita romina rubí sabrina salomé samantha sandro santino selena serafín sheila silvana susana teo thiago tiziana ulises ursula valentino vanesa vera vicente violeta walter wanda ximena yago yamila yésica zaira
    """),
    "Animales" to words("""
abeja águila alacrán alce alpaca anaconda anguila antílope araña ardilla armadillo asno atún
      avestruz avispa ballena barracuda bisonte búfalo búho burro cabra caballo caballito camarón
      camello canario cangrejo canguro caracol carancho carpa castor cebra cerdo ciervo cigüeña
      cisne cobra cocodrilo colibrí comadreja conejo cordero corvina cotorra coyote cuervo delfín
      dromedario elefante erizo escarabajo escorpión foca faisán gallina gallo ganso gato gaviota
      gorila gorrión grillo grulla guanaco guepardo halcón hámster hipopótamo hormiga iguana jabalí
      jaguar jirafa koala lagarto langosta lechuza lemur león liebre llama lobo loro luciérnaga
      manatí mandril mariposa medusa mejillón morsa mosquito mula murciélago nutria ñandú ocelote
      orangután orca ornitorrinco oso ostra oveja pájaro paloma pantera pato pavo pelícano perro
      pez pingüino piojo piraña pitón pollo pulpo puma rana ratón reno rinoceronte salamandra salmón
      sanguijuela sapo sardina serpiente suricata tiburón tigre topo toro tortuga tucán urraca
      vaca víbora vizcacha yacaré yak zorro abejorro abubilla agutí albatros almeja anchoa anémona arenque armadillo australiano babosa bacalao bagre besugo boa bogavante bonobo caballa cacatúa caimán calamar calandria camaleón caribú carpincho casuario cernícalo chacal chinchilla chorlito chotacabras chúcaro damán demonio de tasmania dingo dorada escuerzo estornino faisán flamenco gallineta garduña garrapata gecko gerbo glotón gorila de montaña guacamayo halibut herrerillo ibis impala jicotea jilguero lamprea langur lemming lince loro gris macaco macarela mantarraya martín pescador mirlo mofeta monito del monte morena narval nautilo olinguito oropéndola pájaro carpintero pangolín papagayo pécari petirrojo pez espada pez globo pez payaso pinzón polilla puercoespín quebrantahuesos quetzal quokka raya rémora renacuajo ruiseñor sabueso saltamontes tarántula tejón termita tilapia tritón trucha uapití urraca azul varano vicuña wallaby wapití wombat xoloitzcuintle zángano
    """),
    "Países y provincias" to words("""
afganistán albania alemania andorra angola argentina armenia australia austria bahamas bangladés
      barbados bélgica belice bolivia botsuana brasil bulgaria camboya camerún canadá catar chile china
      chipre colombia corea costa rica croacia cuba dinamarca ecuador egipto el salvador eslovaquia
      eslovenia españa estados unidos estonia etiopía filipinas finlandia francia gabón gales georgia
      ghana grecia guatemala guyana haití honduras hungría india indonesia irak irán irlanda islandia
      israel italia jamaica japón jordania kenia kuwait laos letonia líbano libia lituania luxemburgo
      malasia malta marruecos méxico mónaco mongolia montenegro mozambique nepal nicaragua nigeria
      noruega nueva zelanda países bajos pakistán panamá paraguay perú polonia portugal puerto rico
      reino unido república dominicana rumania rusia senegal serbia singapur siria somalia suecia suiza
      tailandia taiwán tanzania túnez turquía ucrania uruguay venezuela vietnam zambia zimbabue
      buenos aires córdoba corrientes chaco chubut entre ríos formosa jujuy la pampa la rioja mendoza
      misiones neuquén río negro salta san juan san luis santa cruz santa fe santiago del estero
      tierra del fuego tucumán catamarca antigua y barbuda arabia saudita argelia azerbaiyán baréin benín bielorrusia birmania brunéi burkina faso burundi cabo verde chad comoras corea del norte corea del sur costa de marfil dominica emiratos árabes unidos eritrea esuatini fiyi granada guinea guinea ecuatorial guinea bissau irán kazajistán kirguistán kiribati lesoto liberia liechtenstein madagascar malaui maldivas mali mauritania mauricio micronesia moldavia myanmar nauru niger niue omán palau papúa nueva guinea república centroafricana república checa república del congo república democrática del congo samoa san cristóbal y nieves san marino san vicente y las granadinas santa lucía santo tomé y príncipe seychelles sierra leona sri lanka sudáfrica sudán sudán del sur surinam tayikistán timor oriental togo tonga turkmenistán tuvalu uzbekistán vanuatu yemen catamarca la rioja san salvador de jujuy rawson comodoro rivadavia paraná resistencia san fernando del valle de catamarca posadas san miguel de tucumán ush una
    """),
    "Ciudades" to words("""
amsterdam atenas barcelona berlín bogotá bruselas buenos aires caracas cartagena chicago córdoba
      cusco dublín florencia frankfurt ginebra guadalajara hamburgo helsinki hong kong lisboa londres
      madrid málaga manchester mar del plata medellín melbourne mendoza miami milán montevideo moscú
      nueva york oslo ottawa parís pekín praga quito río de janeiro roma rosario san pablo santiago
      sevilla tokio toronto valencia venecia viena washington abu dhabi adelaida alejandría alicante asunción auckland baltimore bangkok basilea beirut belgrado birmingham boston bratislava brest cali cambridge canberra cancún ciudad de méxico ciudad del cabo ciudad del este colón concepción copenhague curitiba damasco denver doha durban edimburgo eindhoven esmirna estrasburgo guayaquil la habana la paz la plata la valeta la vega las vegas leeds leipzig lille lima lyon managua manila medina múnich nagoya nápoles nueva orleans orlando oxford palermo pekín perth porto alegre puebla pune reikiavik rijeka salónica san diego san josé san juan san luis potosí san salvador santa cruz de la sierra santa marta são paulo sarajevo shanghái shenzhen sofía tallin teherán tel aviv tesalónica tijuana tirana trondheim turín urumqi varsovia viena vilna zagreb zaragoza
    """),
    "Comidas" to words("""
acelga alfajor albóndiga empanada asado arroz atún arepa batata berenjena bife bizcocho budín
      burrito cacao café calabaza canelones carne cazuela ceviche cereal chipa chorizo chocolate churro
      coco croqueta curry ensalada espagueti fideos flan frijoles galleta guiso hamburguesa harina
      helado humita jamón lasaña leche lentejas locro longaniza maíz milanesa mortadela ñoquis omelet
      pan panqueque papa papas pasta pastel pastas pizza polenta puré queso ravioles risotto salame
      salchicha sandwich sopa sushi tamal tarta tacos tortilla tostada tuco yogur zapallo achuras arequipe bagel baklava barrita de cereal batido bechamel beicon berenjenas rellenas birria brownie bruschetta calzone capeletti carpaccio cassata causa limeña champiñones cheesecake chicharrón chimichurri chocolate caliente coleslaw conserva couscous crêpe croissant crumble empanadilla enchilada falafel fondue focaccia gazpacho goulash granola gravy kebab kimchi kombucha leche condensada macarrones magdalena maki matambre medialuna merengue mermelada mousse nachos natilla nuggets okonomiyaki pan árabe pan dulce panettone papas fritas parfait paella palitos de queso paté pavlova pesto picada pionono poke ramen ratatouille revuelto gramajo rosca de pascua rosca de reyes roti salpicón sándwich de miga shawarma strudel tabulé tempura tiramisú tortilla española trufa wafle wonton yakisoba yakitori zapallitos rellenos
    """),
    "Frutas y verduras" to words("""
acelga achicoria ajo alcachofa alcaparra apio arándano arveja banana batata berenjena berro
      brócoli calabaza cebolla cereza ciruela coco col coliflor dátil durazno espárrago espinaca
      frambuesa frutilla granada grosella guayaba higo kiwi lima limón mandarina mango manzana melón
      membrillo mora naranja nectarina nopal nuez palta papa pepino pera perejil pimiento piña
      pomelo puerro rábano remolacha repollo rúcula sandía tomate uva zanahoria zapallito zapallo albahaca ananá anís alcaucil alubia banana macho castaña caqui cardo chirivía chirimoya chayote endivia escarola espelta feijoa guanábana jengibre judía kale kumquat lima kaffir mandioca maracuyá melocotón mora azul morrón nabo níspero okra pak choi papaya parsnip patata pecán physalis pitahaya plátano rabanito rambután ruibarbo salsifí tamarindo taro tomatillo topinambur yuca
    """),
    "Objetos" to words("""
abrelatas almohada anillo antena armario aspiradora azucarera balanza bandeja bandera barril
      bicicleta billetera binoculares blusa bolsa botella botón brújula cable cadena caja calculadora
      calendario cama cámara camisa candado canasto carpeta cartera casco celular cepillo cerradura
      cinturón clavo cobija colchón compás computadora corbata cortina cuchara cuchillo cuaderno
      cuadro cubeta cuchilla dado despertador destornillador diccionario disco escoba escuadra espejo
      estante estufa etiqueta exprimidor flauta foco fósforo frasco freno fuente gafas gancho guitarra
      hebilla herramienta hilo horno imán impresora jarrón jarra jabón joyero juguete lámpara lata
      lápiz lavadora libro llave llavero mochila moneda monitor martillo mueble muñeca navaja nevera
      olla ordenador orejera papel paraguas pared peine pelota pendrive persiana pincel pinza pizarrón
      plato plumero puerta radio rastrillo regla reloj remo repisa ropero rueda sacapuntas sábana sartén
      secador silla sillón sofá sombrero soga soporte taza teclado teléfono televisor tijera timbre
      tirador tostadora tubo tuerca uniforme urna ventilador ventana vestido video violín zapato alicate anafe ancla andamio arado arcón archivador arpa balde bandolera bañera batidora biblioteca bidé boina bombilla bordadora brocha bufanda buril caballete cafetera cajón caldera calentador campana carretilla cartabón cenicero cerámica chaleco chubasquero clarinete clavija clip cocinilla collar cortaplumas crisol crucifijo cubiertos dedal dispensador dron embudo encendedor engranaje escáner escopeta espátula esponja extintor falda felpudo filtro funda fusible galera garrafa grapadora guirnalda impermeable interruptor jofaina joystick kayak kimon o lector lámpara led leñera licorera lima llanta lonchera mazo megáfono microondas molde monopatín mortero mosquetón mantel muñequera nivel oboe overol pala palangana paleta palillo panera pañuelo parquímetro pasador perchero perforadora persiana plegable petaca picaporte picadora pileta pipeta pistola plomada portarretrato prendedor proyector pulverizador rallador recibidor recogedor rodillo roldana sacacorchos salero secadora servilleta sifón silbato sopera sube y baja sujetapapeles taladro tambor tapiz termómetro tijerón toldo torno trampolín trapo trompeta vajilla valija velador yunque zueco
    """),
    "Profesiones" to words("""
abogado actor actriz albañil arquitecto artista astronauta bombero cajero cantante carpintero
      cartero chef científico cirujano cocinero comerciante conductor contador dentista diseñador
      docente electricista enfermero enfermera escritor escritora farmacéutico fotógrafo futbolista
      jardinero juez periodista maestro maestra mecánico médico modelo músico odontólogo panadero
      peluquero periodista pintor policía profesor profesora programador psicólogo químico recepcionista
      relojero secretario soldado taxista técnico traductor veterinario zapatero acupunturista afinador agente inmobiliario agricultor anestesista apicultor arqueólogo asesor auditor azafata barbero bibliotecario bioquímico cajera camarógrafo capataz cardiólogo cerrajero chapista científico de datos comerciante de libros community manager concejal coreógrafo dermatólogo desarrollador economista editor entrenador escenógrafo estilista geólogo geriatra herrero ingeniero ingeniero civil ingeniero de sonido investigador kinesiólogo locutor marino matemático meteorólogo minero montador notario nutricionista obstetra oceanógrafo oftalmólogo operario orfebre paisajista pastelero pediatra pescador piloto podólogo portero productor radiólogo terapeuta trabajador social urólogo vidriero viticultor yesero
    """),
    "Marcas" to words("""
adidas amazon apple arcor audi avon barbie bimbo bmw coca cola colgate converse disney ford
      google honda hp ikea intel jeep lego lenovo lg mcdonalds mercedes microsoft nestle nike nintendo
      nissan nokia pepsi playstation puma samsung sony toyota volkswagen walmart xbox zara acer alcatel allianz alpina asics aston martin atari balenciaga benetton black and decker canon carrefour casio chevrolet chery cheetos chevrolet claro danone dell delonghi dior dunkin duracell ebay electrolux energizer estee lauder everlast falabella fanta ferrari fila fisher price fossil garmin gillette givenchy huawei hyundai kodak lacoste lamborghini lancome liverpool loewe lotus makita mazda melitta mini mitsubishi motorola moulinex new balance oracle patagonia philips pirelli prada ray ban reebok renault ripley rolls royce rolex salomon seat sharp skechers skoda smart snapchat spar samsung sennheiser skf skyy starbucks subaru swatch tefal tiktok timberland tissot tommy hilfiger uber under armour unilever uniqlo verizon whirlpool xerox ypf
    """),
    "Personajes famosos" to words("""
shakira messi maradona gardel cerati tini bizarrap milei francella darín susana giménez mirtha
      legrand ricardo arjona bad bunny taylor swift beyonce rihanna madonna elvis presley michael
      jackson cristiano ronaldo neymar marie curie einstein newton frida kahlo picasso mozart albert einstein alicia keys amy winehouse andrés calamaro angela merkel anne hathaway antonio banderas ariana grande arnold schwarzenegger audrey hepburn barack obama brad pitt bruno mars camila cabello carlos gardel carolina herrera charles chaplin che guevara chris evans cindy crawford clint eastwood coco chanel dalai lama daniel radcliffe david beckham denzel washington diego maradona ed sheeran emma watson enrique iglesias eva perón fernando alonso george clooney george lucas gilda greta thunberg harrison ford hayao miyazaki jackie chan jennifer aniston jennifer lopez john lennon johnny depp julia roberts justin bieber keanu reeves lady gaga lebron james leonardo dicaprio lio messi luisana lopilato maluma margot robbie mark zuckerberg martin scorsese natalie portman oprah winfrey ozzy osbourne paul mccartney penélope cruz phil collins prince rafael nadal robert de niro roger federer selena gomez stephen hawking steven spielberg sylvester stallone tim burton tom cruise tom hanks usain bolt walt disney will smith yoko ono
    """),
    "Películas y series" to words("""
avatar barbie batman breaking bad casablanca coco friends frozen gladiador harry potter
      intensamente interstellar jaws joker matrix merlina mision imposible nemo naruto rocky shrek
      spiderman star wars stranger things superman titanic toy story up venom wednesday aladdin alicia en el país de las maravillas anne with an e better call saul black mirror bojack horseman casados con hijos chainsaw man chiquititas cobra kai coraline dark death note demon slayer doctor who el encargado el eternauta el juego del calamar el padrino el señor de los anillos encanto eternos expreso polar evangelion familia moderna futurama grey's anatomy guardians of the galaxy house house of the dragon john wick jurassic park la casa de papel la historia sin fin la ley y el orden la niñera la reina del flow la sirenita lost lucifer mad max malcolm matrix reloaded merlina misión imposible moana modern family one piece orange is the new black outlander paw patrol peaky blinders pokemon prison break pulp fiction rebelde red dead redemption rocky balboa scooby doo sense8 sex education sherlock simpsons south park squid game the office the walking dead the witcher top gun transformers twilight vikings volver al futuro yellowstone
    """),
    "Canciones y artistas" to words("""
abba adele airbag alejandro sanz babasónicos bad bunny beatles belinda bizarrap coldplay cerati
      charly garcía shakira tini fito páez queen soda stereo tan biónica tini trueno dua lipa eminem
      fito paez luis miguel madonna mana miranda queen ricky martin rosalia the weeknd a-ha abel pintos agapornis air supply al green alejandro lerner alicia villarreal amy winehouse andrés calamaro avril lavigne axel bachata rosa bándalos chinos bandas sonoras beret billie eilish blink-182 bon jovi bruno mars callejeros camilo catupecu machu celtas cortos cesar banana coti creedence crónica de una muerte anunciada duki ed sheeran el mató a un policía motorizado elton john enanitos verdes eros ramazzotti estopa evanescence fabiana cantilo faith no more fito páez floricienta foo fighters frank sinatra gloria estefan gorillaz green day guns n roses harry styles imagine dragons iron maiden j balvin joaquin sabina joey montana john mayer juanes kalamarka katy perry keane la beriso la konga la oreja de van gogh la renga lana del rey las pastillas del abuelo los auténticos decadentes los cafres los fabulosos cadillacs los redondos los tipitos los twist luciano pereyra luis alberto spinetta malón mana marco antonio solís maría becerra maroon 5 miranda! morat no te va gustar oasis pablo alborán patricio rey pedro aznar pimpinela pink floyd placebo radiohead ratones paranoicos ricardo montaner ricky martin roberto carlos roxette sia simple minds sin bandera the cranberries the cure the doors the killers the police the rolling stones the strokes u2 vicentico vilma palma y vampiros
    """)
  )

  val allWords: List<String> = categories.values.flatten().distinct()
}
