# FUTURETECH

Mod de máquinas, energia e automação industrial para Minecraft Java.

## Requisitos

| | |
| --- | --- |
| Minecraft | 26.2 |
| NeoForge | 26.2.0.82 ou superior |
| Java | 25 |

## Instalação

1. Instale o NeoForge para Minecraft 26.2.
2. Baixe o `futuretech-1.0.0.jar`.
3. Coloque o arquivo na pasta `mods` da sua instância.

Tudo do mod fica em uma aba própria no inventário criativo, com nomes em português do Brasil e em inglês.

## O que tem no mod

### Energia

- **Geradores:** a Combustível Sólido, de Lava, Solar, Eólico, e o par Boiler + Turbina a Vapor.
- **Baterias MK1 a MK4**, com faces configuráveis e carga preservada ao quebrar o bloco.
- **Cabos de energia MK1 a MK4.** Cabos que se tocam formam uma rede só, e a rede anda no ritmo do MK mais lento.
- **Carregador** e **Bateria Portátil** (MK1 a MK4) para recarregar itens.

### Processamento

Triturador, Fornalha Elétrica, Serraria, Metal Press, Fundidora, Melter, Extrusor, Máquina de Pintura e o Assembler, que fabrica os componentes que não têm receita de bancada.

- **Kits de Upgrade MK2, MK3 e MK4:** Shift + clique direito na máquina. A sequência é MK1 → MK2 → MK3 → MK4, e o upgrade preserva inventário, energia, progresso e configuração. A potência por nível é 100%, 150%, 200% e 300% da MK1.
- **Melhorias:** Velocidade, Eficiência, Lava, Energia e Areia, instaladas nos slots que o MK libera.

### Mineração

- **Mineradora:** os **Marcadores de Área** são tochas azuis que você planta nos cantos. Marcador com sinal de redstone traça uma linha reta mostrando onde cabe o próximo, e dois marcadores no mesmo eixo acendem a linha entre eles. Feche o quadrado com quatro e encoste a Mineradora de costas para um dos cantos: é esse marcador que ela lê. Ela então monta sozinha uma **estrutura de vigas** sobre a moldura — quatro pernas e um anel por cima, recolhendo os marcadores para o próprio inventário — e um braço corre nesse anel, descendo a broca em cada bloco. Cava o que está **dentro** da moldura, camada por camada até o fundo do mundo; a linha dos marcadores nunca é quebrada. A cova cresce com o MK: 9, 16, 25 e 33 blocos de lado. Bedrock, baús e as outras máquinas ficam de pé, a água e a lava do caminho somem, e o que ela tira fica no inventário dela até sair pelos lados de saída. Viga quebrada é reposta sozinha, e a estrutura inteira sai junto quando a máquina sai.

### Transporte

- **Cabos de itens e de fluidos**, cada um em versão normal e opaca, com **Filtros MK1 a MK4** nos conectores.
- **Facades:** clique num cabo com um bloco para esconder aquela face; a Chave tira de volta.
- **Tanque de Fluido** e **Bomba d'Água**.
- **Bomba de Lava:** desce um cano até o fundo do lago e esvazia a lava conectada a ele, da borda para o centro, deixando pedra no lugar de cada fonte para nada ficar escorrendo. A lava fica no tanque interno e sai pelos cabos ou enche baldes.
- **Tesseract**, **Teleportador** com Cartão de Teleporte, e **Teleportador Portátil**.

### Redstone e rede

- **Cabo de Redstone**, **Transmissor** e **Receptor sem fio**, **Sensor de Chuva**.
- **Cabo de Rede**, **Painel de Rede** e **Cartões de Armazenamento**.
- **Controlador de Tempo** e **Controlador de Clima**.

### Materiais e ferramentas

- **Minérios novos:** estanho, chumbo e prata, gerados no mundo, com bruto, bloco, lingote, pó, placa e engrenagem.
- **Ligas:** aço, electrum, liga vermelha e liga do End.
- **Componentes:** chip eletrônico, carcaça de máquina, bobinas de redstone, moldes de placa e engrenagem.
- **Ferramentas de área:** Martelos e Escavadoras quebram um 3 × 3 na face atingida; Machados Lenhadores derrubam a árvore inteira. Em madeira, pedra, cobre, ferro, ouro, diamante e netherita.
- **Chave** para girar máquinas, recolher blocos com o conteúdo e remover facades.

## Configuração das máquinas

Cada face de uma máquina pode ser Entrada, Saída, Ambos ou Nenhum, pela aba de lados na interface. Também dá para controlar a máquina por redstone, escolhendo se ela trabalha sempre, só com sinal ou só sem sinal.

## Compatibilidade

Os cabos se ligam a qualquer bloco que exponha as capabilities padrão do NeoForge (energia, itens e fluidos) na face tocada, e as máquinas daqui expõem as suas do mesmo jeito. Máquinas, baús, tanques e cabos de outros mods NeoForge funcionam junto, sem integração específica.

Os materiais usam as tags comuns (`c:ingots/<metal>`, `c:dusts/<metal>`, `c:plates/<metal>`, `c:gears/<metal>`), então receitas de outros mods aceitam os itens daqui e as máquinas daqui aceitam os de lá.

Suporte opcional a **JEI**, que mostra as receitas das máquinas e dos geradores, e a **Curios**, que aceita a Bateria Portátil nos slots de cinto e amuleto.

## Compilar do código-fonte

Abra a pasta como projeto Gradle na IDE, com um JDK 25.

```bash
./gradlew build
```

```bash
./gradlew runClient
```

```bash
./gradlew test
```

A primeira execução baixa o Gradle e as dependências do Minecraft e do NeoForge. A instância de desenvolvimento usa a pasta `run` do projeto.

## Licença

Todos os direitos reservados. Veja [LICENSE](LICENSE).

A estrutura do projeto vem do [MDK oficial do NeoForge para 26.2 com ModDevGradle](https://github.com/NeoForgeMDKs/MDK-26.2-ModDevGradle); a licença do modelo está em `TEMPLATE_LICENSE.txt` e cobre apenas os arquivos-modelo.
