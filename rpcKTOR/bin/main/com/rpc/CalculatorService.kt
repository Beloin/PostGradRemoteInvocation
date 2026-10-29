package com.rpc

import kotlinx.serialization.Serializable

// Payload no padrão JSON-RPC para transportar as chamadas
@Serializable
data class RpcRequest(
    val method: String,
    val a: Double = 0.0,
    val b: Double = 0.0,
    val expressao: String? = null
)

@Serializable
data class RpcResponse(
    val result: Double? = null,
    val error: String? = null
)

// Contrato de RPC da Calculadora
interface CalculatorService {
    suspend fun somar(a: Double, b: Double): Double
    suspend fun subtrair(a: Double, b: Double): Double
    suspend fun dividir(a: Double, b: Double): Double
    suspend fun multiplicar(a: Double, b: Double): Double

    // Expressão com parênteses calculada inteiramente no servidor
    suspend fun avaliar(expressao: String): Double
}