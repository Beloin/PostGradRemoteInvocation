# sensorSimulator — Simulador de sensores da caldeira (Python)

Publica temperaturas de W sensores no broker Mosquitto (a cada N milissegundos):

```
temperatura = base + uniforme(minimo, maximo)
tópico: caldeira/sensores/{id}/temperatura
payload: {"sensorId": id, "temperatura": valor, "timestamp": ms}
```

- Configuração em memória, começa **desligado** (start off).
- O painel Alarms GUI configura e liga/desliga por chamadas remotas
  (HTTP + JSON, porque Python não serve RMI nativo do Java):
  - `GET /config`, `POST /config` (campos parciais), `POST /control` `{"acao":"iniciar"|"parar"}` na porta 8090.

Rodar:

```sh
make sensor-sim   # cria .venv e instala paho-mqtt na primeira execução
```

Ou manualmente:

```sh
python3 -m venv .venv
.venv/bin/pip install paho-mqtt
.venv/bin/python simulador.py
```

Testes rápidos com curl:

```sh
curl http://localhost:8090/config
curl -X POST http://localhost:8090/config -d '{"base":195,"minimo":3,"maximo":10,"intervaloMillis":500,"sensores":2}'
curl -X POST http://localhost:8090/control -d '{"acao":"iniciar"}'
```