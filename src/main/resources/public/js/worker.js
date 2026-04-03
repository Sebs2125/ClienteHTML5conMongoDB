// Escuchamos mensajes desde el hilo principal (index.html)
self.onmessage = function(e) {
    if (e.data.type === 'SYNC') {
        const datosJSON = e.data.payload; //LocalStorage Web JSON

        // Conectar al WebSocket del servidor (Punto 8)
        const ws = new WebSocket('ws://localhost:7000/sincronizar');

        ws.onopen = function() {
            // Cuando abre la conexión, enviamos los datos
            ws.send(datosJSON);
        };

        ws.onmessage = function(event) {
            if (event.data === 'OK') {
                // Notificamos al index.html que todo fue un éxito
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