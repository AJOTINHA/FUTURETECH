# FUTURETECH

Mod de máquinas, energia e automação industrial para **Minecraft Java 26.2**, com **NeoForge 26.2.0.82** e **Java 25**.

## Estado atual

Versão `0.5.0`, com identificação `futuretech`, aba criativa própria e traduções em português e inglês.

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
- Cada conector com um bloco (baú, barril, fornalha, máquina do mod, hopper) abre uma interface própria com clique direito: **Inserir** e **Extrair** como no cabo de energia; um conector só em Extrair bombeia do inventário vizinho, e um conector normal nunca esvazia o baú que encosta.
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

Os dois cabos compartilham `AbstractCableBlock` e `AbstractCableBlockEntity` (forma, conexões, modos dos conectores, menu); cada tipo só diz o que emenda e qual capability o vizinho precisa oferecer. O cabo de itens usa os modelos do Cabo MK1 como pai, trocando apenas as texturas do miolo (`item_cable_opaque.png`, `item_cable_opaque_node.png`) e o tampão do colar (`item_cable_contact.png`). A lógica fica em `transfer/ItemCableNetwork`.

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

A arte e seus exportadores ficam em `art/crusher/`; a textura estática é `assets/futuretech/textures/block/crusher_front.png`. Durante o processamento, `crusher_front_on.png` mostra os rolos girando em sentidos opostos e o visor ciano pulsante, em dez quadros com ciclo de um segundo. A carcaça permanece idêntica e parada. Veja `art/crusher/active-preview.html` para comparar as duas versões.

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

A primeira aba, **Melhorias**, tem quatro slots para itens de upgrade. Os slots aceitam só itens da tag `futuretech:upgrades`, que inclui o Speed Upgrade, um por slot. Os efeitos nas máquinas ainda não estão implementados. As máquinas guardam os itens pela `UpgradeInventory` (`dev.futuretech.api.upgrade`). Os slots são slots reais do menu (Shift + clique funciona) e o conteúdo é salvo com o bloco e cai ao quebrá-lo.

Como os slots de menu têm posição fixa, a aba de melhorias é a primeira da tira, onde nenhuma aba aberta acima pode empurrá-la.

A API fica em `dev.futuretech.api.redstone` (`RedstoneMode`, `RedstoneControl`, `RedstoneControllable`, `RedstoneControlMenu`, `RedstoneControlTab`) e segue o mesmo desenho da configuração de lados. As abas em si vêm de `dev.futuretech.api.gui` (`MachineTab`, `TabStrip`): uma tela cria um `TabStrip` com as abas que quiser e repassa desenho, tooltip e cliques; abas abertas empurram as de baixo.

A API fica em `dev.futuretech.api.side` e é reaproveitável por qualquer máquina nova: o bloco implementa `SideConfigurableBlock` (modos permitidos, padrões por face, estado para desenhar), o block entity implementa `SideConfigurable` e guarda um `SideConfig` (salvo/carregado e exposto em slots de dados do menu), o menu implementa `SideConfigMenu` e encaminha `clickMenuButton` para `SideConfigMenu.handleButton`, a capability de energia passa por `SidedEnergy.view`, e a tela coloca um `SideConfigTab` no seu `TabStrip`. Os cliques viajam pelo pacote vanilla de botão de menu, sem rede própria.

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
