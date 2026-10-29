package com.rpc

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class CalculatorServiceImpl : CalculatorService {
    override suspend fun somar(a: Double, b: Double): Double {
        println("[Servidor] Calculando soma: $a + $b")
        return a + b
    }

    override suspend fun subtrair(a: Double, b: Double): Double {
        println("[Servidor] Calculando subtração: $a - $b")
        return a - b
    }

    override suspend fun dividir(a: Double, b: Double): Double {
        println("[Servidor] Calculando divisão: $a / $b")
        return a / b
    }

    override suspend fun multiplicar(a: Double, b: Double): Double {
        println("[Servidor] Calculando multiplicação: $a * $b")
        return a * b
    }

    override suspend fun avaliar(expressao: String): Double {
        println("[Servidor] Avaliando expressão: $expressao")
        // Expressão com parênteses calculada inteiramente no servidor
        return Avaliador.avaliar(expressao) { op, a, b ->
            when (op) {
                1 -> a + b
                2 -> a - b
                3 -> a / b
                4 -> a * b
                else -> Double.NaN
            }
        }
    }
}

fun main() {
    val service: CalculatorService = CalculatorServiceImpl()

    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            post("/rpc") {
                val req = call.receive<RpcRequest>()
                try {
                    val resultado = when (req.method) {
                        "somar" -> service.somar(req.a, req.b)
                        "subtrair" -> service.subtrair(req.a, req.b)
                        "dividir" -> service.dividir(req.a, req.b)
                        "multiplicar" -> service.multiplicar(req.a, req.b)
                        "avaliar" -> service.avaliar(req.expressao ?: "")
                        else -> null
                    }

                    if (resultado != null) {
                        call.respond(RpcResponse(result = resultado))
                    } else {
                        call.respond(RpcResponse(error = "Método '${req.method}' desconhecido"))
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(RpcResponse(error = "Erro: ${e.message}"))
                } catch (e: IllegalStateException) {
                    call.respond(RpcResponse(error = "Erro: ${e.message}"))
                }
            }
        }
    }.start(wait = true)
}
