SRV_DIR = JavaServerSocket_Calculadora
CLI_DIR = JavaClientSocket_Calculadora
RMI_DIR = ExemploRMI

.PHONY: build build-rmi server-socket client-socket run-socket server-rmi client-rmi run-rmi

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