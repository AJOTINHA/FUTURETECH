# Frente do Crusher

Textura frontal de 32 × 32 pixels, opaca, com rolos dentados opostos, carcaça de aço grafite e indicador ciano. Criada com a ferramenta integrada de geração de imagens, usando a carcaça e a fornalha elétrica do projeto como referências de estilo.

- `source-front.png`: imagem-fonte gerada.
- `prompt-front.txt`: prompt completo da geração.
- `Export-Texture.ps1`: exportação por amostragem central, seguindo o fluxo da fornalha elétrica.
- `preview.png`: prévia ampliada da textura final, sem suavização.
- Recurso final: `src/main/resources/assets/futuretech/textures/block/crusher_front.png`.

Frente integrada ao bloco `futuretech:crusher` (Triturador), com a carcaça padrão nas outras cinco faces. A máquina possui interface baseada na fornalha elétrica, consome 20 FE/t e processa receitas `futuretech:crushing` em 100 ticks.

## Frente ligada

- `source-front-on.png`: variante ligada criada com a ferramenta integrada de imagens; prompt em `prompt-front-on.txt`.
- `Export-ActiveAnimation.ps1`: preserva os pixels da carcaça desligada, amostra a arte ligada no interior e exporta os dois rolos girando em sentidos opostos, com visor ciano pulsante.
- `preview-on.png`: primeiro quadro ampliado; `active-preview.html`: comparação animada com controle de pausa.
- `crusher_front_on.png` e `.png.mcmeta`: faixa vertical de 10 quadros de 32 × 32, dois ticks por quadro, ciclo de um segundo, sem interpolação.

As quatro orientações com `lit=true` usam o modelo `crusher_on`. A animação aparece enquanto a máquina processa um item; sem energia, com saída bloqueada ou parada por redstone, volta à frente estática.
