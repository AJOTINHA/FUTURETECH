# Face de entrada

Textura escolhida pelo usuário: grade central com três barras e quatro detalhes azuis. Criada com ImageGen usando a lateral padrão como referência. `source.png` preserva a versão aprovada.

Execute `./art/machine_side_input/Export-Texture.ps1` na raiz do projeto para exportar `src/main/resources/assets/futuretech/textures/block/machine_side_input.png` em 32 × 32 pixels, sem suavização e com alpha 255 em todos os pixels. `preview.png` mostra a exportação ampliada.

O modelo dinâmico `ConfiguredSideModel` substitui a textura das faces em `SideMode.INPUT`, inclusive a frente se ela for configurada como entrada. Ao mudar para outro modo, a face volta à textura normal. A aba de configuração usa a mesma imagem. Os modos de energia continuam definidos por cada máquina; o gerador não aceita entrada.

## Prompt de edição

Use the approved 32x32 machine side texture as the base. Add only a centered dark recessed ventilation grille with three horizontal bars and four muted blue tabs above, below, left and right. Keep the original steel-gray plate, border and corner bolts. Match its coarse pixel grid. Fully opaque, no text.
