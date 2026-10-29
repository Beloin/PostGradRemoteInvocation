<?php
// Calculadora remota via POST
// Evolução:
// 1. API REST igual à do servidor remoto: POST /operation/{soma|subtracao|multiplicacao|divisao}/{a}/{b}
//    retornando {"result": <valor>} (o cliente Java usa esse formato)
// 2. Página com formulário HTML que calcula via POST (campos oper1, oper2, operacao)
// 3. Endpoint JSON compatível com clientes que enviam campos de formulário

// Calcula uma operação; devolve um array [resultado, erro]
function calcular($operacao, $oper1, $oper2) {
    switch ($operacao) {
        case 1: // Soma
            return [$oper1 + $oper2, null];
        case 2: // Subtração
            return [$oper1 - $oper2, null];
        case 3: // Multiplicação
            return [$oper1 * $oper2, null];
        case 4: // Divisão
            if ($oper2 == 0) {
                return [null, "Erro: divisão por zero."];
            }
            return [$oper1 / $oper2, null];
        default:
            return [null, "Operação inválida. Use 1 (soma), 2 (subtração), 3 (multiplicação) ou 4 (divisão)."];
    }
}

// ---------- API REST (mesma API do servidor remoto) ----------

// Bloqueio de requisições (make php: tecla b no servidor):
// com o toggle ativo toda requisição recebe 500 (erros 5xx são reintentados pelo cliente)
if (file_exists(__DIR__ . '/.bloqueado')) {
    header('Content-Type: application/json');
    http_response_code(500);
    echo json_encode(["error" => "Servidor indisponível (500) — servidor bloqueado"]);
    exit;
}

// A página PHP é usada como roteador do servidor embutido do PHP:
// toda requisição chega aqui, então a rota é extraída da URI.
$rota = $_SERVER['REQUEST_URI'] ?? '';
if (preg_match('#^/operation/([a-z]+)/(-?[0-9.]+)/(-?[0-9.]+)#', $rota, $partes)) {
    header('Content-Type: application/json');

    $codigoPorNome = ['soma' => 1, 'subtracao' => 2, 'multiplicacao' => 3, 'divisao' => 4];
    $nomeOperacao = $partes[1];

    if (!isset($codigoPorNome[$nomeOperacao])) {
        echo json_encode(["error" => "Operação '$nomeOperacao' desconhecida"]);
        exit;
    }

    list($resultado, $erro) = calcular($codigoPorNome[$nomeOperacao], floatval($partes[2]), floatval($partes[3]));
    if ($erro) {
        echo json_encode(["error" => $erro]);
    } else {
        echo json_encode(["result" => $resultado]);
    }
    exit;
}

// ---------- Endpoint JSON com campos de formulário (API antiga) ----------

// Função para validar e converter valores
function getPostValue($key) {
    return isset($_POST[$key]) ? floatval($_POST[$key]) : null;
}

$oper1 = getPostValue('oper1');
$oper2 = getPostValue('oper2');
$operacao = isset($_POST['operacao']) ? intval($_POST['operacao']) : null;

$resultado = null;
$erro = null;

// Calcula somente quando algum parâmetro chegou via POST
$temParametros = ($oper1 !== null || $oper2 !== null || $operacao !== null);

if ($temParametros) {
    if ($oper1 === null || $oper2 === null || $operacao === null) {
        $erro = "Parâmetros inválidos. Envie 'oper1', 'oper2' e 'operacao' via POST.";
    } else {
        list($resultado, $erro) = calcular($operacao, $oper1, $oper2);
    }
}

// O formulário HTML envia 'formato' = 'html' para receber a resposta em HTML;
// sem 'formato' a resposta continua em JSON (compatível com clientes REST)
$formatoHtml = (isset($_POST['formato']) && $_POST['formato'] === 'html');

if ($formatoHtml || !$temParametros) {
    // Resposta em HTML com o formulário para calcular via POST
    header('Content-Type: text/html; charset=utf-8');
    ?>
<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <title>Calculadora PHP</title>
</head>
<body>
    <h1>Calculadora PHP (POST)</h1>

    <!-- O formulário envia os campos para esta mesma página via POST -->
    <form method="POST" action="">
        <label>Operador 1:
            <input name="oper1" value="<?= htmlspecialchars((string) ($oper1 ?? '')) ?>">
        </label><br><br>
        <label>Operador 2:
            <input name="oper2" value="<?= htmlspecialchars((string) ($oper2 ?? '')) ?>">
        </label><br><br>
        <label>Operação:
            <select name="operacao">
                <?php $operacoes = [1 => 'Soma', 2 => 'Subtração', 3 => 'Multiplicação', 4 => 'Divisão']; ?>
                <?php foreach ($operacoes as $cod => $nome): ?>
                <option value="<?= $cod ?>" <?= ($operacao === $cod) ? 'selected' : '' ?>><?= $nome ?></option>
                <?php endforeach; ?>
            </select>
        </label><br><br>
        <input type="hidden" name="formato" value="html">
        <button type="submit">Calcular</button>
    </form>

    <?php if ($temParametros): ?>
        <?php if ($erro !== null): ?>
            <p style="color: red;">Erro: <?= htmlspecialchars($erro) ?></p>
        <?php else: ?>
            <p>Resultado: <strong><?= htmlspecialchars((string) $resultado) ?></strong></p>
        <?php endif; ?>
    <?php endif; ?>
</body>
</html>
    <?php
    exit;
}

// Retorno em JSON (API antiga)
header('Content-Type: application/json');

if ($erro) {
    echo json_encode(["erro" => $erro]);
} else {
    echo json_encode([
        "oper1" => $oper1,
        "oper2" => $oper2,
        "operacao" => $operacao,
        "resultado" => $resultado
    ]);
}