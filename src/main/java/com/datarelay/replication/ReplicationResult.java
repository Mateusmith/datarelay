package com.datarelay.replication;

public record ReplicationResult(long linhasLidas, long linhasEscritas, int lotes) {

    public ReplicationResult somar(ReplicationResult outro) {
        return new ReplicationResult(
            linhasLidas + outro.linhasLidas,
            linhasEscritas + outro.linhasEscritas,
            lotes + outro.lotes);
    }

    public static ReplicationResult vazio() {
        return new ReplicationResult(0, 0, 0);
    }
}
