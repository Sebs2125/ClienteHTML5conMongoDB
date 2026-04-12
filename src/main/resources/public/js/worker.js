
self.onmessage = function(e) {
    if (e.data.type === 'SYNC') {
        const datosJSON = e.data.payload;

        const host = self.location.host || 'localhost:7000';
        const protocol = self.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const ws = new WebSocket(protocol + '//' + host + '/sincronizar');

        ws.onopen = function() {
            ws.send(datosJSON);
        };

        ws.onmessage = function(event) {
            if (event.data === 'OK') {
                self.postMessage({ status: 'SUCCESS' });
                ws.close();
            }
        };

        ws.onerror = function(error) {
            console.error('Error en WebSocket:', error);
            self.postMessage({ status: 'ERROR' });
        };
    }
};