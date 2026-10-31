#!/usr/bin/env python3
"""Simulador de sensores da caldeira (atividade MQTT).

Publica a temperatura de W sensores no broker Mosquitto a cada N milissegundos:
    temperatura = base + uniforme(minimo, maximo)
em caldeira/sensores/{id}/temperatura com payload
    {"sensorId": id, "temperatura": valor, "timestamp": ms}

A configuração fica em memória (nada é persistido) e o simulador começa
desligado ("start off"). O painel Alarms GUI busca e altera a configuração
por chamadas remotas em HTTP + JSON (Python não serve RMI nativo do Java):

    GET  /config  -> configuração atual
    POST /config  -> atualiza campos enviados (JSON parcial)
    POST /control -> {"acao": "iniciar"} ou {"acao": "parar"}

Rodar com: make sensor-sim (ou .venv/bin/python simulador.py)
"""

import json
import random
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import paho.mqtt.client as mqtt

BROKER = "localhost"
PORTA_MQTT = 1883
PORTA_CONFIG = 8090
TOPICO_SENSOR = "caldeira/sensores/{id}/temperatura"

# Configuração em memória; o simulador começa desligado
config = {
    "base": 100.0,           # temperatura base em °C
    "minimo": 3.0,           # variação mínima somada à base
    "maximo": 10.0,          # variação máxima somada à base
    "intervaloMillis": 5000,  # N: intervalo entre publicações em milissegundos
    "sensores": 3,            # W: quantidade de sensores
}
rodando = False
evento_parada = threading.Event()
cliente_mqtt = None


def publicar_uma_rodada():
    """Publica a temperatura de todos os sensores (uma rodada do tick)."""
    agora = int(time.time() * 1000)
    for sensor_id in range(1, config["sensores"] + 1):
        temperatura = config["base"] + random.uniform(config["minimo"], config["maximo"])
        payload = json.dumps({
            "sensorId": sensor_id,
            "temperatura": round(temperatura, 2),
            "timestamp": agora,
        })
        cliente_mqtt.publish(TOPICO_SENSOR.format(id=sensor_id), payload)


def laco_publicacao(parar):
    """Laço do simulador: publica a cada `intervaloMillis` ms até `parar`.

    O intervalo é lido a cada tick, então uma atualização de configuração
    vale já a partir do próximo tick (mesmo com o simulador rodando).
    """
    while rodando and not parar.wait(config["intervaloMillis"] / 1000.0):
        publicar_uma_rodada()


def iniciar():
    global rodando
    if rodando:
        return
    rodando = True
    parar = threading.Event()
    threading.Thread(target=laco_publicacao, args=(parar,), daemon=True).start()
    print("[SIM] simulador iniciado:", config, flush=True)


def parar():
    global rodando
    rodando = False
    print("[SIM] simulador parado", flush=True)


class HandlerConfig(BaseHTTPRequestHandler):
    """Endpoints de configuração chamados pelo painel Alarms GUI."""

    def _responder(self, obj, codigo=200):
        corpo = json.dumps(obj).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def _corpo(self):
        tamanho = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(tamanho)) if tamanho else {}

    def do_GET(self):
        if self.path == "/config":
            self._responder(dict(config, rodando=rodando))
        else:
            self._responder({"erro": "rota desconhecida"}, 404)

    def do_POST(self):
        if self.path == "/config":
            dados = self._corpo()
            for campo in ("base", "minimo", "maximo", "intervaloMillis", "sensores"):
                if campo in dados:
                    try:
                        valor = float(dados[campo])
                        if campo in ("intervaloMillis", "sensores"):
                            if valor < 1:
                                raise ValueError("deve ser >= 1")
                            valor = int(valor)
                        config[campo] = valor
                    except (TypeError, ValueError):
                        self._responder({"erro": f"valor inválido para {campo}"}, 400)
                        return
            print("[SIM] configuração atualizada:", json.dumps(config), flush=True)
            self._responder(dict(config, rodando=rodando))
        elif self.path == "/control":
            acao = self._corpo().get("acao")
            if acao == "iniciar":
                iniciar()
            elif acao == "parar":
                parar()
            else:
                self._responder({"erro": "acao deve ser 'iniciar' ou 'parar'"}, 400)
                return
            self._responder(dict(config, rodando=rodando))
        else:
            self._responder({"erro": "rota desconhecida"}, 404)

    def log_message(self, formato, *args):
        pass  # o servidor de configuração não polui o console


def main():
    global cliente_mqtt
    try:
        cliente_mqtt = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id="sensor-simulator")
    except AttributeError:
        # paho-mqtt 1.x não tem CallbackAPIVersion
        cliente_mqtt = mqtt.Client(client_id="sensor-simulator")
    cliente_mqtt.connect(BROKER, PORTA_MQTT)
    cliente_mqtt.loop_start()

    servidor = ThreadingHTTPServer(("0.0.0.0", PORTA_CONFIG), HandlerConfig)
    print(f"[SIM] simulador no ar — broker {BROKER}:{PORTA_MQTT}, "
          f"configuração em http://localhost:{PORTA_CONFIG} (começa desligado)", flush=True)
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        parar()
        cliente_mqtt.loop_stop()
        cliente_mqtt.disconnect()


if __name__ == "__main__":
    main()