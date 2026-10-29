# MQTTMonitor — Monitoramento da caldeira via MQTT

Ambiente industrial simulado: sensores de temperatura publicam a cada 60s em um
broker Mosquitto; o serviço CAT calcula a média móvel das temperaturas (últimos
120s); o serviço Alarms transforma os eventos do CAT em alarmes para operadores.

## Arquitetura

| Projeto | Papel |
|---|---|
| `Sensor/` | Sensor(es) simulado(s) — publica temperatura periodicamente (a definir) |
| `CATService/` | Inscrito em temperatura; calcula média de 120s; publica eventos |
| `Alarms/` | Inscrito nos eventos; exibe alarmes (console ou interface gráfica) |
| `docker-compose.yml` | Broker Mosquitto |

## Tópicos (sugestão inicial)

- `caldeira/sensores/{id}/temperatura` — payload do sensor (ex.: `{"sensorId":1,"temperatura":185.0,"timestamp":1714512000000}`)
- `caldeira/cat/evento` — payload do CAT, com tipo no payload:
  - `aumento-repentina` — duas últimas médias diferem em 5 graus
  - `temperatura-alta` — média acima de 200 graus
- `caldeira/alarme` — alarme publicado pelo serviço Alarms

## Como rodar

```sh
make mqtt-up      # sobe o Mosquitto
make cat-service  # terminal 1
make alarms      # terminal 2
# o sensor fica a definir
make mqtt-down    # para o broker
```

Cliente Mosquitto pode ser usado dentro do contêiner para testes:

```sh
docker exec mosquitto mosquitto_pub -h localhost -t caldeira/sensores/1/temperatura -m '{"sensorId":1,"temperatura":210.0}'
docker exec mosquitto mosquitto_sub -h localhost -t '#' -v
```