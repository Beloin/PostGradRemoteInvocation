# MQTTMonitor — Monitoramento da caldeira via MQTT

Ambiente industrial simulado: sensores de temperatura publicam a cada 60s em um
broker Mosquitto; o serviço CAT calcula a média móvel das temperaturas (últimos
120s); o serviço Alarms transforma os eventos do CAT em alarmes no painel GUI.

## Arquitetura

| Projeto | Papel |
|---|---|
| `sensorSimulator/` | Simulador dos sensores (Python): publica W temperaturas a cada N milissegundos; configurável em runtime |
| `CATService/` | Inscrito em temperatura; média de 120s; publica a média e os eventos |
| `Alarms/` | Painel GUI: log de alarmes, gráfico da média e controle do simulador (sem display roda em console) |
| `docker-compose.yml` | Broker Mosquitto |

## Tópicos

- `caldeira/sensores/{id}/temperatura` — payload do sensor: `{"sensorId":1,"temperatura":103.5,"timestamp":1714512000000}`
- `caldeira/cat/media` — média atual calculada pelo CAT (alimenta o gráfico do painel)
- `caldeira/cat/evento` — eventos do CAT:
  - `aumento-repentina` — duas últimas médias diferem 5 graus ou mais
  - `temperatura-alta` — média acima de 200 graus
- `caldeira/alarme` — alarme do serviço Alarms (evento + hora), exibido no log do painel

## Simulador de sensores

Configuração **em memória**, começa desligado ("start off"). O painel Alarms GUI
busca a configuração na inicialização e envia alterações por chamadas remotas
HTTP + JSON na porta 8090 (Python não serve RMI nativo do Java):

- `GET /config`, `POST /config` (campos parciais), `POST /control` `{"acao":"iniciar"|"parar"}`

Sem o simulador no ar, o painel de configuração fica bloqueado (cinza) com o
aviso "Random Generator Service Unavailable".

## Como rodar

```sh
make mqtt-up      # sobe o Mosquitto
make cat-service  # terminal 1
make alarms       # terminal 2 (GUI; sem display vira console)
make sensor-sim   # terminal 3 (simulador; cria o venv na primeira execução)
```

Cliente Mosquitto para testes dentro do contêiner:

```sh
docker exec mosquitto mosquitto_pub -h localhost -t caldeira/sensores/1/temperatura -m '{"sensorId":1,"temperatura":210.0,"timestamp":1714512000000}'
docker exec mosquitto mosquitto_sub -h localhost -t '#' -v
```