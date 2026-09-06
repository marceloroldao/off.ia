package ia.off

enum class MemoryRegressionCategory {
    FACT,
    CORRECTION,
    COLLECTION,
    RESTART,
    AUTHORITY,
    CONTAMINATION,
    CONTEXT,
}

data class MemoryRegressionScenario(
    val id: String,
    val category: MemoryRegressionCategory,
    val setupTurns: List<String>,
    val query: String,
    val expectedTerms: List<String>,
    val forbiddenTerms: List<String> = emptyList(),
    val requiresRestart: Boolean = false,
)

object MemoryRegressionCatalog {
    val scenarios: List<MemoryRegressionScenario> = listOf(
        MemoryRegressionScenario("fact-cat-name", MemoryRegressionCategory.FACT, listOf("Meu gato se chama Alt."), "Como se chama meu gato?", listOf("Alt")),
        MemoryRegressionScenario("fact-car-color", MemoryRegressionCategory.FACT, listOf("Meu carro é um Jetta azul."), "Qual é a cor do meu Jetta?", listOf("azul")),
        MemoryRegressionScenario("fact-city", MemoryRegressionCategory.FACT, listOf("A oficina que uso fica em Canoas."), "Onde fica a oficina que eu uso?", listOf("Canoas")),
        MemoryRegressionScenario("correction-cat-name", MemoryRegressionCategory.CORRECTION, listOf("Meu gato se chama Alt.", "Correção: agora considere que meu gato se chama Alt2."), "Como se chama meu gato?", listOf("Alt2"), listOf("Alt.")),
        MemoryRegressionScenario("correction-car-color", MemoryRegressionCategory.CORRECTION, listOf("Meu Corsa é branco.", "Correção: o Corsa é prata."), "Qual é a cor do meu Corsa?", listOf("prata"), listOf("branco")),
        MemoryRegressionScenario("correction-number", MemoryRegressionCategory.CORRECTION, listOf("Tenho 2 baterias no laboratório.", "Atualização: agora tenho 3 baterias."), "Quantas baterias tenho no laboratório?", listOf("3"), listOf("2")),
        MemoryRegressionScenario("collection-cats", MemoryRegressionCategory.COLLECTION, listOf("Tenho um gato chamado Alt.", "Também conheço um gato chamado Nino."), "Quais gatos você conhece?", listOf("Alt", "Nino")),
        MemoryRegressionScenario("collection-cars", MemoryRegressionCategory.COLLECTION, listOf("Na rua vi uma Kombi branca.", "Também vi um Corsa prata.", "Depois vi um Jetta azul."), "Quais carros eu vi na rua?", listOf("Kombi", "Corsa", "Jetta")),
        MemoryRegressionScenario("collection-devices", MemoryRegressionCategory.COLLECTION, listOf("Tenho um ESP32 no projeto A.", "Tenho outro ESP32 no projeto B."), "Quais projetos têm ESP32?", listOf("A", "B")),
        MemoryRegressionScenario("restart-fact", MemoryRegressionCategory.RESTART, listOf("Meu gato se chama Alt."), "Como se chama meu gato?", listOf("Alt"), requiresRestart = true),
        MemoryRegressionScenario("restart-correction", MemoryRegressionCategory.RESTART, listOf("Meu roteador é o R1.", "Correção: o roteador principal agora é o R2."), "Qual é meu roteador principal?", listOf("R2"), listOf("R1"), requiresRestart = true),
        MemoryRegressionScenario("restart-collection", MemoryRegressionCategory.RESTART, listOf("Vi uma Kombi branca.", "Vi um Jetta azul."), "Quais carros eu vi?", listOf("Kombi", "Jetta"), requiresRestart = true),
        MemoryRegressionScenario("authority-assistant", MemoryRegressionCategory.AUTHORITY, listOf("O usuário não informou nome para o cachorro."), "Qual é o nome do meu cachorro?", emptyList(), listOf("Rex", "Bob", "Thor")),
        MemoryRegressionScenario("authority-public-personal", MemoryRegressionCategory.AUTHORITY, listOf("Uma fonte pública diz que Saturno tem anéis."), "Qual é o nome do meu gato?", emptyList(), listOf("Saturno", "anéis")),
        MemoryRegressionScenario("authority-user-over-model", MemoryRegressionCategory.AUTHORITY, listOf("Para este projeto, considere o código interno como ORBITA-7."), "Qual é o código interno deste projeto?", listOf("ORBITA-7")),
        MemoryRegressionScenario("contamination-unrelated-cat", MemoryRegressionCategory.CONTAMINATION, listOf("Meu gato se chama Alt."), "Quanto é 2 + 3?", listOf("5"), listOf("Alt")),
        MemoryRegressionScenario("contamination-unrelated-car", MemoryRegressionCategory.CONTAMINATION, listOf("Meu carro é um Jetta azul."), "Qual é a capital do Brasil?", listOf("Brasília"), listOf("Jetta", "azul")),
        MemoryRegressionScenario("context-single-fact", MemoryRegressionCategory.CONTEXT, listOf("Meu gato se chama Alt.", "Meu carro é um Jetta azul.", "Minha bancada tem um osciloscópio."), "Como se chama meu gato?", listOf("Alt"), listOf("Jetta", "osciloscópio")),
        MemoryRegressionScenario("context-latest-correction", MemoryRegressionCategory.CONTEXT, listOf("A fonte é 12 V.", "Correção: para este teste a fonte é 24 V.", "O cabo é vermelho."), "Qual tensão devo usar neste teste?", listOf("24 V"), listOf("12 V", "cabo")),
        MemoryRegressionScenario("context-collection-scope", MemoryRegressionCategory.CONTEXT, listOf("Tenho um gato Alt.", "Tenho um carro Jetta.", "Tenho um osciloscópio de 10 MHz."), "Quais animais mencionei?", listOf("Alt"), listOf("Jetta", "osciloscópio")),
    )
}
