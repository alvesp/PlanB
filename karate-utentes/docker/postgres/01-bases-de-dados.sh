#!/bin/bash
# Uma base de dados por serviço, cada uma com o seu utilizador (dono da base).
# Corre só na primeira inicialização do volume, como superutilizador.
set -euo pipefail

criar_base() {
    local base="$1" utilizador="$2" password="$3"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -v base="$base" -v utilizador="$utilizador" -v password="$password" <<'SQL'
CREATE ROLE :"utilizador" LOGIN PASSWORD :'password';
CREATE DATABASE :"base" OWNER :"utilizador";
-- Só o dono se pode ligar: os outros serviços não veem esta base.
REVOKE ALL ON DATABASE :"base" FROM PUBLIC;
SQL
}

criar_base utentes      "$UTENTES_DB_USER"      "$UTENTES_DB_PASSWORD"
criar_base aprendizagem "$APRENDIZAGEM_DB_USER" "$APRENDIZAGEM_DB_PASSWORD"
