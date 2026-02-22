// BtcTrade Service Worker - 離線快取策略
const CACHE_NAME = 'btctrade-v1';
const PRECACHE_URLS = [
  '/',
  '/manifest.json'
];

// 安裝：預快取核心資源
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

// 啟動：清除舊快取
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

// 請求攔截：Network First（API）/ Stale While Revalidate（靜態資源）
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // API 請求：始終走網路
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/ws/')) {
    return;
  }

  // 頁面和靜態資源：Network First with cache fallback
  event.respondWith(
    fetch(event.request)
      .then(response => {
        // 只快取成功的 GET 請求
        if (response.ok && event.request.method === 'GET') {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
