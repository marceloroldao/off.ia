from pathlib import Path

# Pin OFF.IA Android to the validated Memoria.ia post-v1 parser fix.
w = Path('.github/workflows/android-apk.yml')
s = w.read_text(encoding='utf-8')
old_ref = 'ref: e91eabeebdee11472a1ea09ef7ea51ca2aeb3fa7'
new_ref = 'ref: 6b15e2ed8d87fe610cb48f0702e0f9fc54b3273b'
if old_ref not in s:
    raise SystemExit('old Memoria.ia pin not found')
w.write_text(s.replace(old_ref, new_ref, 1), encoding='utf-8')

# Harden Kotlin validation and ensure provenance metadata precedes raw Web
# content in the serialized object. This avoids the legacy C string scanner
# accidentally seeing content before contract keys while post-v1 transitions
# to the decoded parser.
p = Path('android/app/src/main/java/ia/off/NativeMemoryGateway.kt')
s = p.read_text(encoding='utf-8')
old_validation = '''            require(source.sourceUrl.isNotBlank()) { "URL de origem pública vazia" }
            require(source.sourceDomain.isNotBlank()) { "Domínio de origem pública vazio" }
            require(source.sourceTitle.isNotBlank()) { "Título de origem pública vazio" }
'''
new_validation = '''            require(source.sourceUrl.startsWith("https://") || source.sourceUrl.startsWith("http://")) {
                "URL de origem pública deve usar http(s)"
            }
            require(source.sourceDomain.isNotBlank() && source.sourceDomain.none { it.isWhitespace() || it == '/' || it == '\\\\' }) {
                "Domínio de origem pública inválido"
            }
            require(source.sourceTitle.isNotBlank()) { "Título de origem pública vazio" }
'''
if old_validation not in s:
    raise SystemExit('validation anchor not found')
s = s.replace(old_validation, new_validation, 1)
old_request = '''            val request = JSONObject().apply {
                put("content", source.content)
                put("source_class", "external_public")
                put("source_url", source.sourceUrl)
                put("source_domain", source.sourceDomain)
                put("source_title", source.sourceTitle)
                put("acquired_time", source.acquiredTime)
                put("source_excerpt", source.sourceExcerpt)
                put("provider_id", source.providerId)
                put("import_kind", source.importKind)
                put("validation_confidence", source.validationConfidence)
                put("request_id", source.requestId)
                put("session_id", source.sessionId)
                put("namespace", source.namespace)
                put("parent_memory_ids", JSONArray(source.parentMemoryIds))
            }
'''
new_request = '''            val request = JSONObject().apply {
                // Keep contract/provenance keys ahead of untrusted raw Web content.
                put("source_class", "external_public")
                put("source_url", source.sourceUrl)
                put("source_domain", source.sourceDomain)
                put("source_title", source.sourceTitle)
                put("acquired_time", source.acquiredTime)
                put("source_excerpt", source.sourceExcerpt)
                put("provider_id", source.providerId)
                put("import_kind", source.importKind)
                put("validation_confidence", source.validationConfidence)
                put("request_id", source.requestId)
                put("session_id", source.sessionId)
                put("namespace", source.namespace)
                put("parent_memory_ids", JSONArray(source.parentMemoryIds))
                put("content", source.content)
            }
'''
if old_request not in s:
    raise SystemExit('request anchor not found')
p.write_text(s.replace(old_request, new_request, 1), encoding='utf-8')
