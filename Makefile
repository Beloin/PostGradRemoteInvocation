SRV_DIR = JavaServerSocket_Calculadora
CLI_DIR = JavaClientSocket_Calculadora
RMI_DIR = ExemploRMI
HTTP_DIR = ClientHTTPSCalculadora
PHP_DIR = calculadoraPHP
MQTT_DIR = MQTTMonitor
RPC_DIR = rpcKTOR

.PHONY: build build-rmi server-socket client-socket run-socket server-rmi client-rmi run-rmi server-rpc client-rpc run-rpc build-http client-http server-php mqtt-up mqtt-down mqtt-logs cat-service alarms sensor-sim

build:
	mkdir -p $(CLI_DIR)/classes
	javac -encoding windows-1252 -d $(CLI_DIR)/classes $(CLI_DIR)/src/CalculadoraClientSocket.java shared/Avaliador.java
	mkdir -p $(SRV_DIR)/classes
	javac -encoding windows-1252 -d $(SRV_DIR)/classes $(SRV_DIR)/Calculadora.java $(SRV_DIR)/CalculadoraServerSocket.java shared/Avaliador.java

# Compile to classes/ (not bin/) because Eclipse overwrites bin with its own output
build-rmi:
	mkdir -p $(RMI_DIR)/classes
	javac -encoding windows-1252 -d $(RMI_DIR)/classes $(RMI_DIR)/src/*.java shared/Avaliador.java

server-socket: build
	java -cp $(SRV_DIR)/classes CalculadoraServerSocket

client-socket: build
	java -cp $(CLI_DIR)/classes CalculadoraClientSocket

# One command: start socket server in background, open client, stop server on exit
run-socket: build
	bash -c 'java -cp $(SRV_DIR)/classes CalculadoraServerSocket & SRV=$$!; sleep 1; trap "kill $$SRV" EXIT; java -cp $(CLI_DIR)/classes CalculadoraClientSocket'

server-rmi: build-rmi
	java -cp $(RMI_DIR)/classes Calculadora

client-rmi: build-rmi
	java -cp $(RMI_DIR)/classes CalculadoraCliente

# One command: start RMI server (registry in-process) in background, open client, stop server on exit
run-rmi: build-rmi
	bash -c 'java -cp $(RMI_DIR)/classes Calculadora & SRV=$$!; sleep 1; trap "kill $$SRV" EXIT; java -cp $(RMI_DIR)/classes CalculadoraCliente'

server-rpc:
	cd $(RPC_DIR) && ./gradlew runServer

client-rpc:
	cd $(RPC_DIR) && ./gradlew runClient

# One command: start RPC server in background, open client, stop server on exit
run-rpc:
	cd $(RPC_DIR) && bash -c './gradlew runServer & SRV=$$!; sleep 5; trap "kill $$SRV" EXIT; ./gradlew runClient'

build-http:
	mkdir -p $(HTTP_DIR)/classes
	javac -encoding UTF-8 -d $(HTTP_DIR)/classes $(HTTP_DIR)/src/CalculadoraRestClient.java

client-http: build-http
	java -cp $(HTTP_DIR)/classes CalculadoraRestClient

# Local PHP server for the calculator page (http://localhost:8081);
# serve.php is the wrapper at PHP runtime: press b to block/unblock (500 any request), q to quit
server-php:
	php $(PHP_DIR)/serve.php

# Mosquitto broker via docker compose
mqtt-up:
	docker compose -f $(MQTT_DIR)/docker-compose.yml up -d

mqtt-down:
	docker compose -f $(MQTT_DIR)/docker-compose.yml down

mqtt-logs:
	docker logs -f mosquitto

# Simulador de sensores em Python ($(MQTT_DIR)/sensorSimulator): cria o venv na primeira execução
sensor-sim:
	cd $(MQTT_DIR)/sensorSimulator && bash -c '[ -x .venv/bin/python ] || (python3 -m venv .venv && .venv/bin/pip install -q paho-mqtt); .venv/bin/python simulador.py'

# Dependências (Paho) resolvidas pelo Gradle a partir do Maven Central
cat-service:
	cd $(MQTT_DIR) && ./gradlew :CATService:run

alarms:
	cd $(MQTT_DIR) && ./gradlew :Alarms:run
