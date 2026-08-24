import { createRoot } from 'react-dom/client';
import App from './App';
import './styles/global.css';

async function prepare() {
  // 개발 중 mock 사용 — 기존 동작 그대로.
  const devMock = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_DATA === 'true';

  // 백엔드 없이 프론트만 올린 데모 배포용 스위치.
  // 별도 플래그로 둬서, 실제 백엔드를 붙인 빌드에서 실수로 켜지지 않게 한다.
  const demoMock = import.meta.env.VITE_DEMO_STANDALONE === 'true';

  if (devMock || demoMock) {
    const { worker } = await import('./mocks/browser');
    await worker.start({
      onUnhandledRequest: 'bypass', // MSW 핸들러 없는 요청은 그대로 통과
      // 배포 시 하위 경로에서도 워커를 찾을 수 있도록 절대 경로로 고정한다.
      serviceWorker: { url: '/mockServiceWorker.js' },
      quiet: true,
    });
  }
}

prepare().then(() => {
  createRoot(document.getElementById('root')!).render(
      <App />
  );
});