package com.rpc

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Scanner

// Client Stub: abstrai a chamada remota como métodos locais
class CalculatorRpcClient(private val client: HttpClient, private val endpoint: String) : CalculatorService {
    private suspend fun invokeRemote(method: String, a: Double, b: Double): Double {
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(RpcRequest(method, a, b))
        }.body<RpcResponse>()

        return response.result ?: throw RuntimeException(response.error ?: "Erro RPC desconhecido")
    }

    override suspend fun somar(a: Double, b: Double): Double = invokeRemote("somar", a, b)
    override suspend fun subtrair(a: Double, b: Double): Double = invokeRemote("subtrair", a, b)
    override suspend fun dividir(a: Double, b: Double): Double = invokeRemote("dividir", a, b)
    override suspend fun multiplicar(a: Double, b: Double): Double = invokeRemote("multiplicar", a, b)

    override suspend fun avaliar(expressao: String): Double {
        val response = client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(RpcRequest(method = "avaliar", expressao = expressao))
        }.body<RpcResponse>()

        return response.result ?: throw RuntimeException(response.error ?: "Erro RPC desconhecido")
    }
}


// Mensagem de falha legível: quando o servidor não está no ar a exceção de
// conexão vem com message nula (imprimia "Falha RPC: null")
private fun mensagemFalha(e: Exception): String {
    var causa: Throwable? = e
    while (causa != null) {
        if (causa is java.net.ConnectException) {
            return "não foi possível conectar ao servidor em http://localhost:8080 — o servidor está no ar?"
        }
        causa = causa.cause
    }
    return e.message ?: e.toString()
}

fun main() = runBlocking {
    // Configura o HttpClient usando a engine nativa Java do JDK 17
    val httpClient = HttpClient(Java) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            // Opções do JavaHttpConfig suportadas pela engine Java
            dispatcher = Dispatchers.IO
        }
    }

    val calculator: CalculatorService = CalculatorRpcClient(httpClient, "http://localhost:8080/rpc")
    val scanner = Scanner(System.`in`)

    println("=== Cliente Calculadora RPC Conectado (Engine Java) ===")

    while (true) {
        print("\nDigite a operação (+, -, *, /, 'expr' ou 'sair'): ")
        val op = scanner.nextLine().trim()
        if (op.equals("sair", ignoreCase = true)) break

        if (op.equals("expr", ignoreCase = true)) {
            // Expressão matemática com parênteses em dois modos
            println("Modo de execução:")
            println("1 - Cliente decompõe em operações básicas remotas")
            println("2 - Expressão calculada inteiramente no servidor")
            print("Escolha o modo: ")
            val modo = scanner.nextLine().trim()
            print("Expressão (ex: (10 + 15) * 4): ")
            val expressao = scanner.nextLine().trim()

            try {
                val resultado = if (modo == "1") {
                    // O cliente decompõe: cada operador vira uma chamada RPC
                    Avaliador.avaliar(expressao) { opCod, a, b ->
                        when (opCod) {
                            1 -> calculator.somar(a, b)
                            2 -> calculator.subtrair(a, b)
                            3 -> calculator.dividir(a, b)
                            4 -> calculator.multiplicar(a, b)
                            else -> Double.NaN
                        }
                    }
                } else if (modo == "2") {
                    // O servidor avalia a expressão inteira
                    calculator.avaliar(expressao)
                } else {
                    println("Modo inválido!")
                    continue
                }
                println("-> Resposta remota do servidor: $resultado")
            } catch (e: Exception) {
                println("-> Falha RPC: ${mensagemFalha(e)}")
            }
            continue
        }

        if (op !in listOf("+", "-", "*", "/")) {
            println("Operação inválida! Use '+', '-', '*', '/' ou 'expr'.")
            continue
        }

        print("Primeiro número: ")
        val a = scanner.nextLine().trim().toDouble()

        print("Segundo número: ")
        val b = scanner.nextLine().trim().toDouble()

        try {
            // Chamada RPC tipada via interface
            val resultado = when (op) {
                "+" -> calculator.somar(a, b)
                "-" -> calculator.subtrair(a, b)
                "/" -> calculator.dividir(a, b)
                "*" -> calculator.multiplicar(a, b)
                else -> 0.0
            }
            println("-> Resposta remota do servidor: $resultado")
        } catch (e: Exception) {
            println("-> Falha RPC: ${mensagemFalha(e)}")
        }
    }

    httpClient.close()
    println("Cliente finalizado.")
}