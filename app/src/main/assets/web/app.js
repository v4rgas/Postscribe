(function () {
    "use strict";

    const $ = (sel) => document.querySelector(sel);
    const entriesEl = $("#entries");
    const emptyEl = $("#empty");
    const crumbsEl = $("#crumbs");
    const hereEl = $("#here");
    const dropzone = $("#dropzone");
    const fileInput = $("#file-input");
    const queueEl = $("#queue");
    const uploadBtn = $("#upload-btn");
    const uploadForm = $("#upload-form");
    const toast = $("#toast");

    let currentPath = "/";
    let queued = [];

    function currentPathFromUrl() {
        const params = new URLSearchParams(window.location.search);
        let p = params.get("path") || "/";
        if (!p.startsWith("/")) p = "/" + p;
        if (!p.endsWith("/")) p += "/";
        return p;
    }

    function navigate(path) {
        const url = new URL(window.location);
        url.searchParams.set("path", path);
        history.pushState({ path }, "", url);
        currentPath = path;
        load();
    }

    function humanSize(n) {
        if (n < 1024) return n + " B";
        const units = ["KB", "MB", "GB", "TB"];
        let i = -1;
        do { n /= 1024; i++; } while (n >= 1024 && i < units.length - 1);
        return n.toFixed(1) + " " + units[i];
    }

    function showToast(msg, isError) {
        toast.textContent = msg;
        toast.classList.toggle("error", !!isError);
        toast.hidden = false;
        clearTimeout(showToast._t);
        showToast._t = setTimeout(() => { toast.hidden = true; }, 2500);
    }

    function renderCrumbs(path, rootLabel) {
        crumbsEl.innerHTML = "";
        const home = document.createElement("a");
        home.href = "#";
        home.textContent = rootLabel || "root";
        home.onclick = (e) => { e.preventDefault(); navigate("/"); };
        crumbsEl.appendChild(home);
        const trimmed = path.replace(/^\/+|\/+$/g, "");
        if (!trimmed) return;
        const parts = trimmed.split("/");
        let accum = "/";
        parts.forEach((p, i) => {
            accum += p + "/";
            const sep = document.createElement("span");
            sep.className = "sep";
            sep.textContent = "/";
            crumbsEl.appendChild(sep);
            if (i === parts.length - 1) {
                const leaf = document.createElement("span");
                leaf.className = "leaf";
                leaf.textContent = p;
                crumbsEl.appendChild(leaf);
            } else {
                const a = document.createElement("a");
                a.href = "#";
                a.textContent = p;
                const target = accum;
                a.onclick = (e) => { e.preventDefault(); navigate(target); };
                crumbsEl.appendChild(a);
            }
        });
    }

    function renderEntries(entries, path) {
        entriesEl.innerHTML = "";
        if (entries.length === 0) {
            emptyEl.hidden = false;
            return;
        }
        emptyEl.hidden = true;
        entries.forEach((e) => {
            const li = document.createElement("li");
            li.className = "entry";

            const link = document.createElement("a");
            link.className = "name" + (e.dir ? " dir" : "");
            link.textContent = e.name;
            if (e.dir) {
                link.href = "#";
                link.onclick = (ev) => { ev.preventDefault(); navigate(path + encodeURIComponent(e.name) + "/"); };
            } else {
                link.href = "/files" + path + encodeURIComponent(e.name);
            }
            li.appendChild(link);

            const size = document.createElement("span");
            size.className = "size";
            size.textContent = e.dir ? "" : humanSize(e.size);
            li.appendChild(size);

            const del = document.createElement("button");
            del.className = "del";
            del.type = "button";
            del.textContent = "delete";
            del.onclick = () => onDelete(path, e.name);
            li.appendChild(del);

            entriesEl.appendChild(li);
        });
    }

    async function load() {
        try {
            const res = await fetch("/api/list?path=" + encodeURIComponent(currentPath));
            if (!res.ok) throw new Error("HTTP " + res.status);
            const data = await res.json();
            currentPath = data.path;
            hereEl.textContent = data.path;
            renderCrumbs(data.path, data.rootLabel);
            renderEntries(data.entries, data.path);
        } catch (err) {
            showToast("Load failed: " + err.message, true);
        }
    }

    async function onDelete(path, name) {
        if (!confirm("Delete " + name + "?")) return;
        try {
            const body = new FormData();
            body.append("name", name);
            const res = await fetch("/api/delete?path=" + encodeURIComponent(path), { method: "POST", body });
            if (!res.ok) throw new Error("HTTP " + res.status);
            showToast("Deleted " + name);
            load();
        } catch (err) {
            showToast("Delete failed: " + err.message, true);
        }
    }

    function setQueue(files) {
        queued = Array.from(files);
        queueEl.innerHTML = "";
        queued.forEach((f) => {
            const li = document.createElement("li");
            const name = document.createElement("span");
            name.textContent = f.name;
            const size = document.createElement("span");
            size.className = "qsize";
            size.textContent = humanSize(f.size);
            li.appendChild(name);
            li.appendChild(size);
            queueEl.appendChild(li);
        });
        uploadBtn.disabled = queued.length === 0;
    }

    fileInput.addEventListener("change", () => setQueue(fileInput.files));

    ["dragenter", "dragover"].forEach((ev) => {
        dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.add("drag"); });
    });
    ["dragleave", "drop"].forEach((ev) => {
        dropzone.addEventListener(ev, (e) => { e.preventDefault(); dropzone.classList.remove("drag"); });
    });
    dropzone.addEventListener("drop", (e) => {
        if (e.dataTransfer && e.dataTransfer.files) setQueue(e.dataTransfer.files);
    });

    uploadForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        if (queued.length === 0) return;
        const body = new FormData();
        queued.forEach((f) => body.append("f", f));
        uploadBtn.disabled = true;
        const originalText = uploadBtn.textContent;
        uploadBtn.textContent = "Uploading...";
        try {
            const res = await fetch("/api/upload?path=" + encodeURIComponent(currentPath), { method: "POST", body });
            if (!res.ok) throw new Error("HTTP " + res.status);
            const data = await res.json();
            showToast("Uploaded " + data.saved + " file(s)");
            setQueue([]);
            fileInput.value = "";
            load();
        } catch (err) {
            showToast("Upload failed: " + err.message, true);
        } finally {
            uploadBtn.disabled = queued.length === 0;
            uploadBtn.textContent = originalText;
        }
    });

    window.addEventListener("popstate", () => {
        currentPath = currentPathFromUrl();
        load();
    });

    currentPath = currentPathFromUrl();
    load();
})();
