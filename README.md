# tv-precos — Painéis de preço das TVs (WR)

Repositório que alimenta os painéis de preço das TV boxes das lojas.
Cada TV tem a sua própria pasta, com o layout, a config e o controle de
atualização. O APK instalado na box navega essas pastas, você seleciona a
pasta daquela TV, e pronto.

## Estrutura

```
tv/<loja>/<setor>/<tvN>/
    config.json    # servidor, cod_tv, titulo, orientacao daquela TV
    index.html     # o layout DAQUELA TV (deitada ou em pe, como quiser)
    version.txt    # mude este arquivo pra forcar a TV a atualizar (OTA)
```

Exemplo que já está pronto: `tv/loja2/acougue/tv1/`

## config.json

```json
{
  "server": "http://10.6.2.253:9001/tv",
  "cod_tv": 1,
  "titulo": "Acougue",
  "orientacao": "paisagem"
}
```

- **server** — endereço do servidor de TV daquela loja (o mesmo que abre no navegador).
- **cod_tv** — o número da TV (o `?cod_tv=` da URL).
- **titulo** — o que aparece no topo do painel.
- **orientacao** — `paisagem` (deitada) ou `retrato` (em pe). O app gira a tela conforme isso.

## Como criar uma TV nova

1. Copie a pasta `_modelo/paisagem` (TV deitada) ou `_modelo/retrato` (TV em pe)
   para dentro de `tv/<loja>/<setor>/<tvN>/`.
2. Edite o `config.json` com o servidor, cod_tv, titulo e orientacao daquela TV.
3. Ajuste o `index.html` se quiser um visual diferente pra essa TV.
4. Commit/push. Na box, selecione essa pasta.

## Como atualizar o visual de uma TV

1. Edite o `index.html` da pasta daquela TV.
2. **Troque o conteudo do `version.txt`** da mesma pasta (ex: ponha a data/hora).
   Sem mudar o version.txt, o app acha que nada mudou e nao baixa.
3. Commit/push. Em ate X minutos aquela TV baixa e atualiza sozinha.

> Cada TV tem seu `version.txt`, entao atualizar uma nao mexe nas outras.

## _modelo/

Pasta com os layouts-base (paisagem e retrato) pra copiar quando criar TV nova.
Nao e usada por nenhuma box diretamente; e so o ponto de partida.

## Seguranca

Este repo e publico. NUNCA coloque aqui senha de banco, o `config.ini` do
servidor, nem credencial nenhuma. O `config.json` da TV so precisa da URL do
webserver — nada de senha.
