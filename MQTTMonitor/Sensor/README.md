# Sensor — a definir

Projeto em aberto: sensor(es) simulado(s) de temperatura da caldeira.

Requisito: publicar a cada 60 segundos em `caldeira/sensor/{id}/temperatura`
(payload com id do sensor, temperatura e timestamp — ver o padrão sugerido no
README do projeto pai). O número de sensores deve poder aumentar/diminuir.