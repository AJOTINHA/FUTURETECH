# Texturas retiradas dos recursos do mod

Cópias preservadas na limpeza de 13/09/2026, fora do JAR:

- `battery_mk1_front.png`: frente do modelo antigo da bateria.
- `battery_frame_input.png` e `battery_frame_output.png`: atlas antigos de vigas coloridas, substituídos pelos aros de `battery_port_input.png` e `battery_port_output.png`.
- `face correta.png`: imagem sem referência no código ou nos modelos.

O exportador da estrutura não gera mais os atlas antigos. O exportador histórico da bateria grava somente em `art/battery_mk1/exported`.

- `cable_mk1_node.png`: atlas do nó do desenho antigo do cabo, que vinha de um projeto
  Blockbench. Esse pipeline foi removido em 13/09/2026. **Cuidado:** o cabo atual tem uma
  textura viva com esse mesmo nome em `assets/futuretech/textures/block/` — são coisas
  diferentes, e a daqui é a morta.
