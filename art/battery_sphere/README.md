# Esfera da bateria

As linhas ciano e suas junções pulsam suavemente em ciclos de 3 segundos. O brilho varia entre 55% e 100%, mantendo a malha visível; o halo acompanha a pulsação. O tamanho continua vinculado apenas à carga e os anéis metálicos preservam a iluminação normal.

Dois anéis estabilizadores de aço cinza envolvem a esfera em planos inclinados de 52 e -58 graus. Têm espessura de 0,011 bloco e respondem à iluminação do ambiente, com três juntas prateadas. Giram em sentidos opostos e em velocidades diferentes: o plano do anel interno completa uma volta em 5 segundos, e o externo em aproximadamente 6,7 segundos. As juntas percorrem cada anel quatro vezes mais rápido que a rotação do plano. Ambos acompanham a escala da carga e têm raios distintos, preservando a folga entre si, a esfera e o frame. As malhas são calculadas uma vez e reutilizadas pelo renderizador.

Esfera 3D procedural azul-ciano no centro da bateria, com rotação de uma volta a cada 20 segundos. A malha é o dual de um icosaedro subdividido: 150 hexágonos, 12 pentágonos, 320 junções e 480 arestas. O raio do núcleo é 0,265 bloco, mantendo folga dentro da estrutura mesmo durante a rotação.

`BatterySphereRenderer` desenha núcleo escuro, linhas emissivas e junções claras. O brilho é feito com geometria translúcida; não requer shader de bloom. O tamanho acompanha a carga: 12% do tamanho máximo quando vazia, crescendo linearmente até 100% quando cheia. A carga visual é sincronizada em passos de 0,1%, a cada cinco ticks enquanto muda, e interpolada no cliente. A esfera não altera energia, colisão ou configuração dos lados. O frame continua sendo o modelo estático; apenas a esfera gira no renderizador do block entity.

`ExportMesh.java` e `Render-Preview.ps1` produzem `preview.png` usando a mesma malha. A prévia é uma projeção ilustrativa feita fora do jogo, não uma captura do Minecraft.

Após compilar as classes Java, execute na raiz:

```powershell
New-Item -ItemType Directory -Path build/sphere-preview -Force | Out-Null
javac -cp build/classes/java/main -d build/sphere-preview art/battery_sphere/ExportMesh.java
java -cp 'build/classes/java/main;build/sphere-preview' ExportMesh | Set-Content build/sphere-preview/mesh.json -Encoding utf8
./art/battery_sphere/Render-Preview.ps1
```
