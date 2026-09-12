# Texturas do gerador

Arte original criada com a ferramenta integrada ImageGen, inspirada no visual industrial simples do Thermal Expansion.

`source.png` preserva a imagem gerada: frente desligada e ligada na primeira linha, topo e lateral na segunda. O script atual exporta somente a frente desligada e ligada, de `source-fronts.png`, para PNGs de 32 × 32 pixels em `src/main/resources/assets/futuretech/textures/block/`.

Execute `./art/solid_fuel_generator/Export-Textures.ps1` na raiz do projeto para exportar novamente. O exportador preserva as cores RGB e força alpha 255 em todos os pixels: a carcaça é sólida, mesmo que a imagem gerada contenha transparência parcial.

O modelo ligado herda a carcaça do modelo desligado e troca somente a frente. O item e o painel de configuração dos lados usam os mesmos modelos/texturas do bloco.

Gerador e bateria herdam `machine_base`, que usa `machine_side.png` nas cinco faces não frontais. Essa textura compartilhada é a chapa lisa sem linha, escolhida pelo usuário e exportada separadamente por `art/machine_side/Export-Texture.ps1`. As imagens de topo e lateral originais ficam preservadas como material de arte; a prévia atual mostra somente as duas frentes.

## Prompt de geração

### Revisão das frentes: metal liso

`source-fronts.png` guarda a edição feita com ImageGen para combinar o metal das frentes com o miolo da lateral lisa. O exportador usa apenas `source-fronts.png` e exporta somente as frentes. A prévia mostra apenas essas faces. `source.png` fica como referência histórica e não é necessário para executar o script.

Prompt da revisão: replace only the exposed gray background of the front faces with the calm smooth neutral-gray metal from the approved machine-side reference. Preserve the furnace, fire, battery emblem, indicators, outer frames and corner screws. Keep the 2×2 atlas and coarse 32×32 pixel grid. Fully opaque.

```text
Use case: stylized-concept
Asset type: production Minecraft machine block pixel-art texture atlas, exactly four square tiles in a 2 by 2 grid.
Primary request: Original beautiful simple solid-fuel generator textures inspired by the clean industrial machine aesthetic of Thermal Expansion. Restrained pixel art, steel-gray casing, dark graphite inset panels, very small muted blue accents and orange burning furnace. Not a copy of an existing texture.
Composition: Fill the whole square canvas edge-to-edge with exactly four equally sized square texture tiles. No gap, no margin, no labels. Top-left: generator front OFF. Top-right: the IDENTICAL front ON, same casing, same geometry; only furnace interior glowing orange with small yellow pixel coals and tiny status indicator lit. Bottom-left: TOP face, mostly plain steel panel with four short central dark ventilation slits. Bottom-right: SIDE face, mostly plain steel panel with one small centered recessed dark vent and subtle muted blue metal trim.
Style: authentic low-resolution pixel art. Each quadrant is EXACTLY a 32 by 32 logical pixel texture enlarged using nearest-neighbor square pixels; whole sheet exactly 64 by 64 logical pixels. Align every shape to this coarse pixel grid. NO subpixel detail. Limit palette to about 16 flat colors. All four faces share exactly the same simple two-pixel thick dark outer structural frame and one-pixel steel highlight, flush to tile boundaries, small single-pixel rivets at corners. Front has a small thin blue accent strip near top, a large simple black rectangular furnace opening in center-lower area with two dark horizontal grate bars, dark orange unlit interior in OFF; orange-yellow hot coals in ON. Gray metal occupies most of each tile, spacious restrained design. Avoid excessive trim and ornament.
Constraints: flat orthographic faces only, square frontal view, no 3D cube, no perspective, no background, no text, no logo, no watermark, no gradients, no antialiasing, no blurry edges, no noise, no bevel rendering. Ready-to-use texture atlas, not a presentation sheet. Output 1024x1024 pixels if required by renderer, with each logical pixel a uniform 16x16 block.
```
