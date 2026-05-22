package io.wasmcloud.endive.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OciFetcher {
    private static final Logger LOG = LoggerFactory.getLogger(OciFetcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ACCEPT_MANIFESTS = String.join(", ",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.oci.image.index.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json",
            "application/vnd.docker.distribution.manifest.list.v2+json");

    private static final String WASM_LAYER_TYPE = "application/vnd.wasm.content.layer.v1+wasm";

    private final HttpClient http;
    private final Set<String> insecureRegistries;

    public OciFetcher() {
        this(parseInsecureRegistries(System.getenv("ENDIVE_INSECURE_REGISTRIES")));
    }

    public OciFetcher(Set<String> insecureRegistries) {
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.insecureRegistries = insecureRegistries;
    }

    private static Set<String> parseInsecureRegistries(String csv) {
        var out = new HashSet<String>();
        if (csv == null || csv.isBlank()) return out;
        for (var entry : csv.split(",")) {
            var trimmed = entry.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private String scheme(String registry) {
        return insecureRegistries.contains(registry) ? "http" : "https";
    }

    /**
     * Resolve {@code ref} to wasm bytes. A local filesystem path that exists is read
     * verbatim; otherwise the value is parsed as an OCI image reference and pulled
     * over the Docker Registry v2 protocol (anonymous bearer-token flow).
     */
    public byte[] fetch(String ref) throws IOException, InterruptedException {
        var localPath = Path.of(ref);
        if (Files.exists(localPath)) {
            return Files.readAllBytes(localPath);
        }
        if (!OciRef.looksLikeRef(ref)) {
            throw new IOException("not a local file and not a valid OCI reference: " + ref);
        }
        var parsed = OciRef.parse(ref);
        LOG.info("Pulling OCI ref {} (registry={}, repo={}, reference={})",
                ref, parsed.registry, parsed.repo, parsed.reference);

        var token = obtainToken(parsed);
        var manifest = fetchManifest(parsed, parsed.reference, token);

        var mediaType = manifest.path("mediaType").asText("");
        if (mediaType.contains("manifest.list") || mediaType.contains("image.index")) {
            var first = manifest.path("manifests").path(0);
            if (first.isMissingNode() || first.isNull()) {
                throw new IOException("OCI index has no manifests: " + ref);
            }
            manifest = fetchManifest(parsed, first.path("digest").asText(), token);
        }

        var layers = manifest.path("layers");
        if (!layers.isArray() || layers.isEmpty()) {
            throw new IOException("OCI manifest has no layers: " + ref);
        }
        JsonNode wasmLayer = null;
        for (var layer : layers) {
            if (WASM_LAYER_TYPE.equals(layer.path("mediaType").asText())) {
                wasmLayer = layer;
                break;
            }
        }
        if (wasmLayer == null && layers.size() == 1) {
            wasmLayer = layers.get(0);
        }
        if (wasmLayer == null) {
            throw new IOException("no wasm layer in manifest for " + ref);
        }
        return fetchBlob(parsed, wasmLayer.path("digest").asText(), token);
    }

    private JsonNode fetchManifest(OciRef ref, String reference, String token) throws IOException, InterruptedException {
        var url = URI.create(scheme(ref.registry) + "://" + ref.registry + "/v2/" + ref.repo + "/manifests/" + reference);
        var req = HttpRequest.newBuilder(url)
                .header("Accept", ACCEPT_MANIFESTS);
        if (token != null) req.header("Authorization", "Bearer " + token);
        var resp = http.send(req.GET().build(), BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("manifest fetch failed: " + resp.statusCode() + " " + resp.body());
        }
        return MAPPER.readTree(resp.body());
    }

    private byte[] fetchBlob(OciRef ref, String digest, String token) throws IOException, InterruptedException {
        var url = URI.create(scheme(ref.registry) + "://" + ref.registry + "/v2/" + ref.repo + "/blobs/" + digest);
        var req = HttpRequest.newBuilder(url);
        if (token != null) req.header("Authorization", "Bearer " + token);
        var resp = http.send(req.GET().build(), BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("blob fetch failed: " + resp.statusCode());
        }
        return resp.body();
    }

    /**
     * Negotiate an anonymous bearer token for the repository. Probes the manifest
     * URL with no credentials; on 401 with a {@code WWW-Authenticate: Bearer ...}
     * header, follows the realm/service/scope into a token endpoint.
     */
    private String obtainToken(OciRef ref) throws IOException, InterruptedException {
        var url = URI.create(scheme(ref.registry) + "://" + ref.registry + "/v2/" + ref.repo + "/manifests/" + ref.reference);
        var probe = http.send(
                HttpRequest.newBuilder(url).header("Accept", ACCEPT_MANIFESTS).GET().build(),
                BodyHandlers.discarding());
        if (probe.statusCode() != 401) return null;

        var challenge = probe.headers().firstValue("www-authenticate")
                .or(() -> probe.headers().firstValue("WWW-Authenticate"))
                .orElseThrow(() -> new IOException("401 without WWW-Authenticate from " + url));
        var params = parseChallenge(challenge);
        var realm = params.get("realm");
        if (realm == null) throw new IOException("WWW-Authenticate missing realm: " + challenge);

        var qs = new StringBuilder(realm);
        boolean firstParam = !realm.contains("?");
        for (var key : new String[]{"service", "scope"}) {
            var val = params.get(key);
            if (val == null || val.isEmpty()) continue;
            qs.append(firstParam ? '?' : '&');
            firstParam = false;
            qs.append(key).append('=').append(URLEncoder.encode(val, StandardCharsets.UTF_8));
        }
        var tokenResp = http.send(
                HttpRequest.newBuilder(URI.create(qs.toString())).GET().build(),
                BodyHandlers.ofString());
        if (tokenResp.statusCode() / 100 != 2) {
            throw new IOException("token fetch failed: " + tokenResp.statusCode() + " " + tokenResp.body());
        }
        var node = MAPPER.readTree(tokenResp.body());
        var token = node.path("token").asText(null);
        if (token == null || token.isEmpty()) token = node.path("access_token").asText(null);
        if (token == null || token.isEmpty()) {
            throw new IOException("token response missing token: " + tokenResp.body());
        }
        return token;
    }

    private static final Pattern CHALLENGE = Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");

    static Map<String, String> parseChallenge(String header) {
        var out = new HashMap<String, String>();
        Matcher m = CHALLENGE.matcher(header);
        while (m.find()) {
            out.put(m.group(1).toLowerCase(), m.group(2));
        }
        return out;
    }

    /** A parsed OCI image reference. */
    static final class OciRef {
        final String registry;
        final String repo;
        final String reference; // tag or "sha256:..."

        OciRef(String registry, String repo, String reference) {
            this.registry = registry;
            this.repo = repo;
            this.reference = reference;
        }

        static boolean looksLikeRef(String s) {
            if (s == null || s.isEmpty()) return false;
            int slash = s.indexOf('/');
            if (slash <= 0) return false;
            // Need either a :tag or @digest somewhere after the registry/repo split.
            // (The first ':' may belong to a registry port — don't be fooled.)
            String rest = s.substring(slash + 1);
            return rest.indexOf(':') >= 0 || rest.indexOf('@') >= 0;
        }

        static OciRef parse(String s) {
            int slash = s.indexOf('/');
            String registry = s.substring(0, slash);
            String rest = s.substring(slash + 1);

            String repo;
            String reference;
            int at = rest.indexOf('@');
            if (at >= 0) {
                repo = rest.substring(0, at);
                reference = rest.substring(at + 1);
            } else {
                int colon = rest.lastIndexOf(':');
                if (colon < 0) {
                    repo = rest;
                    reference = "latest";
                } else {
                    repo = rest.substring(0, colon);
                    reference = rest.substring(colon + 1);
                }
            }
            return new OciRef(registry, repo, reference);
        }
    }
}
