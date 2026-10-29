package com.rpc

/**
 * Tokenizer é uma máquina de estados sobre os caracteres da expressão;
 * shunting-yard monta a fila pós-fixa usando uma pilha de operadores;
 * avaliar percorre a fila pós-fixa com uma pilha de operandos.
 * Cada operador binário é delegado para aplicar.op(op, a, b).
 */
typealias OperacaoBinaria = suspend (op: Int, a: Double, b: Double) -> Double

object Avaliador {

    // Tokeniza a expressão: números com ponto, + - * /, parênteses
    fun tokenizar(expressao: String): List<String> {
        val tokens = mutableListOf<String>()
        val numero = StringBuilder()
        var estado = 0 // 0 = fora de um número, 1 = dentro de um número
        for (c in expressao) {
            if (c.isDigit() || c == '.') {
                if (estado == 0) {
                    numero.setLength(0)
                    estado = 1
                }
                numero.append(c)
            } else {
                if (estado == 1) {
                    tokens.add(numero.toString())
                    estado = 0
                }
                when (c) {
                    '(', ')', '+', '-', '*', '/' -> tokens.add(c.toString())
                    ' ' -> {}
                    else -> throw IllegalArgumentException("caractere invalido: $c")
                }
            }
        }
        if (estado == 1) {
            tokens.add(numero.toString())
        }
        return tokens
    }

    private fun precedencia(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/" -> 2
        else -> 0
    }

    private fun codigo(op: String): Int = when (op) {
        "+" -> 1
        "-" -> 2
        "/" -> 3
        "*" -> 4
        else -> throw IllegalArgumentException("operador invalido: $op")
    }

    // Shunting-yard: converte a notação infixa para pós-fixa (RPN)
    fun posfixa(expressao: String): ArrayDeque<String> {
        val fila = ArrayDeque<String>()
        val pilha = ArrayDeque<String>() // pilha de operadores
        for (token in tokenizar(expressao)) {
            when {
                token == "(" -> pilha.addFirst(token)
                token == ")" -> {
                    while (pilha.isNotEmpty() && pilha.first() != "(") {
                        fila.add(pilha.removeFirst())
                    }
                    if (pilha.isEmpty()) {
                        throw IllegalArgumentException("parêntese desbalanceado")
                    }
                    pilha.removeFirst()
                }
                precedencia(token) > 0 -> {
                    while (pilha.isNotEmpty() && precedencia(pilha.first()) >= precedencia(token)) {
                        fila.add(pilha.removeFirst())
                    }
                    pilha.addFirst(token)
                }
                else -> fila.add(token) // número
            }
        }
        while (pilha.isNotEmpty()) {
            val op = pilha.removeFirst()
            if (op == "(") {
                throw IllegalArgumentException("parêntese desbalanceado")
            }
            fila.add(op)
        }
        return fila
    }

    // Avalia a expressão; cada operador chama aplicar
    suspend fun avaliar(expressao: String, aplicar: OperacaoBinaria): Double {
        val pilha = ArrayDeque<Double>() // pilha de operandos
        for (token in posfixa(expressao)) {
            if (precedencia(token) > 0) {
                if (pilha.size < 2) {
                    throw IllegalArgumentException("expressão invalida")
                }
                val b = pilha.removeFirst()
                val a = pilha.removeFirst()
                pilha.addFirst(aplicar(codigo(token), a, b))
            } else {
                pilha.addFirst(token.toDouble())
            }
        }
        if (pilha.size != 1) {
            throw IllegalArgumentException("expressão invalida")
        }
        return pilha.removeFirst()
    }
}