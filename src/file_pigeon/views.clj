(ns file-pigeon.views
  (:require [clojure.string :as str]
            [file-pigeon.storage :as storage]))

(defn- category-icon [cat]
  (case cat
    :folder "📁"
    :image "🖼️"
    :video "🎬"
    :audio "🎵"
    :pdf "📄"
    :archive "📦"
    :code "💻"
    :document "📝"
    :text "📃"
    "📄"))

(defn- category-label [cat]
  (case cat
    :folder "Folder"
    :image "Image"
    :video "Video"
    :audio "Audio"
    :pdf "PDF"
    :archive "Archive"
    :code "Code"
    :document "Document"
    :text "Text"
    "File"))

(defn- render-breadcrumb [crumb is-last]
  (let [{:keys [name path]} crumb
        enc-path (storage/url-encode path)
        href (if (empty? path) "/" (str "/?path=" enc-path))]
    (if is-last
      (format "<span class=\"breadcrumb-current\">%s</span>" name)
      (format "<a href=\"%s\" class=\"breadcrumb-link\">%s</a><span class=\"breadcrumb-sep\">/</span>"
              href name))))

(defn- render-item-row [{:keys [name is-dir size-formatted last-modified category relative-path browse-url download-url]}]
  (let [icon (category-icon category)
        label (category-label category)
        enc-rel (storage/url-encode relative-path)
        date-display (if (str/blank? last-modified) "-" (subs last-modified 0 (min 16 (count last-modified))))]
    (if is-dir
      (format
       "<tr class=\"item-row dir-item\" data-name=\"%s\" data-type=\"dir\">
          <td class=\"icon-cell\">%s</td>
          <td class=\"name-cell\"><a href=\"%s\" class=\"dir-link\"><strong>%s/</strong></a></td>
          <td class=\"size-cell\">-</td>
          <td class=\"type-cell\"><span class=\"badge badge-dir\">%s</span></td>
          <td class=\"date-cell\">%s</td>
          <td class=\"action-cell\">
            <a href=\"%s\" class=\"btn btn-sm btn-outline\">Open ➔</a>
          </td>
        </tr>"
       name icon browse-url name label date-display browse-url)
      (format
       "<tr class=\"item-row file-item\" data-name=\"%s\" data-type=\"file\">
          <td class=\"icon-cell\">%s</td>
          <td class=\"name-cell\"><a href=\"%s\" target=\"_blank\" class=\"file-link\">%s</a></td>
          <td class=\"size-cell\">%s</td>
          <td class=\"type-cell\"><span class=\"badge badge-file\">%s</span></td>
          <td class=\"date-cell\">%s</td>
          <td class=\"action-cell\">
            <a href=\"%s\" download=\"%s\" class=\"btn btn-sm btn-primary\" title=\"Download\">⬇️ Download</a>
            <button class=\"btn btn-sm btn-outline copy-btn\" data-url=\"%s\" title=\"Copy Link\">📋 Link</button>
            <button class=\"btn btn-sm btn-danger delete-btn\" data-path=\"%s\" data-name=\"%s\" title=\"Delete\">🗑️</button>
          </td>
        </tr>"
       name icon download-url name size-formatted label date-display
       download-url name download-url enc-rel name))))

(defn index-page
  "Generates modern, responsive HTML for File Pigeon local network file and directory browser with dark/light theme support."
  [{:keys [server-urls base-dir current-path current-abs-path breadcrumbs parent-path items storage-stats port]}]
  (let [lan-url (or (-> server-urls :network first :url)
                    (:local server-urls))
        total-crumbs (count breadcrumbs)
        breadcrumbs-html (str/join "" (map-indexed (fn [idx c]
                                                     (render-breadcrumb c (= idx (dec total-crumbs))))
                                                   breadcrumbs))
        parent-row (when parent-path
                     (let [parent-href (if (empty? parent-path) "/" (str "/?path=" (storage/url-encode parent-path)))]
                       (format
                        "<tr class=\"item-row parent-row\" data-name=\"..\" data-type=\"dir\">
                           <td class=\"icon-cell\">⬆️</td>
                           <td class=\"name-cell\"><a href=\"%s\" class=\"dir-link\"><strong>.. (Parent Directory)</strong></a></td>
                           <td class=\"size-cell\">-</td>
                           <td class=\"type-cell\"><span class=\"badge badge-parent\">Up</span></td>
                           <td class=\"date-cell\">-</td>
                           <td class=\"action-cell\"><a href=\"%s\" class=\"btn btn-sm btn-outline\">⬆️ Up</a></td>
                         </tr>"
                        parent-href parent-href)))
        items-html (if (empty? items)
                     "<tr><td colspan=\"6\" class=\"empty-msg\">This directory is empty. Drag & drop files above to upload!</td></tr>"
                     (str/join "\n" (map render-item-row items)))
        all-rows (str (or parent-row "") "\n" items-html)
        dir-count (or (:dir-count storage-stats) 0)
        file-count (or (:file-count storage-stats) (count (remove :is-dir items)))]
    (str
     "<!DOCTYPE html>
<html lang=\"en\" data-theme=\"light\">
<head>
  <meta charset=\"UTF-8\">
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
  <title>File Pigeon - " (if (str/blank? current-path) "Home" current-path) "</title>
  <script>
    (function() {
      try {
        const saved = localStorage.getItem('pigeon_theme');
        const theme = saved ? saved : (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
        document.documentElement.setAttribute('data-theme', theme);
      } catch (e) {}
    })();
  </script>
  <style>
    :root, [data-theme=\"dark\"] {
      --bg: #0f172a;
      --surface: #1e293b;
      --surface-header: #182234;
      --surface-hover: #273549;
      --surface-border: #334155;
      --text: #f8fafc;
      --text-muted: #94a3b8;
      --primary: #38bdf8;
      --primary-hover: #0284c7;
      --primary-text: #0f172a;
      --danger: #ef4444;
      --danger-border: #7f1d1d;
      --danger-text: #fca5a5;
      --success: #22c55e;
      --table-th-bg: #131c2d;
      --path-bg: rgba(15, 23, 42, 0.6);
      --badge-dir-bg: rgba(56, 189, 248, 0.15);
      --badge-dir-text: #38bdf8;
      --badge-dir-border: rgba(56, 189, 248, 0.35);
      --badge-file-bg: rgba(51, 65, 85, 0.5);
      --badge-file-text: #cbd5e1;
      --badge-parent-bg: rgba(100, 116, 139, 0.2);
      --badge-parent-text: #94a3b8;
      --badge-parent-border: rgba(100, 116, 139, 0.4);
      --card-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.3);
      --font: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    }

    [data-theme=\"light\"] {
      --bg: #f8fafc;
      --surface: #ffffff;
      --surface-header: #f1f5f9;
      --surface-hover: #f1f5f9;
      --surface-border: #e2e8f0;
      --text: #0f172a;
      --text-muted: #64748b;
      --primary: #0284c7;
      --primary-hover: #0369a1;
      --primary-text: #ffffff;
      --danger: #dc2626;
      --danger-border: #fecaca;
      --danger-text: #dc2626;
      --success: #16a34a;
      --table-th-bg: #f8fafc;
      --path-bg: rgba(241, 245, 249, 0.9);
      --badge-dir-bg: rgba(2, 132, 199, 0.1);
      --badge-dir-text: #0284c7;
      --badge-dir-border: rgba(2, 132, 199, 0.3);
      --badge-file-bg: rgba(226, 232, 240, 0.7);
      --badge-file-text: #475569;
      --badge-parent-bg: rgba(203, 213, 225, 0.4);
      --badge-parent-text: #64748b;
      --badge-parent-border: rgba(203, 213, 225, 0.7);
      --card-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background-color: var(--bg);
      color: var(--text);
      font-family: var(--font);
      line-height: 1.5;
      padding: 1.5rem 1rem;
      transition: background-color 0.2s ease, color 0.2s ease;
    }
    .container {
      max-width: 1040px;
      margin: 0 auto;
    }
    header {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1.25rem;
      padding-bottom: 1rem;
      border-bottom: 1px solid var(--surface-border);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      text-decoration: none;
      color: inherit;
    }
    .brand-icon { font-size: 2rem; }
    .brand-title {
      font-size: 1.5rem;
      font-weight: 700;
      letter-spacing: -0.5px;
    }
    .brand-sub { color: var(--text-muted); font-size: 0.8rem; }
    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex-wrap: wrap;
    }
    .network-badge {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      padding: 0.4rem 0.75rem;
      border-radius: 8px;
      font-size: 0.82rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      box-shadow: var(--card-shadow);
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      background-color: var(--success);
      border-radius: 50%;
      box-shadow: 0 0 8px var(--success);
    }
    /* Nav Breadcrumbs */
    .nav-bar {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 10px;
      padding: 0.75rem 1rem;
      margin-bottom: 1.25rem;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      box-shadow: var(--card-shadow);
    }
    .breadcrumbs {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.35rem;
      font-size: 0.95rem;
    }
    .breadcrumb-link {
      color: var(--primary);
      text-decoration: none;
      font-weight: 500;
      padding: 0.15rem 0.35rem;
      border-radius: 4px;
      transition: background 0.15s;
    }
    .breadcrumb-link:hover { background: rgba(56, 189, 248, 0.15); }
    .breadcrumb-sep { color: var(--text-muted); }
    .breadcrumb-current { font-weight: 600; color: var(--text); padding: 0.15rem 0.35rem; }
    .path-detail {
      font-size: 0.78rem;
      color: var(--text-muted);
      font-family: monospace;
      background: var(--path-bg);
      border: 1px solid var(--surface-border);
      padding: 0.25rem 0.6rem;
      border-radius: 6px;
      word-break: break-all;
    }
    /* Toolbar: Search & Upload */
    .toolbar {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 1rem;
      margin-bottom: 1.25rem;
      align-items: center;
    }
    @media (max-width: 640px) {
      .toolbar { grid-template-columns: 1fr; }
    }
    .search-box {
      position: relative;
      width: 100%;
    }
    .search-input {
      width: 100%;
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      padding: 0.6rem 0.85rem;
      color: var(--text);
      font-size: 0.9rem;
      outline: none;
      box-shadow: var(--card-shadow);
      transition: border-color 0.15s, box-shadow 0.15s;
    }
    .search-input:focus {
      border-color: var(--primary);
      box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.25);
    }
    .upload-trigger-wrap {
      display: flex;
      gap: 0.5rem;
      align-items: center;
    }
    /* Dropzone */
    .dropzone {
      background: var(--surface);
      border: 2px dashed var(--surface-border);
      border-radius: 10px;
      padding: 1rem 1.25rem;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s;
      margin-bottom: 1.25rem;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      box-shadow: var(--card-shadow);
    }
    .dropzone:hover, .dropzone.dragover {
      border-color: var(--primary);
      background: var(--surface-hover);
    }
    .drop-text { font-size: 0.9rem; color: var(--text); }
    .drop-sub { font-size: 0.78rem; color: var(--text-muted); }
    /* Progress */
    .progress-wrap {
      display: none;
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      padding: 0.75rem 1rem;
      margin-bottom: 1.25rem;
      box-shadow: var(--card-shadow);
    }
    .progress-bar-bg {
      background: var(--surface-border);
      height: 6px;
      border-radius: 3px;
      overflow: hidden;
      margin-top: 0.4rem;
    }
    .progress-bar {
      height: 100%;
      width: 0%;
      background: var(--primary);
      transition: width 0.15s;
    }
    .progress-status {
      font-size: 0.8rem;
      color: var(--text-muted);
    }
    /* Table */
    .files-card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 10px;
      overflow: hidden;
      box-shadow: var(--card-shadow);
    }
    .files-header {
      padding: 0.75rem 1rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--surface-border);
      background: var(--surface-header);
    }
    .files-count { font-size: 0.85rem; color: var(--text-muted); }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }
    th {
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-muted);
      padding: 0.7rem 0.85rem;
      border-bottom: 1px solid var(--surface-border);
      background: var(--table-th-bg);
    }
    td {
      padding: 0.65rem 0.85rem;
      font-size: 0.88rem;
      border-bottom: 1px solid var(--surface-border);
    }
    tr.item-row:hover { background: var(--surface-hover); }
    tr:last-child td { border-bottom: none; }
    .icon-cell { width: 36px; text-align: center; font-size: 1.15rem; }
    .name-cell { font-weight: 500; word-break: break-all; }
    .dir-link { color: var(--primary); text-decoration: none; font-size: 0.95rem; }
    .dir-link:hover { text-decoration: underline; }
    .file-link { color: var(--text); text-decoration: none; }
    .file-link:hover { color: var(--primary); text-decoration: underline; }
    .size-cell { color: var(--text-muted); font-size: 0.82rem; white-space: nowrap; }
    .type-cell { white-space: nowrap; }
    .date-cell { color: var(--text-muted); font-size: 0.82rem; white-space: nowrap; }
    .action-cell { white-space: nowrap; text-align: right; }
    .badge {
      display: inline-block;
      font-size: 0.7rem;
      font-weight: 600;
      padding: 0.15rem 0.45rem;
      border-radius: 4px;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }
    .badge-dir { background: var(--badge-dir-bg); color: var(--badge-dir-text); border: 1px solid var(--badge-dir-border); }
    .badge-parent { background: var(--badge-parent-bg); color: var(--badge-parent-text); border: 1px solid var(--badge-parent-border); }
    .badge-file { background: var(--badge-file-bg); color: var(--badge-file-text); border: 1px solid var(--surface-border); }
    /* Buttons */
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.4rem 0.75rem;
      border-radius: 6px;
      font-size: 0.82rem;
      font-weight: 500;
      text-decoration: none;
      border: none;
      cursor: pointer;
      transition: background 0.15s, color 0.15s, border-color 0.15s;
    }
    .btn-sm { padding: 0.25rem 0.5rem; font-size: 0.78rem; }
    .btn-primary { background: var(--primary); color: var(--primary-text); font-weight: 600; }
    .btn-primary:hover { background: var(--primary-hover); }
    .btn-outline { background: var(--surface); border: 1px solid var(--surface-border); color: var(--text); }
    .btn-outline:hover { background: var(--surface-hover); }
    .btn-danger { background: transparent; border: 1px solid var(--danger-border); color: var(--danger-text); }
    .btn-danger:hover { background: var(--danger); color: #fff; }
    .empty-msg {
      text-align: center;
      padding: 2.5rem 1rem;
      color: var(--text-muted);
    }
    /* QR Modal */
    .modal-backdrop {
      display: none;
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.7);
      z-index: 1000;
      align-items: center;
      justify-content: center;
      padding: 1rem;
    }
    .modal-backdrop.active { display: flex; }
    .modal-box {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      padding: 1.5rem;
      max-width: 360px;
      width: 100%;
      text-align: center;
      position: relative;
      box-shadow: 0 10px 25px rgba(0, 0, 0, 0.4);
    }
    .modal-close {
      position: absolute;
      top: 0.75rem;
      right: 0.75rem;
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 1.25rem;
      cursor: pointer;
    }
    .modal-close:hover { color: var(--text); }
    .modal-qr-img {
      width: 200px;
      height: 200px;
      background: #fff;
      border-radius: 8px;
      padding: 8px;
      margin: 1rem auto;
      display: block;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }
    .modal-url {
      font-size: 0.85rem;
      color: var(--primary);
      word-break: break-all;
      margin-bottom: 1rem;
    }
    /* Toast */
    .toast {
      position: fixed;
      bottom: 1.5rem;
      right: 1.5rem;
      background: var(--surface);
      border: 1px solid var(--primary);
      color: var(--text);
      padding: 0.65rem 1rem;
      border-radius: 8px;
      box-shadow: 0 4px 16px rgba(0,0,0,0.25);
      font-size: 0.85rem;
      opacity: 0;
      transform: translateY(10px);
      transition: all 0.25s;
      pointer-events: none;
      z-index: 1100;
    }
    .toast.show { opacity: 1; transform: translateY(0); }
  </style>
</head>
<body>
  <div class=\"container\">
    <header>
      <a href=\"/\" class=\"brand\">
        <span class=\"brand-icon\">🕊️</span>
        <div>
          <h1 class=\"brand-title\">File Pigeon</h1>
          <p class=\"brand-sub\">Local Network File Explorer</p>
        </div>
      </a>
      <div class=\"header-actions\">
        <div class=\"network-badge\">
          <span class=\"pulse-dot\"></span>
          <span>LAN: <strong>" lan-url "</strong></span>
        </div>
        <button class=\"btn btn-outline\" id=\"theme-toggle\" title=\"Toggle theme\" aria-label=\"Toggle Light/Dark Theme\">
          <span id=\"theme-icon\">🌙</span>
          <span id=\"theme-text\">Dark</span>
        </button>
        <button class=\"btn btn-outline\" id=\"qr-btn\">📱 Mobile QR</button>
      </div>
    </header>

    <!-- Breadcrumb Navigation Bar -->
    <div class=\"nav-bar\">
      <div class=\"breadcrumbs\">
        " breadcrumbs-html "
      </div>
      <div class=\"path-detail\" title=\"Absolute path\">" current-abs-path "</div>
    </div>

    <!-- Search & Action Toolbar -->
    <div class=\"toolbar\">
      <div class=\"search-box\">
        <input type=\"text\" id=\"filter-input\" class=\"search-input\" placeholder=\"🔍 Filter files and folders in this directory...\">
      </div>
      <div class=\"upload-trigger-wrap\">
        <button class=\"btn btn-primary\" onclick=\"document.getElementById('file-input').click()\">📤 Upload File</button>
        <button class=\"btn btn-outline\" onclick=\"location.reload()\">🔄 Refresh</button>
      </div>
    </div>

    <!-- Dropzone Area -->
    <div class=\"dropzone\" id=\"dropzone\">
      <span style=\"font-size: 1.5rem;\">📁</span>
      <div>
        <span class=\"drop-text\">Drag & drop files here to upload to <strong>" (if (str/blank? current-path) "Home" current-path) "</strong></span>
        <div class=\"drop-sub\">or click anywhere in this box to browse files</div>
      </div>
      <input type=\"file\" id=\"file-input\" multiple style=\"display:none;\">
    </div>

    <!-- Upload Progress -->
    <div class=\"progress-wrap\" id=\"progress-wrap\">
      <div class=\"progress-status\" id=\"progress-status\">Uploading...</div>
      <div class=\"progress-bar-bg\">
        <div class=\"progress-bar\" id=\"progress-bar\"></div>
      </div>
    </div>

    <!-- Files & Directories Table -->
    <div class=\"files-card\">
      <div class=\"files-header\">
        <span class=\"files-count\">Showing items in <strong>" (if (str/blank? current-path) "Home" current-path) "</strong></span>
        <span class=\"files-count\">Free Space: <strong>" (:usable-space-formatted storage-stats) "</strong></span>
      </div>
      <div style=\"overflow-x: auto;\">
        <table>
          <thead>
            <tr>
              <th></th>
              <th>Name</th>
              <th>Size</th>
              <th>Type</th>
              <th>Modified</th>
              <th style=\"text-align:right;\">Actions</th>
            </tr>
          </thead>
          <tbody id=\"items-tbody\">
            " all-rows "
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- QR Code Modal -->
  <div id=\"qr-modal\" class=\"modal-backdrop\">
    <div class=\"modal-box\">
      <button class=\"modal-close\" id=\"modal-close\">&times;</button>
      <h3>📱 Mobile Quick Connect</h3>
      <p style=\"font-size:0.8rem; color:var(--text-muted); margin-top:0.25rem;\">Scan to browse files on your phone</p>
      <img class=\"modal-qr-img\" src=\"/qr\" alt=\"QR Code\">
      <div class=\"modal-url\">" lan-url "</div>
      <button class=\"btn btn-outline copy-btn\" data-url=\"" lan-url "\">📋 Copy LAN URL</button>
    </div>
  </div>

  <div id=\"toast\" class=\"toast\"></div>

  <script>
    const currentPath = " (cheshire.core/generate-string (or current-path "")) ";
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('file-input');
    const progressWrap = document.getElementById('progress-wrap');
    const progressBar = document.getElementById('progress-bar');
    const progressStatus = document.getElementById('progress-status');
    const toast = document.getElementById('toast');
    const filterInput = document.getElementById('filter-input');
    const qrBtn = document.getElementById('qr-btn');
    const qrModal = document.getElementById('qr-modal');
    const modalClose = document.getElementById('modal-close');
    const themeBtn = document.getElementById('theme-toggle');
    const themeIcon = document.getElementById('theme-icon');
    const themeText = document.getElementById('theme-text');

    function showToast(msg) {
      toast.textContent = msg;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 3000);
    }

    // Theme Toggle Logic
    function applyThemeUI(theme) {
      document.documentElement.setAttribute('data-theme', theme);
      if (theme === 'light') {
        themeIcon.textContent = '☀️';
        themeText.textContent = 'Light';
        themeBtn.setAttribute('title', 'Switch to dark theme');
      } else {
        themeIcon.textContent = '🌙';
        themeText.textContent = 'Dark';
        themeBtn.setAttribute('title', 'Switch to light theme');
      }
    }

    const initialTheme = document.documentElement.getAttribute('data-theme') || 'dark';
    applyThemeUI(initialTheme);

    themeBtn.addEventListener('click', () => {
      const active = document.documentElement.getAttribute('data-theme') || 'dark';
      const nextTheme = active === 'dark' ? 'light' : 'dark';
      localStorage.setItem('pigeon_theme', nextTheme);
      applyThemeUI(nextTheme);
      showToast('Switched to ' + nextTheme + ' theme');
    });

    // QR Modal Controls
    qrBtn.addEventListener('click', () => qrModal.classList.add('active'));
    modalClose.addEventListener('click', () => qrModal.classList.remove('active'));
    qrModal.addEventListener('click', (e) => {
      if (e.target === qrModal) qrModal.classList.remove('active');
    });

    // Instant Filter
    filterInput.addEventListener('input', (e) => {
      const q = e.target.value.toLowerCase().trim();
      const rows = document.querySelectorAll('#items-tbody tr.item-row');
      rows.forEach(r => {
        const name = (r.getAttribute('data-name') || '').toLowerCase();
        if (r.classList.contains('parent-row')) {
          r.style.display = '';
        } else if (name.includes(q)) {
          r.style.display = '';
        } else {
          r.style.display = 'none';
        }
      });
    });

    // Dropzone Upload
    dropzone.addEventListener('click', () => fileInput.click());
    dropzone.addEventListener('dragover', (e) => { e.preventDefault(); dropzone.classList.add('dragover'); });
    dropzone.addEventListener('dragleave', () => dropzone.classList.remove('dragover'));
    dropzone.addEventListener('drop', (e) => {
      e.preventDefault();
      dropzone.classList.remove('dragover');
      if (e.dataTransfer.files.length > 0) {
        uploadFiles(e.dataTransfer.files);
      }
    });

    fileInput.addEventListener('change', () => {
      if (fileInput.files.length > 0) {
        uploadFiles(fileInput.files);
      }
    });

    function uploadFiles(fileList) {
      const formData = new FormData();
      for (let i = 0; i < fileList.length; i++) {
        formData.append('files', fileList[i]);
      }
      formData.append('path', currentPath);

      progressWrap.style.display = 'block';
      progressBar.style.width = '0%';
      progressStatus.textContent = 'Uploading ' + fileList.length + ' file(s)...';

      const xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/upload', true);

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
          const percent = Math.round((e.loaded / e.total) * 100);
          progressBar.style.width = percent + '%';
          progressStatus.textContent = 'Uploading: ' + percent + '%';
        }
      };

      xhr.onload = () => {
        progressWrap.style.display = 'none';
        if (xhr.status >= 200 && xhr.status < 300) {
          showToast('Uploaded successfully! Refreshing...');
          setTimeout(() => location.reload(), 500);
        } else {
          showToast('Upload failed: ' + xhr.statusText);
        }
      };

      xhr.onerror = () => {
        progressWrap.style.display = 'none';
        showToast('Network error during upload');
      };

      xhr.send(formData);
    }

    // Copy Link Buttons
    document.querySelectorAll('.copy-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const url = btn.getAttribute('data-url');
        const fullUrl = url.startsWith('http') ? url : window.location.origin + url;
        navigator.clipboard.writeText(fullUrl).then(() => {
          showToast('Copied link: ' + fullUrl);
        }).catch(() => {
          showToast('Failed to copy');
        });
      });
    });

    // Delete Buttons
    document.querySelectorAll('.delete-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const path = btn.getAttribute('data-path');
        const name = btn.getAttribute('data-name');
        if (confirm('Delete \"' + name + '\"?')) {
          fetch('/api/files?path=' + encodeURIComponent(path), { method: 'DELETE' })
            .then(res => res.json())
            .then(data => {
              if (data.success) {
                showToast('Deleted ' + name);
                const row = btn.closest('tr');
                if (row) row.remove();
              } else {
                showToast('Failed to delete: ' + (data.message || 'Error'));
              }
            })
            .catch(() => showToast('Error deleting file'));
        }
      });
    });
  </script>
</body>
</html>")))
