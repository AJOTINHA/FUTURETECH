# FUTURETECH

Mod de máquinas, energia e automação industrial para **Minecraft Java 26.2**, com **NeoForge 26.2.0.82** e **Java 25**.

## Estado atual

Versão `0.5.0`, com identificação `futuretech`, aba criativa própria e traduções em português e inglês.

A **Carcaça de Máquina** está registrada na aba FUTURETECH. Usa provisoriamente a textura de bloco de ferro do Minecraft e pode ser fabricada com oito barras de ferro ao redor de um espaço vazio. Requer picareta de pedra ou superior para soltar o item.

O **Gerador a Combustível Sólido** é a primeira máquina funcional. Clique com o botão direito para abrir sua interface e coloque carvão, carvão vegetal ou um combustível de madeira no slot. Aceita troncos, madeiras descascadas, tábuas, gravetos, ferramentas de madeira, portas, cercas, escadas, lajes, placas, barcos, baús e outros itens de madeira, incluindo bambu e madeiras do Nether. Também é possível usar Shift + clique para mover combustível do inventário.

O nome acima do slot acompanha o item inserido e o idioma do jogo. Quando o slot está vazio, aparece “Vazio” (“Empty” em inglês). A barra de combustível permanece visível, inclusive enquanto termina a queima do último item consumido.

- Gera **20 FE/t** (400 FE/s a 20 ticks por segundo).
- Cada carvão fornece **1.600 ticks de geração**, totalizando **32.000 FE**.
- Os combustíveis de madeira usam a duração da fornalha quando disponível: troncos e tábuas geram normalmente **6.000 FE**, lajes **3.000 FE** e gravetos **2.000 FE**. Peças de madeira sem valor de fornalha, como as do Nether, recebem uma duração por categoria.
- A barra de queima acompanha a duração do combustível em uso, mesmo após salvar e carregar o mundo.
- Armazena **20.000 FE** e pausa quando não há espaço para mais um tick de geração; o combustível em queima fica preservado.
- Envia automaticamente até **80 FE/t**, no total, para blocos adjacentes que aceitem energia pela API do NeoForge. Todas as faces oferecem saída; o gerador não recebe energia. O limite vale por tick e não por chamada: blocos vizinhos que puxem energia por conta própria dividem os mesmos 80 FE/t com o envio automático.
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

O visual do gerador reutiliza provisoriamente texturas de ferro e do alto-forno do Minecraft. Ao quebrá-lo com picareta de pedra ou superior, ele solta o bloco e o combustível restante no inventário; a energia e o combustível já em queima não são preservados no item.

A lista de peças de madeira é extensível pela tag de itens `futuretech:generator_wooden_fuels`, que inclui as categorias de madeira do Minecraft. Madeiras de outros mods que pertençam a essas tags também são aceitas; itens adicionais podem ser incluídos por datapack. Itens que só contêm madeira em parte (camas, estandartes, tochas, quadros, molduras, jukebox, sensor de luz e colmeias naturais) ficam de fora de propósito.

A **Bateria MK1** (`futuretech:battery_mk1`) armazena a energia do gerador. Clique com o botão direito para ver a reserva e as taxas de entrada e saída do último tick.

- Armazena **100.000 FE**; recebe e envia até **200 FE/t** cada, por tick e não por chamada.
- Cada face é configurável (veja "Configuração de lados"); a frente, marcada com cobre e virada para o jogador ao colocar, é a referência do painel. Uma face de bateria nunca é entrada e saída ao mesmo tempo, para um cabo não devolver à bateria a energia dela mesma. Uma bateria nunca envia diretamente para outra bateria.
- Ao quebrar, o item sai com a carga guardada num componente de dados (`futuretech:energy`); a tooltip mostra "x / 100000 FE" e o item exibe uma barra de carga. Ao colocar de novo, a energia volta para o bloco. Baterias vazias não carregam o componente e continuam empilháveis.
- Um comparador mede o nível da reserva.

A receita usa quatro barras de ferro, três pós de redstone, uma carcaça de máquina e uma barra de ouro:

```text
Ferro     Redstone   Ferro
Redstone  Carcaça    Redstone
Ferro     Ouro       Ferro
```

O visual da bateria MK1 reutiliza provisoriamente texturas de ferro e cobre cortado do Minecraft.

Todas as baterias compartilham as mesmas classes (`BatteryBlock`, `BatteryBlockEntity`, `BatteryBlockItem`, `BatteryMenu`, `BatteryScreen`); o que muda entre elas é o `BatteryTier`, que define capacidade e taxa por tick. Uma nova bateria é uma constante no enum, um bloco e um item registrados a partir dela, e os JSONs de recurso.

O **Cabo MK1** (`futuretech:cable_mk1`) transporta energia entre blocos que não estão encostados. Cabos que se tocam formam uma rede única.

- A rede inteira move até **400 FE/t**, no total, e guarda no máximo um tick de energia; quando não há para onde enviar, ela recusa novas inserções e o bloco de origem fica com a energia.
- Funciona por "empurrão", igual ao gerador e à bateria: qualquer bloco vizinho que envie energia para um cabo alimenta a rede, e a rede reparte o que tem em rodízio entre todos os blocos vizinhos que aceitam energia. A rede nunca devolve energia pela face de um bloco que está tentando inserir nela, mesmo quando a inserção é recusada por o buffer estar cheio. Faces configuradas como "Nenhum" não se conectam a cabos.
- Os braços do cabo aparecem para outros cabos e para qualquer bloco com capacidade de energia naquele lado, inclusive máquinas de outros mods.
- A rede é recalculada quando um cabo é colocado ou quebrado; quebrar um cabo no meio divide a rede em duas.
- Sem tratamento de energia guardada no item: o cabo é um bloco simples, quebrável com qualquer picareta ou à mão.

A receita usa seis lingotes de cobre e três pós de redstone e rende seis cabos:

```text
Cobre     Cobre     Cobre
Redstone  Redstone  Redstone
Cobre     Cobre     Cobre
```

Como nas baterias, todos os cabos compartilham as mesmas classes (`CableBlock`, `CableBlockEntity`, `CableNetwork`); o `CableTier` define o throughput. Cabos de tiers diferentes se conectam, e a rede assume o menor throughput entre eles. O visual reutiliza provisoriamente a textura de cobre cortado do Minecraft.

## Configuração de lados

Toda máquina tem, na lateral direita da interface, a aba **Configuração** (segunda da tira, abaixo de Melhorias): o cubo desdobrado com as seis faces do bloco, desenhadas com as texturas reais e nomeadas em relação à frente (Frente, Trás, Esquerda, Direita, Cima, Baixo). Clicar numa face alterna o modo dela — **Entrada → Saída → Entrada e saída → Nenhum** — pulando os modos que aquela máquina não permite. A borda da face mostra o modo (azul entrada, laranja saída, verde ambos, cinza nenhum), e o tooltip diz o nome e o modo.

- Toda máquina nova é colocada com **todas as faces em Nenhum**; o jogador abre as faces que quer usar.
- **Shift + clique na Frente** volta todas as faces para Nenhum.
- Gerador: cada face é Saída ou Nenhum (ele só produz).
- Bateria: Entrada, Saída ou Nenhum; nunca ambos na mesma face.
- Faces em Nenhum não oferecem energia a vizinhos nem se conectam a cabos; a configuração é salva com o bloco.

## Controle de redstone

Abaixo da aba de configuração fica a aba **Redstone**, com três modos: **Ignorar** (funciona sempre), **Sinal baixo** (funciona só sem sinal) e **Sinal alto** (funciona só com sinal). A aba mostra se o bloco está recebendo sinal agora. No gerador, o sinal controla a geração (o combustível em queima fica preservado; a energia guardada continua saindo). Na bateria, controla a saída; a carga pelas faces de entrada nunca é bloqueada.

## Melhorias

A primeira aba, **Melhorias**, tem quatro slots para itens de upgrade. Ainda não existe nenhum item de upgrade: os slots aceitam só itens da tag `futuretech:upgrades`, que está vazia, então por enquanto a aba não tem efeito. Quando os upgrades existirem, entram por essa tag e as máquinas passam a lê-los pela `UpgradeInventory` (`dev.futuretech.api.upgrade`). Os slots são slots reais do menu (Shift + clique funciona) e o conteúdo é salvo com o bloco e cai ao quebrá-lo.

Como os slots de menu têm posição fixa, a aba de melhorias é a primeira da tira, onde nenhuma aba aberta acima pode empurrá-la.

A API fica em `dev.futuretech.api.redstone` (`RedstoneMode`, `RedstoneControl`, `RedstoneControllable`, `RedstoneControlMenu`, `RedstoneControlTab`) e segue o mesmo desenho da configuração de lados. As abas em si vêm de `dev.futuretech.api.gui` (`MachineTab`, `TabStrip`): uma tela cria um `TabStrip` com as abas que quiser e repassa desenho, tooltip e cliques; abas abertas empurram as de baixo.

A API fica em `dev.futuretech.api.side` e é reaproveitável por qualquer máquina nova: o bloco implementa `SideConfigurableBlock` (modos permitidos, padrões por face, estado para desenhar), o block entity implementa `SideConfigurable` e guarda um `SideConfig` (salvo/carregado e exposto em slots de dados do menu), o menu implementa `SideConfigMenu` e encaminha `clickMenuButton` para `SideConfigMenu.handleButton`, a capability de energia passa por `SidedEnergy.view`, e a tela coloca um `SideConfigTab` no seu `TabStrip`. Os cliques viajam pelo pacote vanilla de botão de menu, sem rede própria.

A fornalha elétrica ainda não está implementada.

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

1. Fornalha elétrica para consumir energia no processamento de itens.

## Origem

Estrutura baseada no [MDK oficial do NeoForge para 26.2 com ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle). A licença do modelo está em `TEMPLATE_LICENSE.txt`. O código do mod mantém a configuração inicial `All Rights Reserved`.
