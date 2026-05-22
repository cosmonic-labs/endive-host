package io.wasmcloud.endive.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OciFetcherTest {

    @Test
    void looksLikeRef_acceptsRegistryWithPort() {
        // Regression: the first ':' belongs to the registry port, not the tag separator.
        assertTrue(OciFetcher.OciRef.looksLikeRef("registry:5000/hello:demo"));
    }

    @Test
    void looksLikeRef_acceptsHostedRegistry() {
        assertTrue(OciFetcher.OciRef.looksLikeRef("ghcr.io/wasmcloud/x:0.1.0"));
    }

    @Test
    void looksLikeRef_acceptsDigest() {
        assertTrue(OciFetcher.OciRef.looksLikeRef(
                "ghcr.io/wasmcloud/x@sha256:abcdef0123456789"));
    }

    @Test
    void looksLikeRef_rejectsLocalPaths() {
        assertFalse(OciFetcher.OciRef.looksLikeRef("hello.wasm"));
        assertFalse(OciFetcher.OciRef.looksLikeRef("examples/hello.wasm"));
        assertFalse(OciFetcher.OciRef.looksLikeRef("/abs/path.wasm"));
    }

    @Test
    void looksLikeRef_rejectsBareRepoWithoutSplit() {
        // Has no '/', no way to split registry/repo.
        assertFalse(OciFetcher.OciRef.looksLikeRef("hello:0.1.0"));
    }

    @Test
    void looksLikeRef_rejectsNullAndEmpty() {
        assertFalse(OciFetcher.OciRef.looksLikeRef(null));
        assertFalse(OciFetcher.OciRef.looksLikeRef(""));
    }

    @Test
    void parse_simpleTag() {
        var r = OciFetcher.OciRef.parse("ghcr.io/wasmcloud/x:0.1.0");
        assertEquals("ghcr.io", r.registry);
        assertEquals("wasmcloud/x", r.repo);
        assertEquals("0.1.0", r.reference);
    }

    @Test
    void parse_registryWithPort() {
        var r = OciFetcher.OciRef.parse("registry:5000/hello:demo");
        assertEquals("registry:5000", r.registry);
        assertEquals("hello", r.repo);
        assertEquals("demo", r.reference);
    }

    @Test
    void parse_atDigest() {
        var r = OciFetcher.OciRef.parse(
                "ghcr.io/wasmcloud/x@sha256:abcdef0123456789");
        assertEquals("ghcr.io", r.registry);
        assertEquals("wasmcloud/x", r.repo);
        assertEquals("sha256:abcdef0123456789", r.reference);
    }

    @Test
    void parse_defaultsToLatestWhenNoTag() {
        var r = OciFetcher.OciRef.parse("ghcr.io/wasmcloud/x");
        assertEquals("ghcr.io", r.registry);
        assertEquals("wasmcloud/x", r.repo);
        assertEquals("latest", r.reference);
    }

    @Test
    void parseChallenge_extractsRealmServiceScope() {
        var params = OciFetcher.parseChallenge(
                "Bearer realm=\"https://ghcr.io/token\","
                        + "service=\"ghcr.io\","
                        + "scope=\"repository:foo/bar:pull\"");
        assertEquals("https://ghcr.io/token", params.get("realm"));
        assertEquals("ghcr.io", params.get("service"));
        assertEquals("repository:foo/bar:pull", params.get("scope"));
    }

    @Test
    void parseChallenge_lowercasesKeys() {
        var params = OciFetcher.parseChallenge("Bearer Realm=\"x\",Service=\"y\"");
        assertEquals("x", params.get("realm"));
        assertEquals("y", params.get("service"));
    }

    @Test
    void parseChallenge_handlesEmptyValues() {
        var params = OciFetcher.parseChallenge("Bearer realm=\"\",service=\"a\"");
        assertEquals("", params.get("realm"));
        assertEquals("a", params.get("service"));
    }
}
