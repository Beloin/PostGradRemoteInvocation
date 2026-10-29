<?php
// Servidor da calculadora PHP com toggle de bloqueio em runtime:
// enquanto o servidor roda, a tecla B alterna entre aceitar requisições
// e responder 400 para qualquer request. O php -S roda como processo filho.
//
// Rode com: make server-php
// Durante a execução: B alterna o bloqueio, C-c (ou q) encerra

$flag = __DIR__ . '/.bloqueado';
@unlink($flag);
chdir(__DIR__);

// Teclas chegam sem precisar de Enter e sem eco (restaurado ao sair)
exec('stty -icanon -echo 2>/dev/null');

$processo = proc_open('php -S 0.0.0.0:8081 calculadora.php', [
    0 => ['pipe', 'r'],
    1 => ['pipe', 'w'],
    2 => ['pipe', 'w'],
], $pipes);

if (!$processo) {
    echo "Falha ao iniciar o servidor PHP\n";
    exit(1);
}

stream_set_blocking(STDIN, false);
stream_set_blocking($pipes[1], false);

// Estado inicial: aceitando requisições
$aceitando = true;
echo "Servidor no ar — aceitando requisições\n";
echo "Pressione B para alternar o bloqueio (retorna 500 em qualquer request), q para sair\n";

// Restaura o terminal e o estado ao sair
register_shutdown_function(function () use ($flag, $processo) {
    exec('stty icanon echo 2>/dev/null');
    @unlink($flag);
    proc_terminate($processo);
});

while (true) {
    // Propaga a saída do php -S para o terminal
    $saida = stream_get_contents($pipes[1]);
    if ($saida !== false && $saida !== '') {
        echo $saida;
    }

    // Tecla lida em tempo de execução do servidor
    $tecla = fread(STDIN, 1);
    if ($tecla === 'b' || $tecla === 'B') {
        $aceitando = !$aceitando;
        if ($aceitando) {
            @unlink($flag);
            echo "aceitando requisições\n";
        } else {
            file_put_contents($flag, '1');
            echo "rejeitando requisições (500)\n";
        }
    } else if ($tecla === 'q') {
        echo "Encerrando...\n";
        exit(0);
    } else if (proc_get_status($processo)['running'] === false) {
        echo "Servidor PHP encerrou inesperadamente\n";
        exit(1);
    }

    usleep(50_000);
}