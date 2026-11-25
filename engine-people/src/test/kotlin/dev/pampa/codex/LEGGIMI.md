# La copia di riferimento di Codex

Questi file sono una **copia di sola lettura** di `C:\VibeCoded Projects\Codex`, commit `fa621f4` (25/11/2026):
`core/crypto/src/main/kotlin/dev/pampa/codex/crypto/` (`Bytes`, `Base32`, `Digests`, `Hkdf`, `Aead`, `MediaCipher`,
`IdentityKeys`, `CodexId`, `NearbyHandshake`) e `core/seal/.../seal/` (`SeededRandom`, `RuneAlphabet`).

Stanno solo fra le prove di `engine-people` (F10.6): servono a `CodexVectorsTest`, che controlla che la crittografia
portata nell'engine dia **gli stessi byte** di quella di Codex dove il formato è tenuto così com'è (`CDXM`, base32 di
Crockford, i campi a lunghezza fissa, il generatore dei sigilli e l'alfabeto delle rune). La repo di Codex non si
modifica; se un giorno cambiasse, questi file restano la fotografia di riferimento.
