# Lateral padrão das máquinas

Chapa com a moldura detalhada e os parafusos no estilo do gerador, combinados com o miolo cinza liso da lateral anterior. Arte criada e editada com ImageGen; `source.png` preserva a última versão aprovada. Sem grade, linha interna ou símbolos no painel central.

Execute `./art/machine_side/Export-Texture.ps1` na raiz do projeto para exportar `src/main/resources/assets/futuretech/textures/block/machine_side.png` em 32 × 32 pixels. A exportação usa amostragem sem suavização e alpha 255 em todos os pixels. `preview.png` mostra o resultado ampliado.

O modelo `machine_base` aplica essa textura ao topo, base, laterais e traseira do gerador e da bateria. Cada máquina mantém sua frente. A Carcaça de Máquina usa a textura nas seis faces. Os exportadores das frentes não substituem esta textura compartilhada.

O padrão `-DetailStrength 0` reproduz as cores da arte aprovada, sem acrescentar marcas. Opcionalmente, valores até 12 realçam a granulação e acrescentam pequenas variações de tom no painel. Cada execução parte de `source.png`, portanto os detalhes não se acumulam.

## Prompt da edição aprovada

Keep the detailed industrial frame and corner screws of the generator-style plain side. Replace only the central gray surface with the calm, smooth gray middle of the supplied machine_side texture. Preserve frame widths, bevels and bolts. No internal lines, grille, symbols, extra noise or colored accents. Fully opaque pixel art.
