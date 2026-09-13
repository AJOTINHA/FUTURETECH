# Cabo MK1

Gerado inteiro por `Build-Cable.ps1` — sprites, modelos, modelo de item e blockstate.
Roda no Windows PowerShell 5.1, sem depender do `pwsh`.

`Export-Connector.ps1` é separado e cuida do colar de ligação com máquinas: desenha
`cable_connector.png` e regenera `connector-preview-model.json` a partir da geometria em
`CableConnector.java`. O colar é desenhado por código, não por modelo.

Núcleo de 6×6 em azul-marinho e verde-azulado dentro de uma gaiola de 8×8, cantos cinza e
trilhos brancos, com o meio de cada aresta aberto para o núcleo aparecer.

## Decisões que importam

- **Trecho reto é uma caixa só.** `cable_mk1_line` tem 13 volumes de 16 unidades de
  comprimento. O desenho anterior fatiava o bloco em 4 segmentos de 4 unidades, e cada
  junta entre segmentos aparecia no jogo como um fio escuro atravessando o cabo. Não é
  z-fighting nem mipmap nem ambient occlusion: é a junta em si. Não volte a fatiar.
- **A tira da textura cabe uma vez por bloco.** `cable_mk1.png` tem 32 linhas — oito
  faixas de 4 px, quatro períodos inteiros — mapeadas de uma vez sobre as 16 unidades.
  O padrão continua sem quebrar de um bloco para o outro. São dois texels por unidade,
  tanto na linha quanto no braço.
- **Nenhuma face de tampa interna.** Braço e linha não desenham as faces perpendiculares
  ao eixo do cabo: as duas pontas sempre encostam em algo que as cobre. Quem fecha a boca
  contra uma máquina é o tampão do colar, em `CableConnectorModelPart`.
- **As faces escondidas são provadas, não listadas.** O gerador descarta toda face que
  outro volume do mesmo modelo cobre por inteiro. A linha sai com 28 quads.
- **`ambientocclusion: false`** em todos os modelos de bloco. O cabo é convexo e não tem
  o que ocluir.
- **Padding em todas as sprites.** Fora do recorte desenhado, o pixel mais próximo é
  repetido, então o mipmap nunca mistura preenchimento com o cabo.

## Geometria

- Seção: núcleo 5..11, e uma coroa de doze barras de 1×1 no quadrado 4..12 — quatro
  cantos cinza e duas brancas ladeando cada canto.
- Nó: núcleo 6×6×6 dentro das doze arestas do cubo 4..12.
- Braço: núcleo até z=5, trilhos até z=4, encaixando no nó.
- Tampa: quatro barras brancas fechando uma face sem conexão, com janela de 4×4.
- `cable_contact.png` é o contato em 8×8 que o colar usa para tapar o furo.

O blockstate usa o nó em tudo que não seja reto; multipart não tem `NOT`, então o
complemento dos três casos retos está escrito à mão. `Build-Cable.ps1` monta as 19
cláusulas sozinho.

As molduras vêm de `frame_materials/`, pintadas à mão e quase chapadas; o gerador copia
os dois PNGs em vez de tentar reproduzir o ruído sutil delas.
