self.onmessage = function(e) {
    if (e.data.type === 'SYNC') {
        var datosJSON = e.data.payload;

        var host     = self.location.host || 'localhost:7000';
        var protocol = self.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var ws       = new WebSocket(protocol + '//' + host + '/sincronizar');

        ws.onopen = function() {
            ws.send(datosJSON);
        };

        ws.onmessage = function(event) {
            if (event.data === 'OK') {
                self.postMessage({ status: 'SUCCESS' });
                ws.close();
            } else if (event.data === 'VACIO') {
                self.postMessage({ status: 'EMPTY' });
                ws.close();
            } else {
                self.postMessage({ status: 'ERROR', detalle: event.data });
                ws.close();
            }
        };

        ws.onerror = function() {
            self.postMessage({ status: 'ERROR', detalle: 'No se pudo conectar al servidor.' });
        };

        ws.onclose = function(ev) {
            if (ev.code !== 1000 && ev.code !== 1005) {
                self.postMessage({ status: 'ERROR', detalle: 'Conexión cerrada inesperadamente.' });
            }
        };
    }
};