# Captura experimental de respostas explícitas

Esta integração depende da Memoria.ia experimental em `marceloroldao/memoria.ia#374`.
Ela registra proveniência observada entre duas entradas do usuário na mesma conversa.
Não converte a resposta em fato e não altera a seleção de contexto do chat.

## Reproduzir no aplicativo de laboratório

1. Ative o modo de laboratório e envie uma pergunta, por exemplo, “Qual nome do meu pai?”.
2. Toque em **Responder a esta entrada** na própria mensagem do usuário.
3. Digite uma nova entrada, por exemplo, “Meu pai se chama PessoaA.”, e envie.
4. Em configurações, toque em **Exportar diagnóstico**.
5. No JSON exportado, procure a segunda entrada em `structural.observations`: ela deve conter
   `reply_to` com o `source_id` e `sequence` da pergunta. O vínculo é persistido pela
   Memoria.ia/BDR e reaparece após reiniciar o app.

O export também inclui `app.version_name`, os commits do APK e da Memoria.ia e
`app.laboratory_mode_enabled`. A seção `reply_capture` compara as marcações
explícitas ainda presentes no chat com os vínculos nativos: `selected_count`,
`pending_count`, `recorded_count`, `native_link_count` e inconsistências. Ela
usa apenas endereços e contagens; não classifica o texto como fato. Se uma
conversa foi apagada do app, o vínculo nativo ainda pode existir sem uma
marcação correspondente no chat atual.

Sem a seleção explícita, uma nova entrada não recebe `reply_to`, mesmo se vier logo depois
de uma pergunta. Mensagens geradas pela OFF.IA não podem ser selecionadas como alvo.
Um vínculo interrompido depois de salvar o chat fica pendente e é tentado novamente quando
a conversa é aberta; a operação nativa é idempotente.

O app mantém o resolvedor atual no fluxo de resposta. O modo nativo
`linked_reply_evidence` permanece apenas diagnóstico: devolve grupos sem qualificar ou
escolher uma resposta. Resultados reais precisam de um novo diagnóstico exportado por
esta versão experimental; o export anterior não contém vínculos.

## Consultar uma entrada no laboratório (alpha.14)

Em uma mensagem anterior do usuário, toque em **Ver vínculos na memória**.
O app consulta a Memoria.ia local pelo endereço exato da mensagem: conversa,
`source_id` e `sequence`. Mesmo que o texto apareça em outra conversa, o painel
mostra somente respostas explicitamente vinculadas à entrada selecionada.
Exibe a quantidade de vínculos, trilhas diferentes, perguntas repetidas e
exemplos de entradas vinculadas. `CANDIDATES` e `CONFLICT` descrevem a forma
dessas trilhas; não são validação da resposta. Esta leitura não alimenta a
resposta do chat, não escreve memória e não reforça nódulos.

O primeiro export real da alpha.13 já demonstrou uma seleção, uma gravação
nativa e um vínculo recuperável após reinício. A alpha.14 acrescenta apenas
a inspeção por endereço no app; ainda requer teste visual no dispositivo.
