# Cabo MK1

O relevo é modelado com cuboides reais, em vez de desenhado em uma superfície plana.

- Junção: carcaça recuada, 12 arestas metálicas e seis indicadores de 4 × 4 unidades com borda prateada (19 volumes).
- Braço: quatro trilhos de 1 × 1 unidade e corpo fechado de 5 × 5 unidades. As faces ficam meia unidade abaixo dos trilhos, com laterais prateadas e uma faixa ciano estreita no centro, seguindo a referência.
- Limites externos: 5 a 11 nos eixos transversais, compatíveis com a forma de seis unidades do `CableBlock`.
- O condutor alcança a carcaça central recuada; os trilhos e o condutor chegam ao limite do bloco para encontrar o próximo cabo.
- O mesmo braço é rotacionado pelo blockstate nas seis direções. O ícone do item usa somente o nó central ciano, ampliado no inventário para facilitar a identificação.
- A faixa do miolo é centralizada e o desenho dos trilhos e do miolo é simétrico nos dois eixos da textura. As metades rotacionadas se encontram sem deslocar as faixas ou inverter os brilhos na emenda entre blocos.
- Os nós usam `cable_mk1_node.png`: um desenho completo por face, com UVs posicionados pelas coordenadas de cada peça. Os cantos da moldura se encontram sem cortar as linhas. O indicador aparece apenas na face externa de cada contato; a espessura e as faces internas usam material escuro.
- Ao tocar uma máquina em uma face com conexão de energia ativa, o cabo recebe um conector de aço de 9 × 9 unidades, com dois níveis de espessura e abertura de 6 × 6. A geometria e a área clicável vêm de `CableConnector`; o modelo usa os estados dos blocos vizinhos, sem novas propriedades ou alterações na rede de energia. As emendas entre cabos não recebem esse conector.
- `Export-Connector.ps1` gera `cable_connector.png` e exporta a geometria Java para a prévia local. É executado também por `Export-Cable.ps1`.
- As laterais e as molduras frontais dos dois níveis do conector usam aço grafite com chanfros de baixo contraste e pequenos parafusos, combinando com a paleta das máquinas. O UV acompanha a posição ao longo de cada lateral; as molduras frontais compartilham um desenho contínuo nos cantos.

Execute `Export-Cable.ps1` para gerar o atlas e os três modelos JSON. Execute `Render-Preview.ps1` para renderizar esses modelos em `preview.png`. Essa prévia é uma renderização local dos recursos, não uma captura do Minecraft.
