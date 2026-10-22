// Dev-server proxy: the browser talks to one origin (localhost:4200) and each API prefix goes to the service that
// owns it, so the backend needs no CORS. The Docker image does the same with nginx (nginx.conf).
// Override the hosts with CLAIMS_URL / REPORTING_URL / NOTIFICATION_URL if the services run elsewhere.

/** The services challenge with `WWW-Authenticate: Basic`, which makes browsers pop up their own login dialog
 *  over the app on a 401. The app has its own sign-in page, so the header is dropped here. */
const dropBasicChallenge = (proxy) =>
  proxy.on('proxyRes', (res) => {
    delete res.headers['www-authenticate'];
  });

const target = (url) => ({ target: url, changeOrigin: true, configure: dropBasicChallenge });

export default {
  '/api/reports': target(process.env.REPORTING_URL ?? 'http://localhost:8082'),
  '/api/notifications': target(process.env.NOTIFICATION_URL ?? 'http://localhost:8083'),
  '/api': target(process.env.CLAIMS_URL ?? 'http://localhost:8081'),
};
