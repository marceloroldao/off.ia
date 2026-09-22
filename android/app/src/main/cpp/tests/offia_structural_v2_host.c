#include "memoria_mobile.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CHECK(x) do {     if (!(x)) {         fprintf(stderr, "CHECK failed: %s:%d: %s\n", __FILE__, __LINE__, #x);         return 1;     } } while (0)

static memoria_mobile_status call_json(
    memoria_mobile_status (*fn)(
        memoria_mobile_handle *, memoria_mobile_buffer, memoria_mobile_buffer *
    ),
    memoria_mobile_handle *h,
    const char *json,
    memoria_mobile_buffer *out
) {
    memoria_mobile_buffer in = {
        (const uint8_t *)json,
        strlen(json),
    };
    return fn(h, in, out);
}

static void clear(memoria_mobile_buffer *out) {
    if (out->data) memoria_mobile_free_buffer(*out);
    out->data = NULL;
    out->size = 0u;
}

static int contains(memoria_mobile_buffer out, const char *needle) {
    return out.data && strstr((const char *)out.data, needle) != NULL;
}

static int before(memoria_mobile_buffer out, const char *left, const char *right) {
    const char *a;
    const char *b;
    if (!out.data) return 0;
    a = strstr((const char *)out.data, left);
    b = strstr((const char *)out.data, right);
    return a && b && a < b;
}

static int observe(
    memoria_mobile_handle *h,
    const char *hierarchy,
    const char *source_id,
    unsigned long sequence,
    const char *text
) {
    char json[2048];
    memoria_mobile_buffer out = {0};
    int n = snprintf(
        json,
        sizeof(json),
        "{\"hierarchy_id\":\"%s\",\"source_id\":\"%s\","
        "\"source_kind\":\"user_assertion\",\"sequence\":%lu,"
        "\"text\":\"%s\"}",
        hierarchy,
        source_id,
        sequence,
        text
    );
    CHECK(n > 0 && (size_t)n < sizeof(json));
    CHECK(call_json(
        memoria_mobile_observe_structural_text_json,
        h,
        json,
        &out
    ) == MEMORIA_MOBILE_OK);
    CHECK(contains(out, "\"status\":\"OK\""));
    clear(&out);
    return 0;
}

static int resolve(
    memoria_mobile_handle *h,
    const char *hierarchy,
    const char *query,
    memoria_mobile_buffer *out
) {
    char json[2048];
    int n = snprintf(
        json,
        sizeof(json),
        "{\"hierarchy_id\":\"%s\",\"query\":\"%s\",\"top_k\":3}",
        hierarchy,
        query
    );
    CHECK(n > 0 && (size_t)n < sizeof(json));
    return call_json(
        memoria_mobile_resolve_structural_text_json,
        h,
        json,
        out
    );
}

static int check_fact(memoria_mobile_handle *h) {
    memoria_mobile_buffer out = {0};
    CHECK(observe(h, "conversation:v2-fact", "fact-1", 1, "Meu gato se chama Alt.") == 0);
    CHECK(resolve(
        h,
        "conversation:v2-fact",
        "Como se chama meu gato?",
        &out
    ) == MEMORIA_MOBILE_OK);
    CHECK(contains(out, "\"status\":\"HIT\""));
    CHECK(contains(out, "Meu gato se chama Alt."));
    clear(&out);
    return 0;
}

static int check_conflict_recurrence(memoria_mobile_handle *h) {
    memoria_mobile_buffer out = {0};
    CHECK(observe(h, "conversation:v2-conflict", "conflict-1", 1, "Meu gato se chama Alt.") == 0);
    CHECK(observe(h, "conversation:v2-conflict", "conflict-2", 2, "Meu gato se chama Alt2.") == 0);
    CHECK(observe(h, "conversation:v2-conflict", "conflict-3", 3, "Meu gato se chama Alt2.") == 0);

    CHECK(resolve(
        h,
        "conversation:v2-conflict",
        "Como se chama meu gato?",
        &out
    ) == MEMORIA_MOBILE_OK);
    CHECK(contains(out, "Meu gato se chama Alt."));
    CHECK(contains(out, "Meu gato se chama Alt2."));
    CHECK(contains(out, "\"repetitions\":2"));
    CHECK(before(
        out,
        "\"source_text\":\"Meu gato se chama Alt2.\"",
        "\"source_text\":\"Meu gato se chama Alt.\""
    ));
    clear(&out);
    return 0;
}

static int check_unrelated(memoria_mobile_handle *h) {
    memoria_mobile_buffer out = {0};
    CHECK(observe(
        h,
        "conversation:v2-unrelated",
        "unrelated-1",
        1,
        "Meu gato se chama Alt."
    ) == 0);
    CHECK(resolve(
        h,
        "conversation:v2-unrelated",
        "Qual tensao ha na fonte da bancada?",
        &out
    ) == MEMORIA_MOBILE_UNRESOLVED);
    CHECK(contains(out, "\"status\":\"UNRESOLVED\""));
    CHECK(!contains(out, "Alt"));
    clear(&out);
    return 0;
}

static int check_restart(const char *dir) {
    memoria_mobile_handle *h = NULL;
    memoria_mobile_buffer out = {0};

    CHECK(memoria_mobile_open(dir, "offia-v2-host", &h) == MEMORIA_MOBILE_OK);
    CHECK(observe(
        h,
        "conversation:v2-restart",
        "restart-1",
        1,
        "Minha fonte e 12 V."
    ) == 0);
    CHECK(observe(
        h,
        "conversation:v2-restart",
        "restart-2",
        2,
        "Minha fonte e 24 V."
    ) == 0);
    CHECK(observe(
        h,
        "conversation:v2-restart",
        "restart-3",
        3,
        "Minha fonte e 24 V."
    ) == 0);
    CHECK(memoria_mobile_flush(h) == MEMORIA_MOBILE_OK);
    memoria_mobile_close(h);
    h = NULL;

    CHECK(memoria_mobile_open(dir, "offia-v2-host", &h) == MEMORIA_MOBILE_OK);
    CHECK(resolve(
        h,
        "conversation:v2-restart",
        "Qual tensao foi associada a minha fonte?",
        &out
    ) == MEMORIA_MOBILE_OK);
    CHECK(contains(out, "Minha fonte e 12 V."));
    CHECK(contains(out, "Minha fonte e 24 V."));
    CHECK(contains(out, "\"repetitions\":2"));
    CHECK(before(
        out,
        "\"source_text\":\"Minha fonte e 24 V.\"",
        "\"source_text\":\"Minha fonte e 12 V.\""
    ));
    clear(&out);
    memoria_mobile_close(h);
    return 0;
}

int main(void) {
    const char *dir = "./tmp-offia-v2-native-gate";
    memoria_mobile_handle *h = NULL;

    (void)system("rm -rf ./tmp-offia-v2-native-gate");
    CHECK(memoria_mobile_open(dir, "offia-v2-host", &h) == MEMORIA_MOBILE_OK);

    CHECK(check_fact(h) == 0);
    CHECK(check_conflict_recurrence(h) == 0);
    CHECK(check_unrelated(h) == 0);

    CHECK(memoria_mobile_flush(h) == MEMORIA_MOBILE_OK);
    memoria_mobile_close(h);
    h = NULL;

    CHECK(check_restart(dir) == 0);

    (void)system("rm -rf ./tmp-offia-v2-native-gate");
    puts("OFF.IA structural V2 native gate: PASS");
    return 0;
}
