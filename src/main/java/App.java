/**
 * app.js — Utilidades generales de la aplicación OP Encuestas
 */

document.addEventListener('DOMContentLoaded', function() {
    activarValidacionBootstrap();
    autoCerrarAlertas();
    initNavActivo();
});

/* ── VALIDACIÓN BOOTSTRAP ─────────────────── */
function activarValidacionBootstrap() {
    document.querySelectorAll('form.needs-validation').forEach(function(form) {
        form.addEventListener('submit', function(e) {
            if (!form.checkValidity()) {
                e.preventDefault();
                e.stopPropagation();
            }
            form.classList.add('was-validated');
        });
    });
}

/* ── AUTO-CERRAR ALERTAS ─────────────────── */
function autoCerrarAlertas() {
    document.querySelectorAll('.alert-success.alert-dismissible').forEach(function(alerta) {
        setTimeout(function() {
            if (typeof bootstrap !== 'undefined') {
                var bsAlert = bootstrap.Alert.getOrCreateInstance(alerta);
                if (bsAlert) bsAlert.close();
            }
        }, 4500);
    });
}

/* ── NAV ACTIVO ──────────────────────────── */
function initNavActivo() {
    var path = location.pathname;
    document.querySelectorAll('.nav-link-clay').forEach(function(link) {
        if (link.getAttribute('href') && path.startsWith(link.getAttribute('href')) && link.getAttribute('href') !== '/') {
            link.classList.add('active');
        }
    });
}

/* ── API FETCH HELPER ────────────────────── */
async function apiFetch(url, method, body) {
    method = method || 'GET';
    var opts = { method: method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    var resp = await fetch(url, opts);
    var data = await resp.json();
    if (!resp.ok) throw new Error(data.error || ('Error ' + resp.status));
    return data;
}

/* ── MOSTRAR TOAST ───────────────────────── */
function mostrarToast(mensaje, tipo) {
    tipo = tipo || 'success';
    var div = document.createElement('div');
    div.style.cssText = 'position:fixed;bottom:1.5rem;right:1.5rem;z-index:9999;' +
            'padding:.85rem 1.2rem;border-radius:10px;font-size:.875rem;font-weight:600;' +
            'box-shadow:0 8px 24px rgba(45,42,38,.18);animation:fadeUp .3s ease;' +
            'display:flex;align-items:center;gap:.5rem;max-width:320px;';

    if (tipo === 'success') {
        div.style.background = 'rgba(97,114,74,.15)';
        div.style.color = 'var(--olive-600)';
        div.style.border = '1px solid rgba(97,114,74,.3)';
        div.innerHTML = '<i class="bi bi-check-circle-fill"></i>' + mensaje;
    } else if (tipo === 'error') {
        div.style.background = 'rgba(192,57,43,.1)';
        div.style.color = '#8b2517';
        div.style.border = '1px solid rgba(192,57,43,.25)';
        div.innerHTML = '<i class="bi bi-exclamation-circle-fill"></i>' + mensaje;
    } else {
        div.style.background = 'rgba(201,106,58,.1)';
        div.style.color = 'var(--terra-600)';
        div.style.border = '1px solid rgba(201,106,58,.25)';
        div.innerHTML = '<i class="bi bi-info-circle-fill"></i>' + mensaje;
    }

    document.body.appendChild(div);
    setTimeout(function() {
        div.style.opacity = '0';
        div.style.transition = 'opacity .3s';
        setTimeout(function() { div.remove(); }, 300);
    }, 4000);
}