


window.addEventListener('online', function() {
    document.getElementById('offline-banner')?.classList.remove('visible');
});
window.addEventListener('offline', function() {
    document.getElementById('offline-banner')?.classList.add('visible');
});