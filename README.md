# FUTURETECH

Mod de máquinas, energia e automação industrial para **Minecraft Java 26.2**, com **NeoForge 26.2.0.82** e **Java 25**.

## Estado atual

Versão `0.3.0`, com identificação `futuretech`, aba criativa própria e traduções em português e inglês.

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
- Recebe por todas as faces e envia por todas as faces para blocos que aceitem energia, exceto outras baterias, para a energia não ficar indo e voltando entre elas.
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

Cabos e fornalha elétrica ainda não estão implementados. Por enquanto, o gerador alimenta uma bateria ou um bloco compatível de outro mod colocado diretamente ao lado.

## Desenvolvimento no Windows

Abra esta pasta como projeto Gradle na IDE, usando um JDK 25.

```powershell
# Compilar e copiar automaticamente para o PrismLauncher
.\gradlew.bat build

# Iniciar uma instância de desenvolvimento do Minecraft
.\gradlew.bat runClient

# Testar geração, bateria, transferências, persistência e carregamento das receitas
.\gradlew.bat test
```

A primeira execução baixa o Gradle e as dependências do Minecraft/NeoForge. A instância de desenvolvimento usa a pasta `run` deste projeto.

Na primeira compilação, a tarefa `initGitRepository` executa `git init` (ramo `main`) antes de `compileJava`; nas seguintes ela é pulada porque a pasta `.git` já existe. Os commits continuam manuais.

No Windows, `build` copia o JAR após o empacotamento e as verificações para `C:\Users\AJG\AppData\Roaming\PrismLauncher\instances\FUTURETECH\minecraft\mods\futuretech.jar`. O nome instalado é fixo para substituir a versão anterior nas próximas compilações. O JAR com versão continua em `build/libs`.

O destino pode ser alterado pela propriedade `prism_mods_dir` em `gradle.properties`. A cópia usa a tarefa `copyModToPrism` e preserva os demais mods da pasta.

## Próximas etapas

1. Cabos para transportar energia entre os blocos.
2. Fornalha elétrica para consumir energia no processamento de itens.

## Origem

Estrutura baseada no [MDK oficial do NeoForge para 26.2 com ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle). A licença do modelo está em `TEMPLATE_LICENSE.txt`. O código do mod mantém a configuração inicial `All Rights Reserved`.
