package me.pompel.elauncher;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class UploadServer extends NanoHTTPD {
    private static final String TAG = "UploadServer";
    public static final String AUTH_USER = "elauncher";
    public static final String AUTH_REALM = "eLauncher upload";

    private final File root;
    private final String password;
    private final AssetManager assets;

    public UploadServer(Context ctx, int port, String rootPath, String password) {
        super(port);
        this.assets = ctx.getApplicationContext().getAssets();
        this.root = new File(rootPath == null || rootPath.isEmpty() ? "/sdcard" : rootPath);
        this.password = password == null ? "" : password;
        if (!root.exists()) root.mkdirs();
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (!password.isEmpty() && !isAuthorized(session)) {
            Response r = newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "auth required");
            r.addHeader("WWW-Authenticate", "Basic realm=\"" + AUTH_REALM + "\"");
            return r;
        }

        String uri = session.getUri() == null ? "/" : session.getUri();

        if (uri.equals("/") || uri.equals("/index.html")) {
            return serveAsset("web/index.html", "text/html; charset=utf-8");
        }
        if (uri.startsWith("/static/")) {
            return serveAsset("web/" + uri.substring("/static/".length()), null);
        }
        if (uri.startsWith("/api/")) {
            return handleApi(session, uri.substring("/api/".length()));
        }
        if (uri.startsWith("/files/")) {
            File f = resolveSafe(uri.substring("/files".length()));
            if (f == null || !f.exists() || f.isDirectory()) return notFound("not found");
            return serveFile(f);
        }
        return notFound("not found");
    }

    // ---- API ----

    private Response handleApi(IHTTPSession session, String op) {
        Map<String, List<String>> params = parseQuery(session);

        if (op.equals("list") && Method.GET.equals(session.getMethod())) {
            return apiList(firstParam(params, "path", "/"));
        }
        if (op.equals("upload") && Method.POST.equals(session.getMethod())) {
            Map<String, String> tmp = new HashMap<>();
            try {
                session.parseBody(tmp);
            } catch (IOException e) {
                return jsonError(500, "read error");
            } catch (ResponseException e) {
                return jsonError(e.getStatus().getRequestStatus(), e.getMessage());
            }
            // After parseBody, getParameters() includes both query params and form fields
            Map<String, List<String>> bodyParams = session.getParameters();
            return apiUpload(firstParam(params, "path", "/"), tmp, bodyParams);
        }
        if (op.equals("delete") && Method.POST.equals(session.getMethod())) {
            Map<String, String> tmp = new HashMap<>();
            try {
                session.parseBody(tmp);
            } catch (IOException e) {
                return jsonError(500, "read error");
            } catch (ResponseException e) {
                return jsonError(e.getStatus().getRequestStatus(), e.getMessage());
            }
            Map<String, List<String>> bodyParams = session.getParameters();
            return apiDelete(firstParam(params, "path", "/"), firstParam(bodyParams, "name", ""));
        }
        return jsonError(404, "unknown api op");
    }

    private Response apiList(String pathParam) {
        File dir = resolveSafe(pathParam);
        if (dir == null || !dir.exists() || !dir.isDirectory()) return jsonError(404, "directory not found");
        String rel = relativePath(dir);
        if (!rel.endsWith("/")) rel = rel + "/";

        File[] entries = dir.listFiles();
        if (entries == null) entries = new File[0];
        List<File> list = new ArrayList<>(Arrays.asList(entries));
        Collections.sort(list, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("{\"path\":").append(jsonString(rel))
                .append(",\"rootLabel\":").append(jsonString(rootLabel()))
                .append(",\"entries\":[");
        for (int i = 0; i < list.size(); i++) {
            File f = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":").append(jsonString(f.getName()))
                    .append(",\"dir\":").append(f.isDirectory())
                    .append(",\"size\":").append(f.length())
                    .append("}");
        }
        sb.append("]}");
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", sb.toString());
    }

    private Response apiUpload(String pathParam, Map<String, String> tmpFiles, Map<String, List<String>> bodyParams) {
        File dir = resolveSafe(pathParam);
        if (dir == null) return jsonError(400, "bad path");
        if (!dir.exists()) dir.mkdirs();
        if (!dir.isDirectory()) return jsonError(400, "not a directory");

        int saved = 0;
        for (Map.Entry<String, String> entry : tmpFiles.entrySet()) {
            String paramName = entry.getKey();
            String tmpPath = entry.getValue();
            List<String> nameList = bodyParams.get(paramName);
            String originalName = nameList != null && !nameList.isEmpty()
                    ? nameList.get(0)
                    : ("upload-" + System.currentTimeMillis());
            originalName = sanitize(originalName);
            File dest = uniquify(new File(dir, originalName));
            File src = new File(tmpPath);
            if (src.renameTo(dest) || copyFile(src, dest)) saved++;
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8",
                "{\"saved\":" + saved + "}");
    }

    private Response apiDelete(String pathParam, String name) {
        File dir = resolveSafe(pathParam);
        if (dir == null || !dir.isDirectory()) return jsonError(404, "directory not found");
        String sanitized = sanitize(name);
        if (sanitized.isEmpty() || sanitized.equals(".") || sanitized.equals("..")) {
            return jsonError(400, "bad name");
        }
        File victim;
        try {
            victim = new File(dir, sanitized).getCanonicalFile();
            String rootCanon = root.getCanonicalPath();
            if (!victim.getPath().equals(rootCanon) && !victim.getPath().startsWith(rootCanon + File.separator)) {
                return jsonError(400, "outside root");
            }
        } catch (IOException e) {
            return jsonError(400, "bad name");
        }
        if (!victim.exists()) return jsonError(404, "not found");
        boolean ok = victim.isDirectory() ? deleteRecursive(victim) : victim.delete();
        Log.i(TAG, "delete " + victim.getAbsolutePath() + " -> " + ok);
        if (!ok) return jsonError(500, "delete failed");
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}");
    }

    // ---- helpers ----

    private boolean isAuthorized(IHTTPSession session) {
        String header = session.getHeaders().get("authorization");
        if (header == null) header = session.getHeaders().get("Authorization");
        if (header == null || !header.startsWith("Basic ")) return false;
        try {
            byte[] decoded = Base64.decode(header.substring(6), Base64.DEFAULT);
            String creds = new String(decoded, "UTF-8");
            int colon = creds.indexOf(':');
            if (colon < 0) return false;
            return AUTH_USER.equals(creds.substring(0, colon)) && password.equals(creds.substring(colon + 1));
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return false;
        }
    }

    private File resolveSafe(String uri) {
        try {
            String decoded = URLDecoder.decode(uri == null ? "/" : uri, "UTF-8");
            File candidate = new File(root, decoded).getCanonicalFile();
            String rootCanon = root.getCanonicalPath();
            if (!candidate.getPath().equals(rootCanon)
                    && !candidate.getPath().startsWith(rootCanon + File.separator)) {
                return null;
            }
            return candidate;
        } catch (IOException e) {
            return null;
        }
    }

    private String relativePath(File f) {
        try {
            String rootCanon = root.getCanonicalPath();
            String here = f.getCanonicalPath();
            if (here.equals(rootCanon)) return "/";
            String rel = here.substring(rootCanon.length());
            if (!rel.startsWith("/")) rel = "/" + rel;
            return rel;
        } catch (IOException e) {
            return "/";
        }
    }

    private String rootLabel() {
        String name = root.getName();
        return (name == null || name.isEmpty()) ? root.getAbsolutePath() : name;
    }

    private Response serveAsset(String assetPath, String forcedMime) {
        try {
            InputStream in = assets.open(assetPath);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            byte[] bytes = out.toByteArray();
            String mime = forcedMime != null ? forcedMime : guessMime(assetPath);
            return newFixedLengthResponse(Response.Status.OK, mime,
                    new java.io.ByteArrayInputStream(bytes), bytes.length);
        } catch (IOException e) {
            return notFound("asset missing");
        }
    }

    private Response serveFile(File file) {
        try {
            FileInputStream in = new FileInputStream(file);
            String mime = guessMime(file.getName());
            Response r = newFixedLengthResponse(Response.Status.OK, mime, in, file.length());
            r.addHeader("Content-Disposition", "inline; filename=\"" + file.getName().replace("\"", "_") + "\"");
            return r;
        } catch (FileNotFoundException e) {
            return notFound("file not found");
        }
    }

    private Response notFound(String msg) {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", msg);
    }

    private Response jsonError(int code, String msg) {
        Response.Status status = Response.Status.lookup(code);
        if (status == null) status = Response.Status.INTERNAL_ERROR;
        return newFixedLengthResponse(status, "application/json",
                "{\"error\":" + jsonString(msg) + "}");
    }

    private Map<String, List<String>> parseQuery(IHTTPSession session) {
        Map<String, List<String>> out = new HashMap<>();
        String q = session.getQueryParameterString();
        if (q == null || q.isEmpty()) return out;
        for (String pair : q.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                k = URLDecoder.decode(k, "UTF-8");
                v = URLDecoder.decode(v, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {}
            List<String> bucket = out.get(k);
            if (bucket == null) { bucket = new ArrayList<>(); out.put(k, bucket); }
            bucket.add(v);
        }
        return out;
    }

    private String firstParam(Map<String, List<String>> params, String key, String fallback) {
        List<String> vals = params.get(key);
        if (vals == null || vals.isEmpty()) return fallback;
        return vals.get(0);
    }

    private static String sanitize(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        String n = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return n.isEmpty() ? ("upload-" + System.currentTimeMillis()) : n;
    }

    private static File uniquify(File f) {
        if (!f.exists()) return f;
        String base = f.getName();
        String stem = base;
        String ext = "";
        int dot = base.lastIndexOf('.');
        if (dot > 0) { stem = base.substring(0, dot); ext = base.substring(dot); }
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(f.getParent(), stem + "-" + i + ext);
            if (!candidate.exists()) return candidate;
        }
        return new File(f.getParent(), stem + "-" + System.currentTimeMillis() + ext);
    }

    private static boolean copyFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "copy failed", e);
            return false;
        }
    }

    private static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".log")) return "text/plain; charset=utf-8";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".xml")) return "application/xml";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".epub")) return "application/epub+zip";
        if (n.endsWith(".mobi")) return "application/x-mobipocket-ebook";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }
}
