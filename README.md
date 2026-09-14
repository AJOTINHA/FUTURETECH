# FUTURETECH

Mod de máquinas, energia e automação industrial para **Minecraft Java 26.2**, com **NeoForge 26.2.0.82** e **Java 25**.

## Estado atual

**Plates e gears:** oito componentes na aba FUTURETECH: placa e engrenagem de ferro, ouro, cobre e netherita (`<metal>_plate` e `<metal>_gear`). As placas são chapas finas com bordas chanfradas; as engrenagens têm oito dentes e furo central real. Os modelos 3D usam as texturas dos respectivos blocos de metal do Minecraft, ficam em `models/item/metal_parts/` e podem ser recriados com `tools/Generate-MetalParts.ps1`. Há traduções PT/EN e tags `c:plates/<metal>` e `c:gears/<metal>`. São componentes preparados para futuras receitas, ainda sem fabricação ou uso em receitas.

As texturas de máquinas ficam organizadas em `assets/futuretech/textures/block/`: `machine/` guarda as faces compartilhadas; `solid_fuel_generator/`, `electric_furnace/`, `crusher/`, `battery/` e `assembler/` guardam os recursos de cada máquina. O tanque reutiliza a estrutura e as conexões da pasta `battery/`.

As faces compartilhadas e as frentes do gerador, da fornalha e do triturador possuem cópias nas subpastas `mk2/` (quinas amarelas), `mk3/` (vermelhas) e `mk4/` (azul-ciano). Somente os cantos externos da carcaça mudam de cor; rebites, painéis e animações originais são preservados. Os kits de upgrade selecionam essas texturas no mundo, no inventário e na configuração dos lados. Fontes ImageGen, prompts e exportador ficam em `art/machine_tiers/`.

Versão `0.5.0`, com identificação `futuretech`, aba criativa própria e traduções em português e inglês.
Os **Kits de Upgrade MK2, MK3 e MK4** aparecem na aba FUTURETECH como maletas 3D amarelas, vermelhas e ciano. Segure o kit e use **Shift + clique direito** no Gerador a Combustível Sólido, na Fornalha Elétrica ou no Triturador. A sequência é MK1 → MK2 → MK3 → MK4, usando o kit correspondente a cada etapa. Cada aplicação válida consome um kit no sobrevivência; no criativo, ele permanece. Kits de nível errado e blocos incompatíveis mostram uma mensagem e não consomem o item. O upgrade mantém a mesma máquina, com inventário, energia, progresso e configuração dos lados. O MK é salvo no estado do bloco e acompanha o item ao quebrar ou recolher com a chave; ao recolocar, o nível e o visual são restaurados. O nome do item também mostra o MK. O nível libera slots de melhoria e, no Triturador e na Fornalha, faixas de processamento (veja [Melhorias](#melhorias) e [Faixas por MK](#faixas-por-mk)).

As receitas dos kits usam uma carcaça no centro, quatro materiais A nos cantos, dois B acima/abaixo e dois C nas laterais: MK2 usa A = ferro, B = ouro e C = redstone; MK3 usa A = ouro, B = bloco de redstone e C = quartzo; MK4 usa A = diamante, B = pérola do Ender e C = bloco de redstone. Os modelos e recursos são reproduzíveis com `tools/Generate-UpgradeKits.ps1`.

O **Speed Upgrade** (`futuretech:speed_upgrade`) é um módulo 3D com textura própria, setas ciano e contatos dourados. Aparece na aba FUTURETECH e pode ser instalado nos slots de melhorias, um por slot, com persistência ao salvar o mundo. A receita usa quatro lingotes de ferro nos cantos, dois de ouro no centro superior e inferior, redstone nas laterais e açúcar no centro. Por enquanto o item não altera a velocidade das máquinas. A fonte da textura e seu exportador ficam em `art/speed_upgrade/`.

O **Filtro** (`futuretech:filter`) é um item destinado ao cabo de itens. Aparece na aba FUTURETECH, ao lado do cabo de itens, e usa um modelo 3D com moldura espessa, grade metálica vazada, parafusos e marcador ciano nos dois lados. O inventário mostra a peça levemente inclinada para evidenciar a espessura. A receita rende um filtro com quatro pepitas de ferro nos cantos e cinco linhas nas demais posições da bancada. Colocado no slot de um conector do cabo de itens, decide o que atravessa aquele conector, nas duas direções: a engrenagem ao lado do slot abre a lista de nove itens e o botão Permitir/Bloquear. Permitir deixa passar só o que está na lista (vazia, não passa nada); Bloquear deixa passar tudo menos o listado. A comparação olha só o tipo do item, então uma picareta gasta continua sendo picareta. A lista e o modo ficam no próprio item, em componentes, e viajam com ele. O exportador do modelo e as fontes visuais ficam em `art/filter/`.

A **Carcaça de Máquina** está registrada na aba FUTURETECH. Usa a textura padrão `machine_side` nas seis faces, a mesma usada nos cinco lados não frontais das máquinas, e pode ser fabricada com oito barras de ferro ao redor de um espaço vazio. Requer picareta de pedra ou superior para soltar o item.

O **Gerador a Combustível Sólido** é a primeira máquina funcional. Clique com o botão direito para abrir sua interface e coloque carvão, carvão vegetal ou um combustível de madeira no slot. Aceita troncos, madeiras descascadas, tábuas, gravetos, ferramentas de madeira, portas, cercas, escadas, lajes, placas, barcos, baús e outros itens de madeira, incluindo bambu e madeiras do Nether. Também é possível usar Shift + clique para mover combustível do inventário.

O nome acima do slot acompanha o item inserido e o idioma do jogo. Quando o slot está vazio, aparece “Vazio” (“Empty” em inglês). A barra de combustível permanece visível, inclusive enquanto termina a queima do último item consumido.

- Gera **20 FE/t** (400 FE/s a 20 ticks por segundo).
- Cada carvão fornece **1.600 ticks de geração**, totalizando **32.000 FE**.
- Os combustíveis de madeira usam a duração da fornalha quando disponível: troncos e tábuas geram normalmente **6.000 FE**, lajes **3.000 FE** e gravetos **2.000 FE**. Peças de madeira sem valor de fornalha, como as do Nether, recebem uma duração por categoria.
- A barra de queima acompanha a duração do combustível em uso, mesmo após salvar e carregar o mundo.
- Armazena **20.000 FE** e pausa quando não há espaço para mais um tick de geração; o combustível em queima fica preservado.
- Envia automaticamente até **80 FE/t**, no total, para blocos adjacentes que aceitem energia pela API do NeoForge. Todas as faces entregam energia sem precisar de configuração, inclusive as que estão em Nenhum; o gerador não recebe energia. A configuração de lado do gerador serve só para escolher por onde entra combustível. O limite vale por tick e não por chamada: blocos vizinhos que puxem energia por conta própria dividem os mesmos 80 FE/t com o envio automático.
- Salva energia, combustível e progresso de queima ao sair do mundo ou descarregar o chunk.
- Mostra energia e geração atual na interface. A frente acende durante a geração; um comparador mede o nível da reserva.
- A energia é sincronizada para a interface em duas metades de 16 bits, então capacidades acima de 32.767 FE (como a futura bateria) não estouram o pacote de dados do menu.
- A GUI mantém fundo cinza liso, cabeçalho azul escuro e slots simples, com os quatro cantos externos recortados em pixels no estilo do Minecraft, sombra externa discreta e relevo fino nos slots. As barras têm degradês contínuos, movimento suave e um brilho leve.

A receita usa cinco barras de ferro, duas fornalhas, uma carcaça de máquina e um pó de redstone:

```text
Ferro     Ferro      Ferro
Fornalha  Carcaça    Fornalha
Ferro     Redstone   Ferro
```

O gerador usa texturas próprias de 32 × 32 pixels, com carcaça de aço cinza, moldura escura, detalhes discretos em azul e uma abertura que acende em laranja durante a geração. O visual industrial simples é inspirado no Thermal Expansion. Ao quebrá-lo com picareta de pedra ou superior, ele solta o bloco e o combustível restante no inventário; a energia e o combustível já em queima não são preservados no item.

A lista de peças de madeira é extensível pela tag de itens `futuretech:generator_wooden_fuels`, que inclui as categorias de madeira do Minecraft. Madeiras de outros mods que pertençam a essas tags também são aceitas; itens adicionais podem ser incluídos por datapack. Itens que só contêm madeira em parte (camas, estandartes, tochas, quadros, molduras, jukebox, sensor de luz e colmeias naturais) ficam de fora de propósito.

A **Bateria MK1** (`futuretech:battery_mk1`) armazena a energia do gerador. Clique com o botão direito para ver a reserva e as taxas de entrada e saída do último tick.

- Armazena **100.000 FE**; recebe e envia até **200 FE/t** cada, por tick e não por chamada.
- Cada face é configurável (veja "Configuração de lados"); a orientação voltada para o jogador ao colocar é a referência do painel. Uma face de bateria nunca é entrada e saída ao mesmo tempo, para um cabo não devolver à bateria a energia dela mesma. Uma bateria nunca envia diretamente para outra bateria.
- Ao quebrar, o item sai com a carga guardada num componente de dados (`futuretech:energy`); a tooltip mostra "x / 100000 FE" e o item exibe uma barra de carga. Ao colocar de novo, a energia volta para o bloco. Baterias vazias não carregam o componente e continuam empilháveis.
- Um comparador mede o nível da reserva.

A receita usa quatro barras de ferro, três pós de redstone, uma carcaça de máquina e uma barra de ouro:

```text
Ferro     Redstone   Ferro
Redstone  Carcaça    Redstone
Ferro     Ouro       Ferro
```

A bateria MK1 usa uma estrutura 3D vazada, com doze vigas de metal escuro e cantos reforçados. Cada face em Nenhum permanece aberta. Entrada acrescenta uma chapa de aço com furo quadrado e aro azul; Saída usa o mesmo modelo com aro laranja, nas seis orientações. As vigas mantêm o metal original. No centro, uma esfera azul-ciano com malha hexagonal luminosa gira uma volta a cada 20 segundos, sem encostar nas vigas. A colisão física acompanha apenas a estrutura; as aberturas são reais, sem painéis transparentes. A área de seleção e clique ocupa o bloco inteiro, impedindo interagir com blocos atrás da bateria através das aberturas. Faces configuradas como Saída exibem partículas luminosas em espiral, do núcleo até o centro do encaixe, passando de ciano para laranja. Faces em Entrada exibem o fluxo inverso: partículas azuis partem do encaixe e chegam ao núcleo em ciano. O fluxo acompanha o tamanho do núcleo, funciona nas seis direções e aparece pela configuração da face, independentemente de transferência de energia ou cabo conectado. A carga continua visível na interface e no item. O modelo está em `assets/futuretech/models/block/battery_mk1.json`; as imagens da frente antiga em `art/battery_mk1` ficam preservadas como referência.

Todas as baterias compartilham as mesmas classes (`BatteryBlock`, `BatteryBlockEntity`, `BatteryBlockItem`, `BatteryMenu`, `BatteryScreen`); o que muda entre elas é o `BatteryTier`, que define capacidade e taxa por tick. Uma nova bateria é uma constante no enum, um bloco e um item registrados a partir dela, e os JSONs de recurso.

O **Cabo MK1** (`futuretech:cable_mk1`) transporta energia entre blocos que não estão encostados. Cabos que se tocam formam uma rede única.

- A rede inteira move até **400 FE/t**, no total, e guarda no máximo um tick de energia; quando não há para onde enviar, ela recusa novas inserções e o bloco de origem fica com a energia.
- Funciona por "empurrão", igual ao gerador e à bateria: qualquer bloco vizinho que envie energia para um cabo alimenta a rede, e a rede reparte o que tem em rodízio entre todos os blocos vizinhos que aceitam energia. A rede nunca devolve energia para um bloco que está tentando inserir nela, mesmo quando a inserção é recusada por o buffer estar cheio, e mesmo que esse bloco encoste na rede por mais de uma face. Isso é o que impede uma bateria com uma face de saída e outra de entrada na mesma rede de ficar recebendo a própria energia de volta a cada tick. Faces configuradas como "Nenhum" não se conectam a cabos.
- Os braços do cabo aparecem para outros cabos e para qualquer bloco com capacidade de energia naquele lado, inclusive máquinas de outros mods. Nas conexões com máquinas, geradores e baterias, aparece um conector metálico de 9 × 9 unidades, com dois níveis de espessura e abertura central. Funciona nas seis direções, acompanha a conexão ativa e faz parte da área clicável e da colisão; emendas entre cabos continuam sem o conector.
- A rede é recalculada quando um cabo é colocado ou quebrado; quebrar um cabo no meio divide a rede em duas.
- Sem tratamento de energia guardada no item: o cabo é um bloco simples, quebrável com qualquer picareta ou à mão.

A receita usa seis lingotes de cobre e três pós de redstone e rende seis cabos:

```text
Cobre     Cobre     Cobre
Redstone  Redstone  Redstone
Cobre     Cobre     Cobre
```

Como nas baterias, todos os cabos compartilham as mesmas classes (`CableBlock`, `CableBlockEntity`, `CableNetwork`); o `CableTier` define o throughput. Cabos de tiers diferentes se conectam, e a rede assume o menor throughput entre eles. O MK1 usa os modelos de `Model/cabo_no.bbmodel` e `Model/cabo.bbmodel`: exterior de 8 unidades, miolo ciano de 6 e frames de 1. O nó fechado tem 37 volumes e cada braço 13. O importador centraliza o conjunto sem redimensioná-lo e extrai o branco e o cinza com variações sutis do nó. Nos trechos retos, duas conexões opostas formam uma linha contínua sem nó intermediário. Pontas, curvas e ramificações mostram o nó com braços apenas nas faces conectadas. A colisão tem largura de 8 e os conectores com máquinas têm abertura de 8 × 8. O item mostra o nó completo. Os recursos podem ser recriados com `art/cable_mk1/Export-Cable.ps1`.

O **Cabo de Itens** (`futuretech:item_cable`) é o irmão do cabo de energia para itens: mesma geometria e mesmos conectores, em latão em vez de ciano. Cabos de itens que se tocam formam uma rede; um cabo de itens ao lado de um cabo de energia é só um vizinho, nunca continuação.

- O item **viaja de verdade**: ao entrar num cabo (empurrado pela máquina ou bombeado pelo conector) ele sai da origem na hora, recebe um destino entre os inventários da sua cor e canal que têm espaço — contando o que já está a caminho — e percorre os cabos a 20 ticks por bloco (Speed Upgrades no conector de entrada aceleram: com 32, um bloco por tick); só entra no destino quando chega. Se ninguém tem espaço, o empurrão é recusado e o item fica na máquina. Se o destino encher no meio do caminho, o item procura outro a partir de onde está; sem alternativa, espera na ponta e tenta de novo a cada intervalo. Os itens em trânsito são salvos com o cabo em que estão e caem no chão se esse cabo for quebrado. A rede nunca manda um item de volta ao bloco que o empurrou, nem por outra face.
- No cabo sem miolo dá para ver os itens passando; o opaco esconde e nem os desenha.
- Move **1 item a cada 20 ticks** por conector de entrada, o ritmo dos cabos básicos de outros mods; a cota não acumula entre intervalos. O `ItemCableTier` define lote e intervalo, e uma rede com tiers misturados anda no ritmo do mais lento.
- Cada conector com um bloco (baú, barril, fornalha, máquina do mod, hopper) abre uma interface própria com clique direito: **Inserir** e **Extrair** como no cabo de energia. Em todos os cabos (energia, itens e fluido) um conector novo vem com os dois desligados e não move nada até o jogador ligar o que quer; um conector só em Extrair bombeia do inventário vizinho, e um conector só em Inserir nunca esvazia o baú que encosta.
- **Prioridade** de −100 a +100 (shift + clique anda de 10): quem tem prioridade maior recebe primeiro, e só o que não coube desce para os demais; empatados se revezam. Vale também para as bombas.
- **Cor** (uma das 16 tintas, branco por padrão, escolhida num painel que abre ao clicar no quadrado) e **Canal** de 0 a 100: um item que entra por um conector só sai por conectores da **mesma cor e mesmo canal**. Prioridade e filtro valem dentro de cada linha.
- **Filtro**: slot para um `futuretech:filter`, com a engrenagem que abre a lista. Ao quebrar o cabo, o filtro cai.
- Funciona com qualquer bloco que ofereça a capability de itens, inclusive os baús e fornalhas do jogo e máquinas de outros mods.

A receita usa seis lingotes de ferro e três vidros e rende seis cabos:

```
I I I
G G G
I I I
```

O **Cabo de Itens** (`futuretech:item_cable`) é o mesmo cabo sem o miolo: só a gaiola de cantos cinza e trilhos brancos, com os vãos abertos. Mesma velocidade, mesmos conectores, mesma interface; os dois cabos de itens se emendam entre si. A receita troca o vidro por painéis de vidro:

```
I I I
P P P
I I I
```

Os modelos são gerados a partir dos do Cabo MK1 removendo os volumes do miolo e recompondo as seis faces de cada barra, já que o gerador original descartava as faces que o miolo escondia.

O **Cabo de Fluido** vem em duas versões, ambas verdes: o **Cabo de Fluido Opaco** (`futuretech:fluid_cable_opaque`), com miolo verde, e o **Cabo de Fluido** (`futuretech:fluid_cable`), com miolo de vidro (textura própria na paleta do vidro do jogo), que deixa ver o fluido passando. Mesma geometria e mesmos conectores dos outros cabos; cabos de fluido só se emendam entre si.

- A rede funciona como a de energia: é um buffer de um tick (500 mB/t por linha) que recebe o que os vizinhos empurram, bombeia dos tanques atrás dos conectores em Extrair e reparte o que tem entre os tanques que aceitam, sem nunca devolver ao bloco que empurrou. Cheia, recusa o empurrão e o fluido fica na origem.
- Cada conector tem **Prioridade**, **Cor** e **Canal**; cada par cor+canal é uma linha com buffer próprio, então uma mesma rede pode carregar água e lava em linhas diferentes sem misturar. Dentro da linha, prioridade maior enche primeiro e iguais dividem em rodízio. Não há filtro nem melhoria no conector de fluido.
- O cabo de vidro desenha o fluido que a rede está carregando (textura e tinta do próprio fluido), no miolo e nos braços conectados. A rede diz a cada cabo em que sentido o fluido passou por ele (o caminho da entrada até o tanque que recebeu), e o cliente faz a textura "flowing" do fluido escorrer nesse sentido; a imagem fica por um segundo depois que o fluxo para. O opaco não desenha nada.
- Funciona com qualquer bloco que ofereça a capability de fluidos, inclusive o caldeirão.

Receitas: ferro nas linhas de cima e de baixo, corante verde-limão nas laterais e vidro no meio (opaco) ou painel de vidro (de vidro), rendendo 6.

Todos os cabos compartilham `AbstractCableBlock` e `AbstractCableBlockEntity` (forma, conexões, modos, prioridade, cor e canal dos conectores, menu); cada tipo só diz o que emenda e qual capability o vizinho precisa oferecer. O cabo de itens usa os modelos do Cabo MK1 como pai, trocando apenas as texturas do miolo (`item_cable_opaque.png`, `item_cable_opaque_node.png`) e o tampão do colar (`item_cable_contact.png`). A lógica fica em `transfer/ItemCableNetwork`.

A **Fornalha Elétrica** (`futuretech:electric_furnace`) é a primeira máquina que consome energia. Ela funde exatamente o que uma fornalha comum funde, sem combustível e no dobro da velocidade.

- Buffer de **20.000 FE**, recebendo até **200 FE/t**. Nunca entrega energia: cada face é Entrada ou Nenhum.
- Gasta **20 FE/t** enquanto trabalha, e leva metade do tempo de cozimento da receita — 100 ticks nos 200 ticks padrão. Um gerador a combustível sólido no talo sustenta exatamente uma fornalha.
- Sem energia ela para e a barra recua aos poucos, como uma fornalha esfriando; o progresso não zera de uma vez e o item continua no slot.
- Slot de entrada à esquerda, saída à direita. A saída não aceita itens colocados à mão.
- Automatizável com funis, mas as faces não são fixas: configure Entrada onde o ingrediente entra e Saída onde o resultado sai (veja "Configuração de lados"). Uma máquina recém-colocada está fechada para itens. Como na fornalha comum, o slot de entrada aceita qualquer item — a consulta de receita precisa do servidor, então a checagem acontece na hora de fundir.
- Tem as mesmas abas de Melhorias, Configuração e Redstone das outras máquinas, e um comparador mede a energia guardada.
- Ao quebrar, solta o bloco e os itens dentro; a energia guardada não é preservada.

A receita gasta uma fornalha, quatro lingotes de ferro, dois de cobre, uma carcaça e um pó de redstone:

```text
Ferro     Fornalha  Ferro
Cobre     Carcaça   Cobre
Ferro     Redstone  Ferro
```

O bloco herda o modelo `machine_base`, com a textura `machine_side` nas cinco faces não frontais. A frente própria tem versões desligada e ligada, com porta metálica, duas resistências e visor azul. Fornalha e gerador ligados usam animações de 16 quadros de 32 × 32 pixels: o aquecimento varia e um brilho percorre o visor azul, mantendo a carcaça parada. As versões desligadas são estáticas.

As fontes e os exportadores PowerShell ficam em `art/`. Execute `art/electric_furnace/Export-Textures.ps1` ou `art/solid_fuel_generator/Export-Textures.ps1` para recriar as frentes e os arquivos `.png.mcmeta`. Os dois usam `art/Export-ActiveAnimation.ps1` e a paleta compartilhada de `art/StatusDisplay.ps1`. A prévia animada está em `art/active-animation-preview.html`.

O **Triturador** (`futuretech:crusher`) usa uma frente de rolos dentados, carcaça grafite e indicador ciano. Sua GUI segue o layout da fornalha elétrica: entrada à esquerda, saída à direita, energia na lateral e abas de melhorias, lados, redstone e informações. Um símbolo de rolos indica o funcionamento e a seta ciano acompanha o progresso.

- Reserva de **20.000 FE**, entrada de até **200 FE/t**, consumo de **20 FE/t** e **100 ticks (5 segundos)** por operação: 2.000 FE por item.
- Sem energia ou espaço para o resultado completo, pausa e preserva o progresso. Trocar o ingrediente por outro reinicia o trabalho. Itens, energia e progresso são salvos com o mundo.
- Recebe energia por qualquer face. Para automatizar itens, configure faces de entrada e saída; elas começam fechadas. Possui extração/inserção automática, controle de redstone e slots para melhorias, como as outras máquinas.
- Ao quebrar, solta a máquina e seus itens; não guarda energia no item.
- Receitas próprias, extensíveis por datapacks com o tipo `futuretech:crushing`: pedregulho → cascalho; cascalho → areia; arenito → 4 areias; arenito vermelho → 4 areias vermelhas; osso → 6 farinhas de osso; vara de blaze → 4 pós de blaze; vidro → areia.

O triturador também duplica os metais brutos: **1 ferro bruto → 2 pós de ferro**, **1 ouro bruto → 2 pós de ouro** e **1 cobre bruto → 2 pós de cobre**. Cada pó vira **1 lingote** na fornalha comum (200 ticks) ou elétrica (100 ticks e 2.000 FE). Assim, um bruto rende dois lingotes após triturar e fundir. Os três pós aparecem na aba FUTURETECH e têm ícones próprios em `art/powders/`. Cada lingote de ferro, ouro ou cobre também pode ser triturado e rende **1 pó** do mesmo metal. Os ícones dos pós aparecem 30% maiores na GUI. A fundição dos pós não concede experiência adicional.

A fabricação usa quatro ferros, um pistão, dois cobres, uma carcaça e uma redstone:

```text
Ferro     Pistão    Ferro
Cobre     Carcaça   Cobre
Ferro     Redstone  Ferro
```

A arte e seus exportadores ficam em `art/crusher/`; a textura estática é `assets/futuretech/textures/block/crusher/crusher_front.png`. Durante o processamento, `crusher_front_on.png` mostra os rolos girando em sentidos opostos e o visor ciano pulsante, em dez quadros com ciclo de um segundo. A carcaça permanece idêntica e parada. Veja `art/crusher/active-preview.html` para comparar as duas versões.

## Configuração de lados

Toda máquina tem, na lateral direita da interface, a aba **Configuração** (ícone de cubo desdobrado). Ela mostra as seis faces do bloco com as texturas reais, a frente no meio, e os lados nomeados como quem olha de frente para a máquina.

O modo de uma face **sempre** decide os itens. Se ele também decide a energia depende da máquina:

| | itens | energia |
|---|---|---|
| **Máquinas** (fornalha) | o modo decide | livre — recebe por qualquer face, não precisa configurar |
| **Gerador** | Entrada aceita combustível | livre — envia por qualquer face, mesmo em Nenhum |
| **Bateria** | o modo decide (quando tiver slot) | **o modo decide** — Entrada carrega, Saída descarrega |

A ideia é não fazer o jogador configurar energia à toa: uma máquina liga em qualquer lado e um gerador alimenta o que estiver encostado. Na bateria é o contrário, porque escolher por onde a energia entra e sai é a função dela; uma face em Nenhum não carrega nem descarrega.

- Clique esquerdo numa face avança o modo: Entrada → Saída → Ambos → Nenhum; clique direito percorre a ordem inversa. Os dois pulam os modos que a máquina não permite.
- Shift + clique esquerdo na frente põe todas as faces em Nenhum.
- Faces em Nenhum não oferecem a capability de itens ao vizinho e somem para os funis.
- A configuração é salva com o bloco.

Modos por máquina:

| máquina | modos | por quê |
|---|---|---|
| Gerador | Entrada, Nenhum | só tem slot de combustível, e não produz item de resultado |
| Bateria | Entrada, Saída, Nenhum | direção da energia; nunca Ambos, para não devolver energia a si mesma |
| Fornalha | Entrada, Saída, Ambos, Nenhum | ingrediente entra, resultado sai, ou os dois na mesma face |

- Faces configuradas usam a textura do modo, no mundo e na aba: **Entrada** com detalhes azuis (`machine_side_input`), **Saída** com detalhes laranja (`machine_side_output`) e **Ambos** combinando as duas cores (`machine_side_input_output`). Faces em Nenhum mantêm a lateral normal. Vale também para a frente, se ela for configurada.
- A aparência é sincronizada para os jogadores próximos e restaurada ao carregar o mundo.

### Transporte automático

Máquinas com inventário ganham dois botões na lateral esquerda da aba, desenhados nas mesmas cores dos modos:

- **Seta azul para baixo — Puxar itens.** A máquina tira itens por conta própria de qualquer baú, máquina ou tubo encostado numa face configurada como **Entrada**.
- **Seta laranja para cima — Empurrar itens.** A máquina entrega o resultado a qualquer baú, máquina ou tubo encostado numa face configurada como **Saída**.

Os dois começam **desligados**, então uma máquina recém-colocada nunca mexe num vizinho sem você mandar. Cada um move até **4 itens por tick**, no total, com a primeira face sorteada a cada tick para uma vizinha ocupada não travar as outras — o mesmo rodízio que o envio de energia usa.

O transporte respeita as mesmas regras de face do funil: uma face em Entrada só recebe, uma em Saída só entrega, e uma em Nenhum não faz nem uma coisa nem outra. Ligar o botão sem configurar face nenhuma não faz nada.

Quais botões aparecem depende da máquina: a fornalha tem os dois, o gerador só o de puxar (ele consome combustível mas não produz item), e a bateria nenhum, por não ter inventário.

Os funis seguem a configuração em vez de faces fixas: uma face em Entrada aceita item no slot de entrada, uma em Saída deixa puxar do slot de resultado, e uma em Ambos faz as duas coisas. A mesma configuração alimenta `Capabilities.Item.BLOCK`, então tubos e máquinas de outros mods enxergam exatamente o mesmo que um funil.

A API fica em `dev.futuretech.api.side` e é reaproveitável por qualquer máquina nova: o bloco implementa `SideConfigurableBlock` (modos permitidos, padrões por face, estado para desenhar), o block entity implementa `SideConfigurable` e guarda um `SideConfig` (salvo/carregado e exposto em slots de dados do menu), o menu implementa `SideConfigMenu` e encaminha `clickMenuButton` para `SideConfigMenu.handleButton`, as capabilities passam por `SidedEnergy.view` e `SidedItems.view`, e a tela coloca um `SideConfigTab` no seu `TabStrip`. O construtor do `SideConfig` recebe se aquela máquina deixa a configuração governar energia. Os cliques viajam pelo pacote vanilla de botão de menu, sem rede própria.

## Controle de redstone

Abaixo da aba de configuração fica a aba **Redstone**, com três modos: **Ignorar** (funciona sempre), **Sinal baixo** (funciona só sem sinal) e **Sinal alto** (funciona só com sinal). A aba mostra se o bloco está recebendo sinal agora. No gerador, o sinal controla a geração (o combustível em queima fica preservado; a energia guardada continua saindo). Na bateria, controla a saída; a carga pelas faces de entrada nunca é bloqueada.

## Melhorias

A primeira aba, **Melhorias**, tem quatro slots para itens de upgrade. Os slots aceitam só itens da tag `futuretech:upgrades`, que inclui o Speed Upgrade, um por slot. As máquinas guardam os itens pela `UpgradeInventory` (`dev.futuretech.api.upgrade`). Os slots são slots reais do menu (Shift + clique funciona) e o conteúdo é salvo com o bloco e cai ao quebrá-lo.

O **MK da máquina libera os slots**: MK1 (o padrão) abre um, MK2 dois, MK3 três e MK4 os quatro. Os slots fechados aparecem com um cadeado e não aceitam item (a bateria segue o próprio tier; o tanque, sem MK, tem um). Os efeitos dos itens de upgrade ainda não estão implementados.

**Cada nível MK** acima do MK1 dá à máquina, além dos slots (e das faixas, no Triturador e na Fornalha):

- **+25 % de energia armazenada** sobre a capacidade base (`MachineLevel.capacity`): 20.000 FE viram 25.000, 30.000 e 35.000;
- **+15 % de velocidade**: o tempo de cada trabalho é dividido por `1 + 0,15 × (MK − 1)` (`MachineLevel.duration`), então 100 ticks viram 87, 77 e 69;
- **+20 % de consumo por tick** para pagar essa velocidade (`MachineLevel.consumption`): 20 FE/t viram 24, 28 e 32.

Os percentuais ficam em `MachineLevel`. O gerador ganha só a capacidade. Aplicar um kit muda a capacidade na hora, inclusive na barra e na aba de energia.

Como os slots de menu têm posição fixa, a aba de melhorias é a primeira da tira, onde nenhuma aba aberta acima pode empurrá-la.

A API fica em `dev.futuretech.api.redstone` (`RedstoneMode`, `RedstoneControl`, `RedstoneControllable`, `RedstoneControlMenu`, `RedstoneControlTab`) e segue o mesmo desenho da configuração de lados. As abas em si vêm de `dev.futuretech.api.gui` (`MachineTab`, `TabStrip`): uma tela cria um `TabStrip` com as abas que quiser e repassa desenho, tooltip e cliques; abas abertas empurram as de baixo.

A API fica em `dev.futuretech.api.side` e é reaproveitável por qualquer máquina nova: o bloco implementa `SideConfigurableBlock` (modos permitidos, padrões por face, estado para desenhar), o block entity implementa `SideConfigurable` e guarda um `SideConfig` (salvo/carregado e exposto em slots de dados do menu), o menu implementa `SideConfigMenu` e encaminha `clickMenuButton` para `SideConfigMenu.handleButton`, a capability de energia passa por `SidedEnergy.view`, e a tela coloca um `SideConfigTab` no seu `TabStrip`. Os cliques viajam pelo pacote vanilla de botão de menu, sem rede própria.

## Faixas por MK

O Triturador e a Fornalha Elétrica têm até quatro **faixas**, cada uma um slot de entrada emparelhado com um de saída e o próprio progresso. O MK diz quantas estão abertas: MK1 uma, MK2 duas, MK3 três e MK4 quatro, então um MK4 processa quatro itens ao mesmo tempo. Cada faixa em trabalho paga o consumo por tick do nível, ou seja, quatro faixas ativas gastam quatro vezes mais; faixa parada não gasta. A interface cresce uma linha de slots por faixa extra, cada uma com a própria seta de progresso (e, com mais de uma faixa, os rolos ou a chama ao lado de cada saída). O MK viaja no pacote de abertura do menu, por isso esses dois menus usam `IMenuTypeExtension`.

No inventário do bloco as entradas são os slots 0–3 e as saídas 4–7 (`SLOT_INPUT + faixa`, `SLOT_OUTPUT + faixa`); só as faixas abertas aparecem para funis, cabos e configuração de lados. Um funil ou cabo que empurra um mesmo item é distribuído entre as faixas abertas: cada item vai para a faixa que tem menos daquele item (vazias incluídas), então uma pilha se espalha e as faixas trabalham em paralelo em vez de encher a primeira. Um item diferente só entra numa faixa vazia. O progresso de cada faixa é salvo com o bloco (`Progress`, `Progress1`…) e o da faixa 0 usa as chaves antigas, então mundos anteriores continuam de onde estavam.

## Tanque de fluido

O **Tanque de Fluido** (`futuretech:fluid_tank`) usa a mesma armação metálica da bateria, com os seis vãos fechados por vidro. Guarda **16.000 mB (16 baldes)** de um fluido por vez. O conteúdo aparece dentro do bloco com a textura e a cor do fluido; a superfície sobe e desce suavemente conforme o tanque enche ou esvazia.

Clique com um balde cheio para abastecer ou com um balde vazio para retirar. Fluidos diferentes não se misturam, e um balde só transfere se couber o volume inteiro. Clique sem balde para abrir a interface: entrada à esquerda, barra de armazenamento no centro e saída à direita. Um balde vazio na entrada retira 1.000 mB e produz um balde cheio na saída; um balde cheio deposita 1.000 mB e produz um balde vazio. A troca espera se a saída estiver bloqueada, faltar fluido ou espaço no tanque, ou o fluido não for compatível. Shift + clique move os baldes entre a interface e o inventário. Os dois slots são salvos com o mundo; seus itens caem ao quebrar o bloco. A aba **Configuração de lados** define Entrada, Saída, Ambos ou Nenhum para fluidos em cada face; tanques colocados começam com todas as faces em Nenhum; abra os lados desejados na configuração. A aba **Redstone** oferece Ignorar, Sinal baixo e Sinal alto, controlando o processamento dos baldes e as transferências externas. O uso manual de baldes diretamente no bloco continua disponível. A aba **Melhorias** tem os quatro slots padrão, salvos com o mundo e soltos ao quebrar; por enquanto os upgrades não alteram a velocidade ou a capacidade do tanque. As seis faces oferecem a capability de fluidos do NeoForge para cabos e máquinas compatíveis conforme sua configuração. Um comparador mede o nível de enchimento.

O conteúdo é salvo com o mundo e acompanha o item ao quebrar o tanque com picareta de pedra ou superior. A tooltip do item mostra o fluido e a quantidade; ao recolocar, o conteúdo retorna. A receita usa quatro lingotes de ferro nos cantos, quatro blocos de vidro nas laterais e um balde vazio no centro. O tanque aparece na aba criativa FUTURETECH, ao lado da bateria. O modelo pode ser recriado por `art/fluid_tank/export_tank.py`.

A **Wrench** (`futuretech:wrench`) é um item 3D com boca aberta, cabeça de aço, parafuso central e cabo escuro com detalhes ciano. Aparece na aba FUTURETECH e tem posições próprias na mão, no inventário e na moldura. A receita usa três lingotes de ferro e um de cobre. Clique direito em um bloco gira ele: blocos com `FACING` percorrem norte → leste → sul → oeste → cima → baixo (as máquinas do mod só as quatro horizontais), blocos com eixo ciclam X → Y → Z e o resto usa a rotação própria do bloco. Shift + clique direito em um bloco do FutureTech desmonta ele pelo caminho normal de drop, então o tanque cai com o fluido e a bateria com a carga. O modelo usa os materiais existentes do filtro e pode ser recriado com `art/wrench/export_model.py`.

## Assembler

O Assembler usa quatro blocos separados: **Mesa de montagem**, **Braço de transporte**, **Braço de montagem** e **Terminal do Assembler**. Todos aparecem na aba FUTURETECH e têm receitas de fabricação com materiais vanilla, para não depender da primeira carcaça.

Monte a célula livremente, com cada braço a até **3 blocos** de distância da mesa e do inventário que ele atende. A distância é medida entre os centros dos blocos; a busca inclui diferenças de altura e escolhe o alvo compatível mais próximo. O terminal precisa estar a até 3 blocos da mesa. Um arranjo inicial, visto de cima, é:

```text
                        Terminal
                           ·
Baú entrada · Braço azul · Mesa · Braço laranja · Baú saída
                           ·
                    Braço de montagem
```

Cada `·` representa um espaço vazio. Os braços detectam os blocos automaticamente, sem precisar de ligações manuais. Clique com a **Wrench** no braço de transporte parado para alternar entre **entrada azul** e **saída laranja**. O braço de montagem usa uma ferramenta giratória própria. Shift + Wrench desmonta qualquer uma das peças; os materiais guardados na peça caem para recuperação.

Abra o terminal e use as setas para selecionar **Carcaça de máquina**: **1 pedra (`minecraft:stone`) + 1 lingote de ferro → 1 carcaça (`futuretech:machine_casing`)**. Os ícones esmaecidos mostram os materiais esperados. Coloque os ingredientes no baú de entrada. O braço azul leva um item por viagem até a mesa; o braço de montagem se aproxima, trabalha por 4 segundos e recua; só então o braço laranja recolhe o resultado e o leva ao baú de saída. A interface mostra o progresso, o estado e as quatro conexões.

O resultado aguarda na mesa se o baú estiver cheio. Se o destino ficar indisponível durante o transporte, o braço mantém o item e tenta novamente. Cada peça salva seus itens e o estágio do movimento; os testes cobrem retomadas antes e depois da fabricação. A troca de receita exige a mesa vazia e os braços parados. É possível retirar materiais pela interface quando a mesa está livre. A energia entra somente pelo controller (Terminal do Assembler), em qualquer face: capacidade de 32.000 FE e entrada máxima de 200 FE por tick. Cada braço consome 20 FE por tick de movimento ou montagem; parado ou bloqueado não consome. Sem energia, os movimentos e a montagem pausam, preservando os itens, e continuam quando a energia retorna. A GUI do controller mostra a barra vertical com o mesmo degradê animado das máquinas e a quantidade de FE ao passar o mouse. A energia armazenada é salva com o mundo.

As receitas próprias ficam em `src/main/recipes/assembler.json` (veja [Receitas das máquinas](#receitas-das-máquinas)), com tipo `futuretech:assembling`, de 1 a 9 ingredientes (um item por entrada), resultado e duração em ticks. Modelos e dados podem ser regenerados com `python tools/generate_assembler.py`.

## Receitas das máquinas

Cada máquina com receitas próprias tem **um único arquivo** em `src/main/recipes/`: `crusher.json` (tipo `futuretech:crushing`), `metal_press.json` (tipo `futuretech:pressing`) e `assembler.json` (tipo `futuretech:assembling`). O arquivo traz o `type` uma vez e um mapa `recipes` em que a chave é o nome da receita e o valor é o corpo dela, sem o `type`:

```json
{
  "type": "futuretech:crushing",
  "recipes": {
    "cobblestone": { "ingredient": "minecraft:cobblestone", "result": { "id": "minecraft:gravel", "count": 1 } }
  }
}
```

O jogo só carrega uma receita por JSON, então a tarefa `expandMachineRecipes` do Gradle (que roda em todo `build`/`runClient`) gera `data/futuretech/recipe/<tipo>/<nome>.json` em `build/generated/recipes` a partir desses arquivos. Para adicionar uma receita, basta acrescentar uma entrada no arquivo da máquina; não crie arquivos soltos em `data/futuretech/recipe/crushing` ou `assembling`. As receitas de crafting das mesas vanilla continuam uma por arquivo em `src/main/resources/data/futuretech/recipe/`.

## Desenvolvimento no Windows

Abra esta pasta como projeto Gradle na IDE, usando um JDK 25.

```powershell
# Compilar e copiar automaticamente para o PrismLauncher
.\gradlew.bat build

# Iniciar uma instância de desenvolvimento do Minecraft
.\gradlew.bat runClient

# Testar geração, bateria, rede de cabos, configuração de lados, persistência e receitas
.\gradlew.bat test
```

A primeira execução baixa o Gradle e as dependências do Minecraft/NeoForge. A instância de desenvolvimento usa a pasta `run` deste projeto.

Na primeira compilação, a tarefa `initGitRepository` executa `git init` (ramo `main`) antes de `compileJava`; nas seguintes ela é pulada porque a pasta `.git` já existe. Os commits continuam manuais.

No Windows, `build` copia o JAR após o empacotamento e as verificações para `C:\Users\AJG\AppData\Roaming\PrismLauncher\instances\FUTURETECH\minecraft\mods\futuretech.jar`. O nome instalado é fixo para substituir a versão anterior nas próximas compilações. O JAR com versão continua em `build/libs`.

O destino pode ser alterado pela propriedade `prism_mods_dir` em `gradle.properties`. A cópia usa a tarefa `copyModToPrism` e preserva os demais mods da pasta.

## Próximas etapas

1. Tiers de máquina (velocidade e buffer) reaproveitando o padrão de `BatteryTier` e `CableTier`.

## Origem

Estrutura baseada no [MDK oficial do NeoForge para 26.2 com ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle). A licença do modelo está em `TEMPLATE_LICENSE.txt`. O código do mod mantém a configuração inicial `All Rights Reserved`.

## Metal Press

A Metal Press transforma lingotes de ferro, ouro, cobre e netherita em chapas e engrenagens. O slot **Molde** da GUI aceita um **Molde de Chapa** (1 lingote → 1 chapa, 100 ticks) ou **Molde de Engrenagem** (4 lingotes → 1 engrenagem, 160 ticks). Um único molde reutilizável define o trabalho de todas as linhas; sem molde, a máquina não processa. Trocar ou remover o molde reinicia o progresso, preservando os lingotes. Cada linha consome 20 FE/t na MK1; falta de energia ou saída cheia pausa o trabalho. O molde não é consumido nem se desgasta. Molde, inventário, energia e progresso são salvos no mundo. Shift+clique em um molde o coloca no slot próprio; automação transporta apenas ingredientes e resultados. As receitas dos moldes usam quatro lingotes de ferro: quadrado 2×2 para chapa e cruz vazada para engrenagem. Prensas de versões anteriores precisam receber um molde para voltar a trabalhar.

Suporta configuração dos lados, entrada/saída automática, redstone e kits MK2–MK4, com uma linha de processamento por MK e os mesmos ajustes de energia, velocidade e cores das outras máquinas. A entrada automática junta lotes de quatro antes de distribuir os lingotes entre linhas no modo engrenagem. A frente usa PNGs próprios de 32×32 em `textures/block/metal_press/`: `metal_press_front.png` (desligada) e `metal_press_front_on.png` (ligada, tira de quatro quadros 32×32 com o pistão descendo e subindo e indicador ciano fixo; ciclo de 16 ticks definido no `.mcmeta`). Os modelos em `models/block/metal_press/` reutilizam as laterais e quinas MK da carcaça. `tools/Generate-MetalPress.ps1` gera somente os modelos; as oito receitas são editadas diretamente em `src/main/recipes/metal_press.json`.
## Aço

O lingote de aço (`futuretech:steel_ingot`) está disponível na aba criativa, sem receita de fabricação por enquanto. A Metal Press aceita aço com os moldes existentes: 1 lingote produz 1 chapa de aço; 4 lingotes produzem 1 engrenagem de aço. As receitas ficam no arquivo único `src/main/recipes/metal_press.json`. Os itens integram as tags `c:ingots/steel`, `c:plates/steel` e `c:gears/steel`.
