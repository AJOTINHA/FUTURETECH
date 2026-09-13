# Estrutura da bateria

Textura pixel art criada pelo exportador PowerShell, conforme solicitado. Todas as cores são amostradas de `machine_side.png`, compartilhando a paleta das outras máquinas.

O acabamento atual mistura os tons dessa paleta para formar vigas com um rebaixo central suave, encaixes discretos nas pontas e um pequeno parafuso em um contorno rebaixado por canto. A geometria e o mapeamento UV permanecem iguais.

Execute `./art/battery_frame/Export-Texture.ps1` na raiz do projeto. O script exporta `battery_frame.png` em 64 × 64 pixels, opaco, e prévias ampliadas. O atlas contém uma viga horizontal de 40 × 10 pixels, uma vertical de 10 × 40 e uma placa de canto de 12 × 12 com parafuso. Os atlas `battery_port_input.png` e `battery_port_output.png` fornecem a cor dos aros das chapas; as vigas no mundo usam somente `battery_frame.png`.

O modelo `battery_mk1.json` usa essas regiões sem esticar uma textura de face inteira sobre as vigas. São doze vigas de 2,5 unidades de espessura, centralizadas em oito cantos reforçados de 3 unidades. O topo e o centro permanecem vazados.

`Export-SideModes.ps1`, chamado pelo exportador principal, chama `Export-Ports.ps1` para gerar os atlas das conexões e gera três ícones para a configuração. Os antigos atlas de vigas coloridas não são mais gerados. Nenhum mostra a estrutura aberta; Entrada/Saída mostram uma chapa de aço e abertura quadrada com aro azul/laranja. Os pixels do centro da abertura são transparentes nos ícones.

`BatteryPortGeometry` define a chapa em quatro peças e o aro em quatro barras, deixando um furo real de 4 × 4 unidades. O aro atravessa a chapa, fica visível pela parte interna e chega ao limite do bloco para encostar no cabo de 6 × 6 unidades; o aço termina ao redor dele, sem cobrir o interior colorido. `Export-Ports.ps1` gera os atlas de conexão de 64 × 64 pixels: aço baseado em `machine_side.png`, encaixe rebaixado, aro com bordas claras/escuras e quatro fixações. Os oito pixels superiores do atlas guardam a faixa usada nas paredes internas do furo. `BatteryPortModelPart` mapeia as duas extremidades com a face detalhada e a profundidade com essa faixa colorida; a geometria e o contato com o cabo permanecem iguais. As rotações colocam essas peças em qualquer uma das seis faces do mundo. `ConfiguredSideModel` acrescenta as peças conforme o ModelData de cada lado, sem modificar as superfícies das vigas. Nenhum não acrescenta geometria. A colisão continua sendo a da estrutura.
