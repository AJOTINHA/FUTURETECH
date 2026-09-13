# Texturas da bateria MK1

Arte original gerada com ImageGen usando a textura do gerador como referência visual: carcaça cinza, moldura escura, detalhes azuis e contatos de cobre.

`source.png` preserva a imagem gerada. Ordem dos quadrantes: frente, lateral, topo e base. `Export-Textures.ps1` exporta somente a frente de `source-fronts.png` em 32 × 32 pixels para `art/battery_mk1/exported/battery_mk1_front.png`, com alpha 255 em todos os pixels. `preview.png` mostra a textura histórica ampliada sem suavização. O exportador não grava mais nos recursos do mod.

Esta frente pertence ao modelo antigo e não é mais usada no jogo. A bateria atual usa a estrutura vazada de `art/battery_frame`. A folha original e a prévia ficam preservadas como referência histórica.

## Prompt de geração

### Revisão das frentes: metal liso

`source-fronts.png` guarda a edição feita com ImageGen para combinar o metal das frentes com o miolo da lateral lisa. O exportador usa apenas `source-fronts.png` e exporta somente as frentes. A prévia mostra apenas essas faces. `source.png` fica como referência histórica e não é necessário para executar o script.

Prompt da revisão: replace only the exposed gray background of the front faces with the calm smooth neutral-gray metal from the approved machine-side reference. Preserve the furnace, fire, battery emblem, indicators, outer frames and corner screws. Keep the 2×2 atlas and coarse 32×32 pixel grid. Fully opaque.

```text
Use case: stylized-concept
Asset type: production Minecraft block pixel-art texture atlas for the FUTURETECH MK1 battery, exactly four equally sized square tiles in a 2 by 2 grid.
Input image: style reference ONLY, the existing generator texture sheet. Match its steel-gray casing, dark graphite square edge frame, corner rivets, muted blue accents, coarse pixel size and simple industrial Thermal Expansion inspired aesthetic. Create an ORIGINAL matching battery texture sheet, not another generator.
Composition, exact tile order: TOP LEFT = battery FRONT. TOP RIGHT = battery SIDE (used also on back). BOTTOM LEFT = battery TOP. BOTTOM RIGHT = battery BOTTOM.
Front: spacious gray steel panel, a small thin muted blue horizontal strip near top like reference, central dark recessed vertical rectangular BATTERY emblem with a small terminal cap and three bold cyan-blue horizontal cell bars inside. The emblem is a static identity symbol. Small copper contacts at its base. No furnace, no flames. Give the front a strong readable battery silhouette.
Side: mostly plain steel-gray with a compact centered dark recessed panel containing two vertical muted copper conductor strips, small understated blue detail.
Top: mostly plain steel-gray plate with two small square recessed copper terminals symmetrically centered, restrained mechanical panel.
Bottom: plain gray steel plate, small dark inset square service cover in center, no lights.
Style: authentic simple 32x32 pixel art per tile, 64x64 logical pixels for entire atlas, enlarged with crisp nearest-neighbor square pixels. Large simple color clusters, limited flat palette, low noise. ALL tiles share exactly the same two-pixel dark structural frame and corner bolts as reference. Consistent border positions. Suitable alongside the referenced generator in Minecraft.
Constraints: perfectly flat orthographic square faces, exactly a 2x2 grid, fill entire canvas edge-to-edge, no gaps or padding, no text, no labels, no watermarks, no perspective or 3D cube, no gradients, no antialiasing, no glow outside shapes. FULLY OPAQUE image, alpha 255 everywhere, no transparency even in vents or black areas. This is a texture atlas, not a presentation board.
```
