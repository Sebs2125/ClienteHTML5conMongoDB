/**
 * offline.js
 * Manejo de almacenamiento offline, sincronización WebSocket
 * y coordinación con Web Worker para sincronización en background.
 */

(function() {
    'use strict';

    var STORAGE_KEY = 'encuestas_pendientes';
    var WS_URL      = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/sync';
    var ws          = null;
    var wsReady     = false;
    var worker      = null;

    /* ── WEB WORKER ──────────────────────────────────────── */
    // Inline worker para no requerir archivo separado
    var workerCode = `
    var pendientes = [];

    self.onmessage = function(e) {
        var msg = e.data;
        if (msg.type === 'SYNC') {
            pendientes = msg.pendientes || [];
            sincronizar();
        }
    };

    async function sincronizar() {
        var noSync = pendientes.filter(function(p) { return !p.sincronizado; });
        if (!noSync.length) { self.postMessage({ type: 'NADA' }); return; }

        var resultados = [];
        for (var i = 0; i < noSync.length; i++) {
            var enc = noSync[i];
            try {
                var r = await fetch('/api/encuestas', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(enc)
          });
                resultados.push({ id: enc.id, ok: r.ok });
            } catch(err) {
                resultados.push({ id: enc.id, ok: false });
            }
        }
        self.postMessage({ type: 'RESULTADO', resultados: resultados });
    }
  `;

    function crearWorker() {
        try {
            var blob = new Blob([workerCode], { type: 'application/javascript' });
            var url  = URL.createObjectURL(blob);
            worker = new Worker(url);
            worker.onmessage = onWorkerMessage;
        } catch(e) {
            console.warn('[offline] Web Worker no disponible:', e.message);
        }
    }

    function onWorkerMessage(e) {
            var msg = e.data;
    if (msg.type === 'RESULTADO') {
        var pendientes = getPendientes();
        msg.resultados.forEach(function(r) {
            var idx = pendientes.findIndex(function(p) { return p.id === r.id; });
            if (idx !== -1 && r.ok) pendientes[idx].sincronizado = true;
        });
        setPendientes(pendientes);
        actualizarUI();
    }
  }

    /* ── WEBSOCKET ───────────────────────────────────────── */
    function conectarWS() {
        if (!navigator.onLine) return;
        try {
            ws = new WebSocket(WS_URL);
            ws.onopen    = function() { wsReady = true; enviarPendientesWS(); };
            ws.onclose   = function() { wsReady = false; setTimeout(conectarWS, 5000); };
            ws.onerror   = function() { wsReady = false; };
            ws.onmessage = function(e) {
                try {
                    var data = JSON.parse(e.data);
                    if (data.type === 'ACK' && data.id) {
                        var pendientes = getPendientes();
                        var enc = pendientes.find(function(p) { return p.id === data.id; });
                        if (enc) { enc.sincronizado = true; setPendientes(pendientes); actualizarUI(); }
                    }
                } catch(err) {}
            };
        } catch(e) {
            console.warn('[offline] WebSocket no disponible');
        }
    }

    function enviarPendientesWS() {
        if (!wsReady || !ws) return;
        var pendientes = getPendientes().filter(function(p) { return !p.sincronizado; });
        pendientes.forEach(function(enc) {
            try { ws.send(JSON.stringify({ type: 'ENCUESTA', payload: enc })); } catch(e) {}
        });
    }

    /* ── STORAGE ─────────────────────────────────────────── */
    function getPendientes() {
        try { return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]'); }
        catch(e) { return []; }
    }

    function setPendientes(lista) {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(lista)); }
    catch(e) { console.warn('[offline] localStorage lleno'); }
  }

    /* ── UI ──────────────────────────────────────────────── */
    function actualizarUI() {
        var pendientes = getPendientes();
        var noSync = pendientes.filter(function(p) { return !p.sincronizado; }).length;

        var badge   = document.getElementById('pending-badge');
        var counter = document.getElementById('pending-count');
        var globalBadge = document.getElementById('badge-pendientes');

        if (counter)     counter.textContent = noSync;
        if (badge)       badge.style.display = noSync > 0 ? 'inline-flex' : 'none';
        if (globalBadge) globalBadge.textContent = noSync;
    }

    function actualizarBanner() {
        var banner = document.getElementById('offline-banner');
        if (!banner) return;
        if (!navigator.onLine) {
            banner.classList.add('visible');
        } else {
            banner.classList.remove('visible');
        }
    }

    /* ── SYNC DISPARADO ──────────────────────────────────── */
    function intentarSync() {
        if (!navigator.onLine) return;
        var pendientes = getPendientes().filter(function(p) { return !p.sincronizado; });
        if (!pendientes.length) return;

        // Intentar primero WS, luego Worker, luego fetch directo
        if (wsReady && ws) {
            enviarPendientesWS();
        } else if (worker) {
            worker.postMessage({ type: 'SYNC', pendientes: getPendientes() });
        } else {
            // Fallback: fetch directo
            pendientes.forEach(async function(enc) {
                try {
                    var r = await fetch('/api/encuestas', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(enc)
          });
                    if (r.ok) {
                        var all = getPendientes();
                        var idx = all.findIndex(function(p) { return p.id === enc.id; });
                        if (idx !== -1) { all[idx].sincronizado = true; setPendientes(all); actualizarUI(); }
                    }
                } catch(e) {}
            });
        }
    }

    /* ── EVENTOS ─────────────────────────────────────────── */
    window.addEventListener('online', function() {
        actualizarBanner();
        intentarSync();
        if (!wsReady) conectarWS();
    });

    window.addEventListener('offline', function() {
        actualizarBanner();
        wsReady = false;
    });

    document.addEventListener('DOMContentLoaded', function() {
        crearWorker();
        actualizarBanner();
        actualizarUI();
        if (navigator.onLine) {
            conectarWS();
            intentarSync();
        }
    });

    // Exponer para uso desde otros scripts
    window.offlineSync = {
            getPendientes: getPendientes,
            setPendientes: setPendientes,
            intentarSync:  intentarSync,
            actualizarUI:  actualizarUI
  };

})();